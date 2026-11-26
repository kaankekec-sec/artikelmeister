const MISTRAL_API_URL = "https://api.mistral.ai/v1/chat/completions";

const DEFAULT_MODEL = "deepseek-v4-1-flash-1017";

const ALLOWED_MODELS = [
  DEFAULT_MODEL,
  "mistral-small-latest",
  "mistral-medium-latest",
  "mistral-large-latest",
  "open-mistral-nemo",
];

const MAX_PAYLOAD_BYTES = 64 * 1024; // 64 KB
const MAX_TOKENS_CAP = 8192;
const RATE_LIMIT_WINDOW_MS = 60_000;
const RATE_LIMIT_MAX_REQUESTS = 30;

// In-memory sliding window per isolate: Map<ip, number[]>
const rateLimitMap = new Map();

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization, X-ArtikelMeister-Token",
  "Access-Control-Max-Age": "86400",
};

function jsonResponse(data, status = 200, extraHeaders = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      ...CORS_HEADERS,
      ...extraHeaders,
    },
  });
}

function errorResponse(message, status, type = "invalid_request_error") {
  return jsonResponse({ error: { message, type } }, status);
}

function getClientIp(request) {
  return (
    request.headers.get("CF-Connecting-IP") ||
    request.headers.get("X-Forwarded-For")?.split(",")[0]?.trim() ||
    "unknown"
  );
}

function checkRateLimit(ip) {
  const now = Date.now();
  const windowStart = now - RATE_LIMIT_WINDOW_MS;
  const timestamps = (rateLimitMap.get(ip) || []).filter((ts) => ts > windowStart);

  if (timestamps.length >= RATE_LIMIT_MAX_REQUESTS) {
    const oldestInWindow = timestamps[0];
    const retryAfterSec = Math.max(1, Math.ceil((oldestInWindow + RATE_LIMIT_WINDOW_MS - now) / 1000));
    rateLimitMap.set(ip, timestamps);
    return { allowed: false, retryAfterSec };
  }

  timestamps.push(now);
  rateLimitMap.set(ip, timestamps);
  return { allowed: true, retryAfterSec: 0 };
}

function isAuthorized(request, env) {
  const expectedToken = env?.APP_CLIENT_TOKEN;
  if (!expectedToken) {
    return true;
  }
  const providedToken = request.headers.get("X-ArtikelMeister-Token");
  return providedToken === expectedToken;
}

async function handleChatCompletions(request, env) {
  if (!env?.MISTRAL_API_KEY) {
    return errorResponse("Server misconfigured: MISTRAL_API_KEY is not set.", 500, "server_error");
  }

  const contentLength = request.headers.get("Content-Length");
  if (contentLength && Number(contentLength) > MAX_PAYLOAD_BYTES) {
    return errorResponse("Payload too large.", 413);
  }

  let rawBody;
  try {
    rawBody = await request.text();
  } catch {
    return errorResponse("Failed to read request body.", 400);
  }

  if (rawBody.length > MAX_PAYLOAD_BYTES) {
    return errorResponse("Payload too large.", 413);
  }

  let body;
  try {
    body = JSON.parse(rawBody);
  } catch {
    return errorResponse("Invalid JSON body.", 400);
  }

  if (!body || typeof body !== "object" || Array.isArray(body)) {
    return errorResponse("Request body must be a JSON object.", 400);
  }

  const requestedModel =
    body.model === undefined || body.model === null || body.model === ""
      ? DEFAULT_MODEL
      : body.model;

  if (typeof requestedModel !== "string" || !ALLOWED_MODELS.includes(requestedModel)) {
    return errorResponse(
      `Unsupported model. Allowed models: ${ALLOWED_MODELS.join(", ")}`,
      400
    );
  }

  if (!Array.isArray(body.messages) || body.messages.length === 0) {
    return errorResponse("'messages' must be a non-empty array.", 400);
  }

  const sanitizedBody = { ...body, model: requestedModel };
  sanitizedBody.reasoning_effort = body.reasoning_effort ?? "none";
  if (sanitizedBody.max_tokens !== undefined) {
    const parsedMax = Number(sanitizedBody.max_tokens);
    if (!Number.isFinite(parsedMax) || parsedMax < 1) {
      return errorResponse("'max_tokens' must be a positive number.", 400);
    }
    sanitizedBody.max_tokens = Math.min(Math.floor(parsedMax), MAX_TOKENS_CAP);
  }

  let upstreamResponse;
  try {
    upstreamResponse = await fetch(MISTRAL_API_URL, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${env.MISTRAL_API_KEY}`,
      },
      body: JSON.stringify(sanitizedBody),
    });
  } catch {
    return errorResponse("Failed to reach Mistral API.", 502, "upstream_error");
  }

  let responseText = await upstreamResponse.text();
  if (
    (upstreamResponse.status === 400 || upstreamResponse.status === 422) &&
    /reasoning_effort/i.test(responseText)
  ) {
    const { reasoning_effort: _omitted, ...fallbackBody } = sanitizedBody;
    try {
      upstreamResponse = await fetch(MISTRAL_API_URL, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${env.MISTRAL_API_KEY}`,
        },
        body: JSON.stringify(fallbackBody),
      });
      responseText = await upstreamResponse.text();
    } catch {
      return errorResponse("Failed to reach Mistral API.", 502, "upstream_error");
    }
  }
  return new Response(responseText, {
    status: upstreamResponse.status,
    headers: {
      "Content-Type": upstreamResponse.headers.get("Content-Type") || "application/json; charset=utf-8",
      ...CORS_HEADERS,
    },
  });
}

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: CORS_HEADERS });
    }

    const url = new URL(request.url);
    const path = url.pathname;

    if (request.method === "GET" && path === "/health") {
      return jsonResponse({ ok: true, provider: "mistral" });
    }

    if (!isAuthorized(request, env)) {
      return errorResponse("Unauthorized: invalid or missing X-ArtikelMeister-Token.", 401, "authentication_error");
    }

    const ip = getClientIp(request);
    const rateCheck = checkRateLimit(ip);
    if (!rateCheck.allowed) {
      return jsonResponse(
        { error: { message: "Rate limit exceeded. Please try again later.", type: "rate_limit_error" } },
        429,
        { "Retry-After": String(rateCheck.retryAfterSec) }
      );
    }

    if (request.method === "GET" && path === "/v1/models") {
      return jsonResponse({
        object: "list",
        data: ALLOWED_MODELS.map((id) => ({
          id,
          object: "model",
          owned_by: "mistralai",
        })),
      });
    }

    if (request.method === "POST" && path === "/v1/chat/completions") {
      return handleChatCompletions(request, env);
    }

    return errorResponse("Not found.", 404, "not_found_error");
  },
};
