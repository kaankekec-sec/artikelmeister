package com.artikelmeister.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import javax.net.ssl.SSLException
import kotlin.math.max
import kotlin.math.min

enum class CEFRLevel(val rawValue: String) {
    A1("A1"),
    A2("A2"),
    B1("B1"),
    B2("B2"),
    C1("C1");

    val id: String get() = rawValue

    val title: String
        get() = when (this) {
            A1 -> "A1 — Başlangıç"
            A2 -> "A2 — Temel"
            B1 -> "B1 — Orta"
            B2 -> "B2 — İleri Orta"
            C1 -> "C1 — İleri / Akademik"
        }

    val shortSubtitle: String
        get() = title.substringAfter("—").trim()

    val description: String
        get() = when (this) {
            A1 -> "Günlük hayatta en sık kullanılan temel somut isimler (ev, yiyecek, aile, zaman)."
            A2 -> "Alışveriş, seyahat, ulaşım, sağlık ve günlük iletişimde kullanılan isimler."
            B1 -> "İş, eğitim, çevre, kültür ve deneyim aktarımında kullanılan orta düzey isimler."
            B2 -> "Tartışma, teknoloji, ekonomi, toplum ve soyut düşünce ifade eden isimler."
            C1 -> "Akademik, bilimsel, kurumsal ve ileri düzey edebi/soyut Almanca isimler."
        }
}

enum class GeminiAuthMode(val rawValue: String) {
    API_KEY("apiKey"),
    GOOGLE_OAUTH("googleOAuth");

    val id: String get() = rawValue

    val title: String
        get() = when (this) {
            API_KEY -> "Google AI Studio (API Anahtarı)"
            GOOGLE_OAUTH -> "Google Hesabı (OAuth 2.0)"
        }

    companion object {
        fun fromRaw(raw: String): GeminiAuthMode {
            return entries.firstOrNull { it.rawValue == raw } ?: API_KEY
        }
    }
}

data class GeneratedWordItem(
    val artikel: String,
    val word: String,
    val meaning: String
) {
    val id: String
        get() = "${artikel.lowercase(Locale.ROOT)}_${word.lowercase(Locale.ROOT)}"
}

class GeminiAuthManager private constructor(private val appContext: Context) {
    var authMode by mutableStateOf(GeminiAuthMode.GOOGLE_OAUTH)
    var apiKey by mutableStateOf("")
    var oauthClientId by mutableStateOf("")
    var oauthClientSecret by mutableStateOf("")
    var oauthProjectId by mutableStateOf("")
    var oauthAccessToken by mutableStateOf("")
    var oauthRefreshToken by mutableStateOf("")
    var oauthTokenExpiry by mutableStateOf<Long?>(null)
    var selectedModel by mutableStateOf("gemini-3.8-flash")
    var isAuthenticating by mutableStateOf(false)
    var authStatusMessage by mutableStateOf<String?>(null)

    // Universal Google Profile & Cloud Sync state
    var userEmail by mutableStateOf("")
    var userName by mutableStateOf("")
    var userPictureUrl by mutableStateOf("")
    var driveSyncFolderId by mutableStateOf("")
    var driveSyncFileId by mutableStateOf("")
    var hasDismissedStartupSignIn by mutableStateOf(false)
    var hasCompletedOnboarding by mutableStateOf(false)
    var hasCloudSyncScope by mutableStateOf(false)

    var availableModels by mutableStateOf(
        listOf(
            "gemini-3.8-flash",
            "gemini-3.8-pro",
            "gemini-2.0-flash",
            "gemini-1.5-flash",
            "gemini-1.5-pro"
        )
    )

    private val prefs = appContext.getSharedPreferences("com.artikelmeister.gemini.auth", Context.MODE_PRIVATE)
    @Volatile
    private var activeServerSocket: ServerSocket? = null
    @Volatile
    private var pendingAuthDeferred: CompletableDeferred<String>? = null

    fun handleOAuthDeepLink(uri: Uri): Boolean {
        val code = uri.getQueryParameter("code")
        val error = uri.getQueryParameter("error")
        val deferred = pendingAuthDeferred
        if (deferred != null && deferred.isActive) {
            if (!code.isNullOrEmpty()) {
                deferred.complete(code)
                return true
            } else if (!error.isNullOrEmpty()) {
                deferred.completeExceptionally(IllegalStateException("Google OAuth hatası: $error"))
                return true
            }
        }
        return false
    }

    init {
        try {
            System.setProperty("java.net.preferIPv4Stack", "true")
            System.setProperty("java.net.preferIPv6Addresses", "false")
        } catch (_: Exception) {}
        loadFromKeystore()
    }

    val effectiveClientId: String
        get() {
            val custom = oauthClientId.trim()
            return custom.ifEmpty { defaultOAuthClientId }
        }

    val effectiveClientSecret: String
        get() {
            val custom = oauthClientSecret.trim()
            return custom.ifEmpty { defaultOAuthClientSecret }
        }

    val isAuthorized: Boolean
        get() = when (authMode) {
            GeminiAuthMode.API_KEY -> apiKey.trim().isNotEmpty()
            GeminiAuthMode.GOOGLE_OAUTH -> oauthAccessToken.trim().isNotEmpty() || oauthRefreshToken.trim().isNotEmpty()
        }

    val isGoogleAccountConnected: Boolean
        get() = oauthAccessToken.trim().isNotEmpty() || oauthRefreshToken.trim().isNotEmpty()

    fun saveToKeystore() {
        prefs.edit().apply {
            putString("authMode", authMode.rawValue)
            putString("apiKey", apiKey.trim())
            putString("oauthClientId", oauthClientId.trim())
            putString("oauthClientSecret", oauthClientSecret.trim())
            putString("oauthProjectId", oauthProjectId.trim())
            putString("oauthAccessToken", oauthAccessToken.trim())
            putString("oauthRefreshToken", oauthRefreshToken.trim())
            if (oauthTokenExpiry != null) {
                putLong("oauthTokenExpiry", oauthTokenExpiry!!)
            } else {
                remove("oauthTokenExpiry")
            }
            putString("selectedModel", selectedModel)
            putString("userEmail", userEmail.trim())
            putString("userName", userName.trim())
            putString("userPictureUrl", userPictureUrl.trim())
            putString("driveSyncFolderId", driveSyncFolderId.trim())
            putString("driveSyncFileId", driveSyncFileId.trim())
            putBoolean("hasDismissedStartupSignIn", hasDismissedStartupSignIn)
            putBoolean("hasCompletedOnboarding", hasCompletedOnboarding)
            putBoolean("hasCloudSyncScope", hasCloudSyncScope)
            apply()
        }
    }

    fun loadFromKeystore() {
        authMode = GeminiAuthMode.fromRaw(prefs.getString("authMode", GeminiAuthMode.GOOGLE_OAUTH.rawValue) ?: "googleOAuth")
        apiKey = prefs.getString("apiKey", "") ?: ""
        oauthClientId = prefs.getString("oauthClientId", "") ?: ""
        oauthClientSecret = prefs.getString("oauthClientSecret", "") ?: ""
        oauthProjectId = prefs.getString("oauthProjectId", "") ?: ""
        oauthAccessToken = prefs.getString("oauthAccessToken", "") ?: ""
        oauthRefreshToken = prefs.getString("oauthRefreshToken", "") ?: ""
        oauthTokenExpiry = if (prefs.contains("oauthTokenExpiry")) prefs.getLong("oauthTokenExpiry", 0L) else null
        userEmail = prefs.getString("userEmail", "") ?: ""
        userName = prefs.getString("userName", "") ?: ""
        userPictureUrl = prefs.getString("userPictureUrl", "") ?: ""
        driveSyncFolderId = prefs.getString("driveSyncFolderId", "") ?: ""
        driveSyncFileId = prefs.getString("driveSyncFileId", "") ?: ""
        hasDismissedStartupSignIn = prefs.getBoolean("hasDismissedStartupSignIn", false)
        hasCompletedOnboarding = prefs.getBoolean("hasCompletedOnboarding", false)
        hasCloudSyncScope = prefs.getBoolean("hasCloudSyncScope", false)
        val savedModel = prefs.getString("selectedModel", "gemini-3.8-flash") ?: "gemini-3.8-flash"
        // Auto-migrate legacy 2.5 models to 3.8-flash
        selectedModel = if (savedModel.contains("2.5")) "gemini-3.8-flash" else savedModel
        if (!availableModels.contains(selectedModel)) {
            availableModels = listOf(selectedModel) + availableModels
        }
    }

    fun clearAuthorization() {
        apiKey = ""
        oauthAccessToken = ""
        oauthRefreshToken = ""
        oauthTokenExpiry = null
        userEmail = ""
        userName = ""
        userPictureUrl = ""
        driveSyncFolderId = ""
        driveSyncFileId = ""
        hasDismissedStartupSignIn = false
        hasCompletedOnboarding = false
        hasCloudSyncScope = false
        authStatusMessage = "Yetkilendirme ve hesap bilgileri temizlendi."
        prefs.edit().clear().apply()
    }

    fun completeOnboarding() {
        hasCompletedOnboarding = true
        hasDismissedStartupSignIn = true
        saveToKeystore()
    }

    fun dismissStartupSignIn() {
        hasDismissedStartupSignIn = true
        saveToKeystore()
    }

    fun openGoogleAIStudioInBrowser(context: Context) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    suspend fun startBrowserOAuthFlow(context: Context) {
        val clientId = effectiveClientId
        if (clientId.isEmpty()) {
            throw IllegalStateException("Google OAuth 2.0 istemci bilgisi bulunamadı.")
        }

        isAuthenticating = true
        authStatusMessage = "Tarayıcıda Google yetkilendirme sayfası açılıyor..."

        try {
            val codeVerifier = generateCodeVerifier()
            val codeChallenge = generateCodeChallenge(codeVerifier)

            val deferred = CompletableDeferred<String>()
            pendingAuthDeferred = deferred

            val serverSocket = withContext(Dispatchers.IO) {
                runCatching { activeServerSocket?.close() }
                ServerSocket(0, 10, InetAddress.getByName("127.0.0.1")).also {
                    it.soTimeout = 180_000 // 3 min timeout
                    activeServerSocket = it
                }
            }
            val port = serverSocket.localPort
            val redirectURI = "http://127.0.0.1:$port/callback"

            // Asynchronous multi-request accept loop that handles Chrome PNA, preconnect, and /callback
            val serverScope = CoroutineScope(Dispatchers.IO)
            val serverJob = serverScope.launch {
                while (isActive && !deferred.isCompleted) {
                    val client = try {
                        serverSocket.accept()
                    } catch (_: Exception) {
                        break
                    }
                    launch {
                        handleLoopbackClient(client, deferred)
                    }
                }
            }

            val authUri = Uri.parse("https://accounts.google.com/o/oauth2/v2/auth").buildUpon()
                .appendQueryParameter("client_id", clientId)
                .appendQueryParameter("redirect_uri", redirectURI)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter(
                    "scope",
                    "openid https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/userinfo.profile https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/generative-language.retriever https://www.googleapis.com/auth/cloud-platform"
                )
                .appendQueryParameter("access_type", "offline")
                .appendQueryParameter("prompt", "consent")
                .appendQueryParameter("code_challenge", codeChallenge)
                .appendQueryParameter("code_challenge_method", "S256")
                .build()

            withContext(Dispatchers.Main) {
                // Launch via Chrome / Samsung Internet Custom Tabs to keep app alive in foreground
                val intent = Intent(Intent.ACTION_VIEW, authUri).apply {
                    putExtra("android.support.customtabs.extra.SESSION", null as android.os.IBinder?)
                    putExtra("androidx.browser.customtabs.extra.SESSION", null as android.os.IBinder?)
                    putExtra("android.support.customtabs.extra.TOOLBAR_COLOR", 0xFF18181B.toInt())
                    putExtra("androidx.browser.customtabs.extra.TOOLBAR_COLOR", 0xFF18181B.toInt())
                    putExtra("android.support.customtabs.extra.TITLE_VISIBILITY", 1)
                    putExtra("androidx.browser.customtabs.extra.TITLE_VISIBILITY", 1)
                    if (context !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                context.startActivity(intent)
            }

            val authCode = try {
                withTimeout(180_000) {
                    deferred.await()
                }
            } finally {
                pendingAuthDeferred = null
                runCatching { serverSocket.close() }
                serverJob.cancel()
            }

            authStatusMessage = "Yetkilendirme kodu alındı, erişim anahtarı (Access Token) alınıyor..."
            exchangeCodeForToken(
                code = authCode,
                codeVerifier = codeVerifier,
                redirectURI = redirectURI,
                clientId = clientId,
                clientSecret = effectiveClientSecret
            )

            authMode = GeminiAuthMode.GOOGLE_OAUTH
            hasCloudSyncScope = true
            hasDismissedStartupSignIn = true
            runCatching { fetchUserProfile() }
            saveToKeystore()
            val emailSuffix = if (userEmail.isNotBlank()) " ($userEmail)" else ""
            authStatusMessage = "Google Hesabı$emailSuffix, Drive Senkronizasyonu ve Gemini AI başarıyla bağlandı!"
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: ""
            if (msg.contains("Unable to resolve host", ignoreCase = true) || msg.contains("No address associated", ignoreCase = true)) {
                throw IllegalStateException("Google sunucularına bağlanılamadı (Ağ/DNS Hatası).\nLütfen emülatörünüzün veya cihazınızın internet bağlantısını kontrol edin (Android Emülatörde Uçak modunu açıp kapatmak ağ bağlantısını yeniler).")
            } else {
                throw e
            }
        } finally {
            isAuthenticating = false
            runCatching { activeServerSocket?.close() }
            activeServerSocket = null
        }
    }

    private fun handleLoopbackClient(sock: Socket, deferred: CompletableDeferred<String>) {
        try {
            sock.soTimeout = 15_000
            val reader = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8))
            val firstLine = reader.readLine() ?: ""
            val parts = firstLine.split(" ")
            if (parts.size < 2) {
                sock.close()
                return
            }

            val method = parts[0].uppercase(Locale.ROOT)
            val path = parts[1]

            // 1. Chrome on Android Private Network Access (PNA) preflight check
            if (method == "OPTIONS") {
                val pnaResponse =
                    "HTTP/1.1 204 No Content\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Access-Control-Allow-Methods: GET, OPTIONS\r\n" +
                    "Access-Control-Allow-Headers: *\r\n" +
                    "Access-Control-Allow-Private-Network: true\r\n" +
                    "Connection: close\r\n\r\n"
                runCatching {
                    sock.getOutputStream().apply {
                        write(pnaResponse.toByteArray(Charsets.UTF_8))
                        flush()
                    }
                }
                sock.close()
                return
            }

            // 2. Ignore static favicon or preconnect noise
            if (path.startsWith("/favicon")) {
                val notFound = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                runCatching {
                    sock.getOutputStream().apply {
                        write(notFound.toByteArray(Charsets.UTF_8))
                        flush()
                    }
                }
                sock.close()
                return
            }

            // 3. Handle /callback
            val reqUri = Uri.parse("http://127.0.0.1$path")
            val code = reqUri.getQueryParameter("code")
            val error = reqUri.getQueryParameter("error")

            if (code != null) {
                deferred.complete(code)
                val html = """
                    <!DOCTYPE html>
                    <html><head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>ArtikelMeister Giriş Başarılı</title>
                    <style>
                      body { font-family: -apple-system, Roboto, sans-serif; text-align: center; padding: 40px 20px; background: #121214; color: #fff; }
                      .card { max-width: 440px; margin: 40px auto; background: #1c1c1f; border-radius: 18px; padding: 28px; border: 1px solid #2e2e33; }
                      h2 { color: #22c55e; margin-bottom: 12px; font-size: 22px; }
                      p { color: #a1a1aa; font-size: 14px; line-height: 1.5; }
                      .btn { display: inline-block; margin-top: 24px; padding: 12px 24px; background: #3b82f6; color: #fff; text-decoration: none; border-radius: 12px; font-weight: bold; }
                    </style>
                    <script>
                      window.onload = function() {
                        setTimeout(function() {
                          window.location.href = "artikelmeister://oauth2redirect?status=success";
                        }, 250);
                      };
                    </script>
                    </head>
                    <body>
                      <div class="card">
                        <h2>✓ Google Hesabı Bağlandı!</h2>
                        <p>Bulut senkronizasyonu ve Gemini AI yetkilendirmesi tamamlandı. Uygulamaya dönülüyor...</p>
                        <a class="btn" href="artikelmeister://oauth2redirect?status=success">Uygulamaya Dön</a>
                      </div>
                    </body></html>
                """.trimIndent()

                val bodyBytes = html.toByteArray(Charsets.UTF_8)
                val response =
                    "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/html; charset=utf-8\r\n" +
                    "Content-Length: ${bodyBytes.size}\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Access-Control-Allow-Private-Network: true\r\n" +
                    "Connection: close\r\n\r\n"

                runCatching {
                    sock.getOutputStream().apply {
                        write(response.toByteArray(Charsets.UTF_8))
                        write(bodyBytes)
                        flush()
                    }
                }
            } else if (error != null) {
                deferred.completeExceptionally(IllegalStateException("Google OAuth hatası: $error"))
                val html = """
                    <!DOCTYPE html>
                    <html><head>
                    <meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>Yetkilendirme Hatası</title>
                    <body style="font-family: -apple-system, sans-serif; text-align: center; padding: 40px; background: #121214; color: #fff;">
                    <h2 style="color: #ef4444;">Yetkilendirme Tamamlanamadı</h2>
                    <p style="color: #a1a1aa;">$error</p>
                    </body></html>
                """.trimIndent()
                val bodyBytes = html.toByteArray(Charsets.UTF_8)
                val response =
                    "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/html; charset=utf-8\r\n" +
                    "Content-Length: ${bodyBytes.size}\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Access-Control-Allow-Private-Network: true\r\n" +
                    "Connection: close\r\n\r\n"
                runCatching {
                    sock.getOutputStream().apply {
                        write(response.toByteArray(Charsets.UTF_8))
                        write(bodyBytes)
                        flush()
                    }
                }
            }
        } catch (_: Exception) {
            // Ignored, Broken pipe or connection reset during socket writes
        } finally {
            runCatching { sock.close() }
        }
    }

    private suspend fun <T> withNetworkRetry(
        maxAttempts: Int = 4,
        operation: String = "Ağ isteği",
        block: suspend () -> T
    ): T {
        var lastException: Exception? = null
        for (attempt in 1..maxAttempts) {
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                val msg = e.message ?: ""
                val isNetworkOrDns = e is java.net.UnknownHostException ||
                        e is java.net.SocketTimeoutException ||
                        e is java.net.ConnectException ||
                        e is SocketException ||
                        e is SSLException ||
                        msg.contains("Unable to resolve host", ignoreCase = true) ||
                        msg.contains("No address associated", ignoreCase = true) ||
                        msg.contains("Broken pipe", ignoreCase = true) ||
                        msg.contains("Connection reset", ignoreCase = true)
                if (isNetworkOrDns && attempt < maxAttempts) {
                    delay(1200L * attempt)
                    continue
                }
                throw e
            }
        }
        throw lastException ?: IllegalStateException("$operation başarısız oldu.")
    }

    private suspend fun exchangeCodeForToken(
        code: String,
        codeVerifier: String,
        redirectURI: String,
        clientId: String,
        clientSecret: String
    ) = withContext(Dispatchers.IO) {
        withNetworkRetry(maxAttempts = 4, operation = "Google OAuth Token Değişimi") {
            val params = mutableMapOf(
                "client_id" to clientId,
                "code" to code,
                "code_verifier" to codeVerifier,
                "grant_type" to "authorization_code",
                "redirect_uri" to redirectURI
            )
            if (clientSecret.isNotEmpty()) {
                params["client_secret"] = clientSecret
            }

            val formBody = params.entries.joinToString("&") { (k, v) ->
                "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
            }

            val conn = (URL("https://oauth2.googleapis.com/token").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 30_000
                readTimeout = 30_000
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("Connection", "close")
            }

            conn.outputStream.use { it.write(formBody.toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            val body = (if (status in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""

            if (status !in 200..299) {
                throw IllegalStateException("OAuth Token Hatası: $body")
            }

            val json = JSONObject(body)
            val access = json.optString("access_token", "")
            val refresh = json.optString("refresh_token", "")
            val expiresIn = json.optDouble("expires_in", 3600.0)

            withContext(Dispatchers.Main) {
                if (access.isNotEmpty()) oauthAccessToken = access
                if (refresh.isNotEmpty()) oauthRefreshToken = refresh
                oauthTokenExpiry = System.currentTimeMillis() + ((expiresIn - 60) * 1000).toLong()
            }
        }
    }

    suspend fun validOAuthAccessToken(): String {
        val expiry = oauthTokenExpiry
        if (expiry != null && expiry <= System.currentTimeMillis() && oauthRefreshToken.isNotEmpty() && effectiveClientId.isNotEmpty()) {
            refreshOAuthToken()
        }
        val token = oauthAccessToken.trim()
        if (token.isEmpty()) {
            throw IllegalStateException("Geçerli bir OAuth erişim anahtarı bulunamadı. Lütfen yeniden giriş yapın.")
        }
        return token
    }

    suspend fun refreshOAuthToken() = withContext(Dispatchers.IO) {
        withNetworkRetry(maxAttempts = 3, operation = "OAuth Token Yenileme") {
            val params = mutableMapOf(
                "client_id" to effectiveClientId,
                "refresh_token" to oauthRefreshToken.trim(),
                "grant_type" to "refresh_token"
            )
            val secret = effectiveClientSecret
            if (secret.isNotEmpty()) {
                params["client_secret"] = secret
            }
            val formBody = params.entries.joinToString("&") { (k, v) ->
                "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
            }

            val conn = (URL("https://oauth2.googleapis.com/token").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 30_000
                readTimeout = 30_000
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("Connection", "close")
            }
            conn.outputStream.use { it.write(formBody.toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            val body = (if (status in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""

            if (status !in 200..299) {
                throw IllegalStateException("OAuth oturumu yenilenemedi. Lütfen tekrar giriş yapın.")
            }

            val json = JSONObject(body)
            val access = json.optString("access_token", "")
            if (access.isEmpty()) {
                throw IllegalStateException("OAuth oturumu yenilenemedi. Lütfen tekrar giriş yapın.")
            }
            val expiresIn = json.optDouble("expires_in", 3600.0)

            withContext(Dispatchers.Main) {
                oauthAccessToken = access
                oauthTokenExpiry = System.currentTimeMillis() + ((expiresIn - 60) * 1000).toLong()
                saveToKeystore()
            }
        }
    }

    suspend fun verifyCredentials() {
        val (status, body) = withContext(Dispatchers.IO) {
            withNetworkRetry(maxAttempts = 3, operation = "Gemini Yetkilendirme Doğrulama") {
                val conn: HttpURLConnection = when (authMode) {
                    GeminiAuthMode.API_KEY -> {
                        val cleanKey = apiKey.trim()
                        if (cleanKey.isEmpty()) {
                            throw IllegalArgumentException("Lütfen API anahtarınızı girin.")
                        }
                        val url = URL("https://generativelanguage.googleapis.com/v1beta/models?key=${URLEncoder.encode(cleanKey, "UTF-8")}")
                        (url.openConnection() as HttpURLConnection)
                    }

                    GeminiAuthMode.GOOGLE_OAUTH -> {
                        val token = validOAuthAccessToken()
                        val url = URL("https://generativelanguage.googleapis.com/v1beta/models")
                        (url.openConnection() as HttpURLConnection).apply {
                            setRequestProperty("Authorization", "Bearer $token")
                            val proj = oauthProjectId.trim()
                            if (proj.isNotEmpty()) {
                                setRequestProperty("x-goog-user-project", proj)
                            }
                        }
                    }
                }
                conn.requestMethod = "GET"
                conn.connectTimeout = 30_000
                conn.readTimeout = 30_000
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                Pair(code, text)
            }
        }

        if (status !in 200..299) {
            throw IllegalStateException("Yetkilendirme doğrulanamadı (HTTP $status). Bilgilerinizi kontrol edin.\n$body")
        }

        updateDiscoveredModels(body)
        saveToKeystore()
        authStatusMessage = "Yetkilendirme doğrulandı! Aktif model: $selectedModel"
    }

    suspend fun fetchUserProfile() = withContext(Dispatchers.IO) {
        if (!isGoogleAccountConnected) return@withContext
        withNetworkRetry(maxAttempts = 3, operation = "Google Profil Bilgisi Alma") {
            val token = validOAuthAccessToken()
            val conn = (URL("https://www.googleapis.com/oauth2/v3/userinfo").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 20_000
                readTimeout = 20_000
                setRequestProperty("Authorization", "Bearer $token")
            }
            val code = conn.responseCode
            if (code in 200..299) {
                val text = conn.inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                val json = JSONObject(text)
                val email = json.optString("email", "").trim()
                val name = json.optString("name", "").trim()
                val picture = json.optString("picture", "").trim()
                withContext(Dispatchers.Main) {
                    if (email.isNotEmpty()) userEmail = email
                    if (name.isNotEmpty()) userName = name
                    if (picture.isNotEmpty()) userPictureUrl = picture
                    saveToKeystore()
                }
            }
        }
    }

    suspend fun revokeGoogleTokenBestEffort() = withContext(Dispatchers.IO) {
        val tokenToRevoke = oauthRefreshToken.ifBlank { oauthAccessToken }.trim()
        if (tokenToRevoke.isEmpty()) return@withContext
        runCatching {
            val url = URL("https://oauth2.googleapis.com/revoke?token=${URLEncoder.encode(tokenToRevoke, "UTF-8")}")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
            conn.outputStream.use { it.write(ByteArray(0)) }
            conn.responseCode
        }
    }

    private fun updateDiscoveredModels(jsonStr: String) {
        val root = runCatching { JSONObject(jsonStr) }.getOrNull() ?: return
        val modelsArray = root.optJSONArray("models") ?: return

        val discovered = mutableListOf<String>()
        for (i in 0 until modelsArray.length()) {
            val modelObj = modelsArray.optJSONObject(i) ?: continue
            val rawName = modelObj.optString("name", "")
            if (rawName.isEmpty()) continue
            val methodsArr = modelObj.optJSONArray("supportedGenerationMethods")
            var supportsGen = false
            if (methodsArr != null) {
                for (j in 0 until methodsArr.length()) {
                    if (methodsArr.optString(j) == "generateContent") {
                        supportsGen = true
                        break
                    }
                }
            }
            if (!supportsGen) continue
            val shortName = rawName.removePrefix("models/")
            if (shortName.lowercase(Locale.ROOT).contains("gemini")) {
                discovered.add(shortName)
            }
        }

        if (discovered.isEmpty()) return

        val sortedDiscovered = discovered.sortedWith { a, b ->
            val aIs38Flash = a.contains("3.8") && a.contains("flash")
            val bIs38Flash = b.contains("3.8") && b.contains("flash")
            if (aIs38Flash != bIs38Flash) return@sortedWith if (aIs38Flash) -1 else 1

            val aIs38 = a.contains("3.8")
            val bIs38 = b.contains("3.8")
            if (aIs38 != bIs38) return@sortedWith if (aIs38) -1 else 1

            val aIsFlash = a.contains("flash")
            val bIsFlash = b.contains("flash")
            if (aIsFlash != bIsFlash) return@sortedWith if (aIsFlash) -1 else 1

            b.compareTo(a)
        }

        val merged = mutableListOf("gemini-3.8-flash", "gemini-3.8-pro", "gemini-2.0-flash", "gemini-1.5-flash", "gemini-1.5-pro")
        for (m in sortedDiscovered) {
            if (!merged.contains(m) && !m.contains("2.5")) merged.add(m)
        }
        availableModels = merged.filter { !it.contains("2.5") }

        if (!discovered.contains(selectedModel) || selectedModel.contains("2.5")) {
            selectedModel = sortedDiscovered.firstOrNull { it.contains("3.8") && it.contains("flash") }
                ?: sortedDiscovered.firstOrNull { it.contains("flash") && !it.contains("8b") && !it.contains("lite") && !it.contains("2.5") }
                ?: "gemini-3.8-flash"
        }
    }

    companion object {
        @Volatile
        private var instance: GeminiAuthManager? = null

        fun getInstance(context: Context): GeminiAuthManager {
            return instance ?: synchronized(this) {
                instance ?: GeminiAuthManager(context.applicationContext).also { instance = it }
            }
        }

        private fun unmask(bytes: IntArray, key: Int = 0x5A): String {
            val decoded = ByteArray(bytes.size) { i -> (bytes[i] xor key).toByte() }
            return String(decoded, Charsets.UTF_8)
        }

        val defaultOAuthClientId: String = unmask(
            intArrayOf(
                110, 107, 110, 105, 99, 105, 107, 105, 110, 98, 111, 108, 119, 46, 46, 48,
                57, 49, 60, 57, 63, 109, 48, 106, 104, 106, 63, 106, 109, 99, 62, 61,
                49, 107, 60, 106, 54, 44, 47, 54, 49, 44, 110, 99, 62, 116, 59, 42,
                42, 41, 116, 61, 53, 53, 61, 54, 63, 47, 41, 63, 40, 57, 53, 52,
                46, 63, 52, 46, 116, 57, 53, 55
            )
        )

        val defaultOAuthClientSecret: String = unmask(
            intArrayOf(
                29, 21, 25, 9, 10, 2, 119, 61, 48, 55, 10, 2, 15, 30, 43, 106,
                40, 40, 52, 21, 13, 15, 14, 106, 3, 16, 22, 14, 32, 29, 52, 54,
                57, 110, 105
            )
        )

        private fun generateCodeVerifier(): String {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }

        private fun generateCodeChallenge(verifier: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.UTF_8))
            return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }
    }
}

object GeminiVocabularyGenerator {
    suspend fun generateWords(
        level: CEFRLevel,
        targetCount: Int,
        topicHint: String = "",
        existingCards: List<Flashcard>,
        authManager: GeminiAuthManager,
        onProgress: (String) -> Unit
    ): List<GeneratedWordItem> {
        val clampedCount = max(1, min(100, targetCount))

        val excludedWordsLower = existingCards.map { it.word.trim().lowercase(Locale.ROOT) }.toMutableSet()
        val excludedListForPrompt = existingCards.map { it.word.trim() }.toMutableList()

        val collected = mutableListOf<GeneratedWordItem>()
        var consecutiveErrors = 0
        val maxConsecutiveErrors = 3

        while (collected.size < clampedCount && consecutiveErrors < maxConsecutiveErrors) {
            val remainingNeeded = clampedCount - collected.size
            val chunkSize = min(15, remainingNeeded)

            val progressPct = ((collected.size.toDouble() / clampedCount.toDouble()) * 100.0).toInt()
            if (collected.isEmpty()) {
                onProgress("Gemini (${authManager.selectedModel}) ile ${level.rawValue} kelimeleri üretiliyor (%0)...")
            } else {
                onProgress("${collected.size}/$clampedCount kelime üretildi (%$progressPct)...")
            }

            try {
                val batch = requestBatchFromGemini(
                    level = level,
                    count = chunkSize,
                    topicHint = topicHint,
                    excludedWords = excludedListForPrompt,
                    authManager = authManager,
                    allowModelAutoDiscoveryRetry = true
                )

                var batchAdded = 0
                for (item in batch) {
                    if (collected.size >= clampedCount) break
                    val parsedArtikel = Artikel.parse(item.artikel) ?: continue
                    val cleanWord = item.word.trim()
                    val cleanMeaning = item.meaning.trim()
                    if (cleanWord.isEmpty()) continue

                    val normalizedWord = stripLeadingArtikelIfPresent(cleanWord)
                    if (normalizedWord.isEmpty()) continue

                    val lowerKey = normalizedWord.lowercase(Locale.ROOT)
                    if (!excludedWordsLower.contains(lowerKey)) {
                        excludedWordsLower.add(lowerKey)
                        excludedListForPrompt.add(normalizedWord)
                        collected.add(
                            GeneratedWordItem(
                                artikel = parsedArtikel.rawValue,
                                word = normalizedWord,
                                meaning = cleanMeaning
                            )
                        )
                        batchAdded += 1
                    }
                }

                if (batchAdded > 0) {
                    consecutiveErrors = 0
                } else {
                    consecutiveErrors += 1
                }
            } catch (e: Exception) {
                consecutiveErrors += 1
                if (collected.isEmpty() && consecutiveErrors >= maxConsecutiveErrors) {
                    throw e
                }
                if (collected.isNotEmpty() && consecutiveErrors >= 2) {
                    break
                }
                delay(1000L)
            }
        }

        if (collected.isEmpty()) {
            throw IllegalStateException("Kelime üretilemedi. Lütfen internet bağlantınızı veya API yetkilendirmenizi kontrol edin.")
        }

        onProgress("${collected.size}/$clampedCount kelime başarıyla tamamlandı!")
        return collected
    }

    private fun stripLeadingArtikelIfPresent(rawWord: String): String {
        val parts = rawWord.split(" ", limit = 2)
        if (parts.size == 2 && Artikel.parse(parts[0]) != null) {
            return parts[1].trim()
        }
        return rawWord
    }

    private suspend fun requestBatchFromGemini(
        level: CEFRLevel,
        count: Int,
        topicHint: String,
        excludedWords: List<String>,
        authManager: GeminiAuthManager,
        allowModelAutoDiscoveryRetry: Boolean
    ): List<GeneratedWordItem> {
        val rawModel = authManager.selectedModel.trim()
        val modelName = rawModel.removePrefix("models/")

        val exclusionText = if (excludedWords.isEmpty()) {
            "Henüz kayıtlı kelime yok."
        } else {
            val slice = excludedWords.takeLast(300).joinToString(", ")
            "KULLANICININ MEVCUT KELİME LİSTESİ (BU KELİMELERİ ASLA DAHİL ETME):\n[$slice]"
        }

        val topicInstruction = if (topicHint.trim().isEmpty()) {
            "Genel ${level.rawValue} müfredatına uygun çeşitli konular."
        } else {
            "Öncelikli konu/tema: $topicHint"
        }

        val prompt = """
            Uzman Almanca öğretmenisin. CEFR **${level.rawValue}** seviyesine uygun, artikeli yalnızca "der", "die" veya "das" olan tam **$count** adet tekil Almanca isim ve Türkçe karşılığını üret.
            KURALLAR:
            1. "artikel": "der", "die" veya "das"
            2. "word": Sadece ismin kendisi (baş harfi büyük, artikelsiz, örn: "Tisch")
            3. "meaning": Kısa ve net Türkçe anlamı
            4. $topicInstruction
            5. Aşağıdaki mevcut kelimelerden HİÇBİRİNİ kullanma:
            $exclusionText
        """.trimIndent()

        fun buildPayloadJson(includeThinkingConfig: Boolean): String {
            val genConfig = JSONObject().apply {
                put("temperature", 0.7)
                if (includeThinkingConfig) {
                    put("thinkingConfig", JSONObject().apply { put("thinkingBudget", 0) })
                }
                put("responseMimeType", "application/json")
                put(
                    "responseSchema",
                    JSONObject().apply {
                        put("type", "ARRAY")
                        put("description", "German nouns with article and Turkish meaning")
                        put(
                            "items",
                            JSONObject().apply {
                                put("type", "OBJECT")
                                put(
                                    "properties",
                                    JSONObject().apply {
                                        put(
                                            "artikel",
                                            JSONObject().apply {
                                                put("type", "STRING")
                                                put("enum", JSONArray(listOf("der", "die", "das")))
                                            }
                                        )
                                        put("word", JSONObject().apply { put("type", "STRING") })
                                        put("meaning", JSONObject().apply { put("type", "STRING") })
                                    }
                                )
                                put("required", JSONArray(listOf("artikel", "word", "meaning")))
                            }
                        )
                    }
                )
            }

            return JSONObject().apply {
                put(
                    "contents",
                    JSONArray().apply {
                        put(
                            JSONObject().apply {
                                put("role", "user")
                                put(
                                    "parts",
                                    JSONArray().apply {
                                        put(JSONObject().apply { put("text", prompt) })
                                    }
                                )
                            }
                        )
                    }
                )
                put("generationConfig", genConfig)
            }.toString()
        }

        suspend fun executePost(payloadStr: String): Pair<Int, String> = withContext(Dispatchers.IO) {
            var lastErr: Exception? = null
            for (attempt in 1..3) {
                try {
                    val conn: HttpURLConnection = when (authManager.authMode) {
                        GeminiAuthMode.API_KEY -> {
                            val cleanKey = authManager.apiKey.trim()
                            if (cleanKey.isEmpty()) {
                                throw IllegalStateException("Gemini API Anahtarı girilmedi. Lütfen önce yetkilendirme yapın.")
                            }
                            val url = URL(
                                "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=${
                                    URLEncoder.encode(cleanKey, "UTF-8")
                                }"
                            )
                            url.openConnection() as HttpURLConnection
                        }

                        GeminiAuthMode.GOOGLE_OAUTH -> {
                            val token = authManager.validOAuthAccessToken()
                            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent")
                            (url.openConnection() as HttpURLConnection).apply {
                                setRequestProperty("Authorization", "Bearer $token")
                                val proj = authManager.oauthProjectId.trim()
                                if (proj.isNotEmpty()) {
                                    setRequestProperty("x-goog-user-project", proj)
                                }
                            }
                        }
                    }

                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.connectTimeout = 60_000
                    conn.readTimeout = 60_000
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.outputStream.use { it.write(payloadStr.toByteArray(Charsets.UTF_8)) }

                    val status = conn.responseCode
                    val body = (if (status in 200..299) conn.inputStream else conn.errorStream)
                        ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                    return@withContext Pair(status, body)
                } catch (e: Exception) {
                    lastErr = e
                    val msg = e.message ?: ""
                    val isDnsOrNet = e is java.net.UnknownHostException ||
                            e is java.net.SocketTimeoutException ||
                            e is java.net.ConnectException ||
                            msg.contains("Unable to resolve host", ignoreCase = true) ||
                            msg.contains("No address associated", ignoreCase = true)
                    if (isDnsOrNet && attempt < 3) {
                        delay(1200L * attempt)
                        continue
                    }
                    throw e
                }
            }
            throw lastErr ?: IllegalStateException("Gemini bağlantısı kurulamadı.")
        }

        var (status, body) = executePost(buildPayloadJson(includeThinkingConfig = true))

        if (status == 400) {
            val fallback = runCatching { executePost(buildPayloadJson(includeThinkingConfig = false)) }.getOrNull()
            if (fallback != null && fallback.first in 200..299) {
                return parseGeminiCandidates(fallback.second)
            }
        }

        if ((status == 404 || status == 400) && allowModelAutoDiscoveryRetry) {
            val previousModel = authManager.selectedModel
            runCatching { authManager.verifyCredentials() }
            if (authManager.selectedModel != previousModel) {
                return requestBatchFromGemini(
                    level = level,
                    count = count,
                    topicHint = topicHint,
                    excludedWords = excludedWords,
                    authManager = authManager,
                    allowModelAutoDiscoveryRetry = false
                )
            }
        }

        if (status !in 200..299) {
            var errorMessage = "Gemini API Hatası (HTTP $status - Model: $modelName)."
            runCatching {
                val errMsg = JSONObject(body).optJSONObject("error")?.optString("message", "") ?: ""
                if (errMsg.isNotEmpty()) {
                    errorMessage += "\n$errMsg"
                }
            }
            throw IllegalStateException(errorMessage)
        }

        return parseGeminiCandidates(body)
    }

    private fun parseGeminiCandidates(bodyStr: String): List<GeneratedWordItem> {
        val root = JSONObject(bodyStr)
        val candidates = root.optJSONArray("candidates")
            ?: throw IllegalStateException("Gemini yanıtı beklenmeyen bir formatta döndü.")
        val firstCandidate = candidates.optJSONObject(0)
            ?: throw IllegalStateException("Gemini yanıtı boş döndü.")
        val content = firstCandidate.optJSONObject("content")
            ?: throw IllegalStateException("Gemini yanıt içeriği okunamadı.")
        val parts = content.optJSONArray("parts")
            ?: throw IllegalStateException("Gemini yanıt parçaları okunamadı.")
        val text = parts.optJSONObject(0)?.optString("text", "") ?: ""
        if (text.isEmpty()) {
            throw IllegalStateException("Gemini yanıt metni boş döndü.")
        }

        val itemsArr = JSONArray(text)
        val result = mutableListOf<GeneratedWordItem>()
        for (i in 0 until itemsArr.length()) {
            val obj = itemsArr.optJSONObject(i) ?: continue
            result.add(
                GeneratedWordItem(
                    artikel = obj.optString("artikel", ""),
                    word = obj.optString("word", ""),
                    meaning = obj.optString("meaning", "")
                )
            )
        }
        return result
    }
}

/**
 * Universal Cross-Platform Google Drive Cloud Sync Service
 * Creates/maintains the 'ArtikelMeister Cloud Sync' folder and 'artikelmeister_universal_sync.json'
 * inside the user's Google Drive so macOS, Android, and future iOS/Windows platforms stay 1:1 in sync.
 */
object GoogleDriveSyncService {
    const val SYNC_FOLDER_NAME = "ArtikelMeister Cloud Sync"
    const val SYNC_FILE_NAME = "artikelmeister_universal_sync.json"

    data class CloudSnapshot(
        val folderId: String,
        val fileId: String?,
        val backup: ArtikelMeisterBackup?
    )

    fun parseGoogleDriveError(httpCode: Int, responseBody: String): String {
        var message: String? = null
        var reason: String? = null
        runCatching {
            val root = JSONObject(responseBody)
            val errObj = root.optJSONObject("error")
            if (errObj != null) {
                message = errObj.optString("message", "").ifBlank { null }
                val errorsArr = errObj.optJSONArray("errors")
                if (errorsArr != null && errorsArr.length() > 0) {
                    reason = errorsArr.optJSONObject(0)?.optString("reason", "")
                }
            }
        }

        val rawMsg = message ?: responseBody.take(240).trim()

        if (rawMsg.contains("Google Drive API has not been used in project", ignoreCase = true) ||
            rawMsg.contains("is disabled", ignoreCase = true) ||
            reason == "accessNotConfigured" || reason == "SERVICE_DISABLED"
        ) {
            return "Google Cloud projenizde 'Google Drive API' etkinleştirilmemiş.\nLütfen Google Cloud Console'dan Google Drive API'yi etkinleştirin (Enable):\nhttps://console.developers.google.com/apis/api/drive.googleapis.com/overview?project=414393134856"
        }

        if (httpCode == 401) {
            return "Google oturumunun süresi dolmuş. Lütfen 'Hesaptan Çıkış Yap' deyip tekrar giriş yapın."
        }

        if (httpCode == 403) {
            if (rawMsg.contains("insufficient", ignoreCase = true)) {
                return "Google Drive izinleri eksik. Lütfen 'Hesaptan Çıkış Yap' deyip tekrar giriş yapın ve Drive izin kutucuğunu işaretleyin."
            }
            return "Google Drive erişim reddedildi (HTTP 403):\n$rawMsg"
        }

        return "Google Drive hatası (HTTP $httpCode):\n$rawMsg"
    }

    suspend fun ensureSyncFolder(authManager: GeminiAuthManager): String = withContext(Dispatchers.IO) {
        var token = authManager.validOAuthAccessToken()

        // 1. Check if cached folderId is still valid and not trashed
        val cachedFolderId = authManager.driveSyncFolderId.trim()
        if (cachedFolderId.isNotEmpty()) {
            val checkUrl = URL("https://www.googleapis.com/drive/v3/files/$cachedFolderId?fields=id,name,trashed")
            var checkConn = (checkUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 20_000
                readTimeout = 20_000
                setRequestProperty("Authorization", "Bearer $token")
            }
            if (checkConn.responseCode == 401 && authManager.oauthRefreshToken.isNotEmpty()) {
                runCatching { authManager.refreshOAuthToken() }
                token = authManager.validOAuthAccessToken()
                checkConn = (checkUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 20_000
                    readTimeout = 20_000
                    setRequestProperty("Authorization", "Bearer $token")
                }
            }
            if (checkConn.responseCode in 200..299) {
                val body = checkConn.inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "{}"
                val obj = runCatching { JSONObject(body) }.getOrNull()
                if (obj != null && !obj.optBoolean("trashed", false)) {
                    return@withContext cachedFolderId
                }
            }
        }

        // 2. Query Drive for existing 'ArtikelMeister Cloud Sync' folder
        val query = "mimeType = 'application/vnd.google-apps.folder' and name = '$SYNC_FOLDER_NAME' and trashed = false"
        val listUrl = URL(
            "https://www.googleapis.com/drive/v3/files?q=${URLEncoder.encode(query, "UTF-8")}&fields=files(id,name,modifiedTime)&orderBy=modifiedTime desc"
        )
        var listConn = (listUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 25_000
            readTimeout = 25_000
            setRequestProperty("Authorization", "Bearer $token")
        }
        var listCode = listConn.responseCode
        if (listCode == 401 && authManager.oauthRefreshToken.isNotEmpty()) {
            runCatching { authManager.refreshOAuthToken() }
            token = authManager.validOAuthAccessToken()
            listConn = (listUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 25_000
                readTimeout = 25_000
                setRequestProperty("Authorization", "Bearer $token")
            }
            listCode = listConn.responseCode
        }

        val listBody = (if (listCode in 200..299) listConn.inputStream else listConn.errorStream)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""

        if (listCode !in 200..299) {
            throw IllegalStateException(parseGoogleDriveError(listCode, listBody))
        }

        val filesArr = JSONObject(listBody).optJSONArray("files")
        if (filesArr != null && filesArr.length() > 0) {
            val foundId = filesArr.optJSONObject(0)?.optString("id", "") ?: ""
            if (foundId.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    authManager.driveSyncFolderId = foundId
                    authManager.hasCloudSyncScope = true
                    authManager.saveToKeystore()
                }
                return@withContext foundId
            }
        }

        // 3. Create 'ArtikelMeister Cloud Sync' folder if it doesn't exist yet
        val createMeta = JSONObject().apply {
            put("name", SYNC_FOLDER_NAME)
            put("mimeType", "application/vnd.google-apps.folder")
        }
        var createConn = (URL("https://www.googleapis.com/drive/v3/files?fields=id").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 25_000
            readTimeout = 25_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }
        createConn.outputStream.use { it.write(createMeta.toString().toByteArray(Charsets.UTF_8)) }
        var createCode = createConn.responseCode
        if (createCode == 401 && authManager.oauthRefreshToken.isNotEmpty()) {
            runCatching { authManager.refreshOAuthToken() }
            token = authManager.validOAuthAccessToken()
            createConn = (URL("https://www.googleapis.com/drive/v3/files?fields=id").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 25_000
                readTimeout = 25_000
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            createConn.outputStream.use { it.write(createMeta.toString().toByteArray(Charsets.UTF_8)) }
            createCode = createConn.responseCode
        }

        val createBody = (if (createCode in 200..299) createConn.inputStream else createConn.errorStream)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""

        if (createCode !in 200..299) {
            throw IllegalStateException(parseGoogleDriveError(createCode, createBody))
        }

        val newFolderId = JSONObject(createBody).optString("id", "")
        if (newFolderId.isEmpty()) {
            throw IllegalStateException("Google Drive klasör kimliği alınamadı.")
        }

        withContext(Dispatchers.Main) {
            authManager.driveSyncFolderId = newFolderId
            authManager.hasCloudSyncScope = true
            authManager.saveToKeystore()
        }
        newFolderId
    }

    suspend fun fetchCloudSnapshot(authManager: GeminiAuthManager): CloudSnapshot = withContext(Dispatchers.IO) {
        val folderId = ensureSyncFolder(authManager)
        var token = authManager.validOAuthAccessToken()

        val query = "name = '$SYNC_FILE_NAME' and '$folderId' in parents and trashed = false"
        val listUrl = URL(
            "https://www.googleapis.com/drive/v3/files?q=${URLEncoder.encode(query, "UTF-8")}&fields=files(id,name,modifiedTime)&orderBy=modifiedTime desc"
        )
        var listConn = (listUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 25_000
            readTimeout = 25_000
            setRequestProperty("Authorization", "Bearer $token")
        }
        var listCode = listConn.responseCode
        if (listCode == 401 && authManager.oauthRefreshToken.isNotEmpty()) {
            runCatching { authManager.refreshOAuthToken() }
            token = authManager.validOAuthAccessToken()
            listConn = (listUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 25_000
                readTimeout = 25_000
                setRequestProperty("Authorization", "Bearer $token")
            }
            listCode = listConn.responseCode
        }

        val listBody = (if (listCode in 200..299) listConn.inputStream else listConn.errorStream)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""

        if (listCode !in 200..299) {
            throw IllegalStateException(parseGoogleDriveError(listCode, listBody))
        }

        val filesArr = JSONObject(listBody).optJSONArray("files")
        if (filesArr == null || filesArr.length() == 0) {
            return@withContext CloudSnapshot(folderId = folderId, fileId = null, backup = null)
        }

        val fileId = filesArr.optJSONObject(0)?.optString("id", "") ?: ""
        if (fileId.isEmpty()) {
            return@withContext CloudSnapshot(folderId = folderId, fileId = null, backup = null)
        }

        withContext(Dispatchers.Main) {
            authManager.driveSyncFileId = fileId
            authManager.saveToKeystore()
        }

        val downloadUrl = URL("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
        var dlConn = (downloadUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 30_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $token")
        }
        var dlCode = dlConn.responseCode
        if (dlCode == 401 && authManager.oauthRefreshToken.isNotEmpty()) {
            runCatching { authManager.refreshOAuthToken() }
            token = authManager.validOAuthAccessToken()
            dlConn = (downloadUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 30_000
                readTimeout = 30_000
                setRequestProperty("Authorization", "Bearer $token")
            }
            dlCode = dlConn.responseCode
        }

        val dlText = (if (dlCode in 200..299) dlConn.inputStream else dlConn.errorStream)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""

        if (dlCode !in 200..299 || dlText.isBlank()) {
            return@withContext CloudSnapshot(folderId = folderId, fileId = fileId, backup = null)
        }

        val parsedBackup = runCatching { ArtikelMeisterBackup.fromJsonString(dlText) }.getOrNull()
        CloudSnapshot(folderId = folderId, fileId = fileId, backup = parsedBackup)
    }

    suspend fun uploadCloudBackup(
        authManager: GeminiAuthManager,
        folderId: String,
        existingFileId: String?,
        backupJson: String
    ): String = withContext(Dispatchers.IO) {
        var token = authManager.validOAuthAccessToken()
        var targetFileId = existingFileId?.trim().orEmpty()

        // Step 1: If no file exists yet in the folder, create the empty file metadata first
        if (targetFileId.isEmpty()) {
            val createMeta = JSONObject().apply {
                put("name", SYNC_FILE_NAME)
                put("mimeType", "application/json")
                put("parents", JSONArray().put(folderId))
            }
            var createConn = (URL("https://www.googleapis.com/drive/v3/files?fields=id").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 25_000
                readTimeout = 25_000
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            createConn.outputStream.use { it.write(createMeta.toString().toByteArray(Charsets.UTF_8)) }
            var createCode = createConn.responseCode
            if (createCode == 401 && authManager.oauthRefreshToken.isNotEmpty()) {
                runCatching { authManager.refreshOAuthToken() }
                token = authManager.validOAuthAccessToken()
                createConn = (URL("https://www.googleapis.com/drive/v3/files?fields=id").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 25_000
                    readTimeout = 25_000
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                }
                createConn.outputStream.use { it.write(createMeta.toString().toByteArray(Charsets.UTF_8)) }
                createCode = createConn.responseCode
            }

            val createBody = (if (createCode in 200..299) createConn.inputStream else createConn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (createCode !in 200..299) {
                throw IllegalStateException(parseGoogleDriveError(createCode, createBody))
            }
            targetFileId = JSONObject(createBody).optString("id", "")
        }

        if (targetFileId.isEmpty()) {
            throw IllegalStateException("Google Drive dosya kimliği oluşturulamadı.")
        }

        // Step 2: Upload the JSON content via media upload (using POST + X-HTTP-Method-Override: PATCH for Android HttpURLConnection compatibility)
        val uploadUrl = URL("https://www.googleapis.com/upload/drive/v3/files/$targetFileId?uploadType=media")
        var uploadConn = (uploadUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-HTTP-Method-Override", "PATCH")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }
        val payloadBytes = backupJson.toByteArray(Charsets.UTF_8)
        uploadConn.outputStream.use { it.write(payloadBytes) }
        var uploadCode = uploadConn.responseCode
        if (uploadCode == 401 && authManager.oauthRefreshToken.isNotEmpty()) {
            runCatching { authManager.refreshOAuthToken() }
            token = authManager.validOAuthAccessToken()
            uploadConn = (uploadUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 30_000
                readTimeout = 30_000
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("X-HTTP-Method-Override", "PATCH")
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            uploadConn.outputStream.use { it.write(payloadBytes) }
            uploadCode = uploadConn.responseCode
        }

        if (uploadCode == 404 && existingFileId != null) {
            // File was deleted externally; recreate it
            return@withContext uploadCloudBackup(authManager, folderId, null, backupJson)
        }
        if (uploadCode !in 200..299) {
            val err = uploadConn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            throw IllegalStateException(parseGoogleDriveError(uploadCode, err))
        }

        withContext(Dispatchers.Main) {
            authManager.driveSyncFileId = targetFileId
            authManager.saveToKeystore()
        }
        targetFileId
    }

    suspend fun deleteAllCloudAccountData(authManager: GeminiAuthManager) = withContext(Dispatchers.IO) {
        if (!authManager.isGoogleAccountConnected) return@withContext
        runCatching {
            val token = authManager.validOAuthAccessToken()

            // Delete sync file if known
            val fileId = authManager.driveSyncFileId.trim()
            if (fileId.isNotEmpty()) {
                runCatching {
                    val conn = (URL("https://www.googleapis.com/drive/v3/files/$fileId").openConnection() as HttpURLConnection).apply {
                        requestMethod = "DELETE"
                        connectTimeout = 15_000
                        readTimeout = 15_000
                        setRequestProperty("Authorization", "Bearer $token")
                    }
                    conn.responseCode
                }
            }

            // Find and delete all 'ArtikelMeister Cloud Sync' folders
            val query = "name = '$SYNC_FOLDER_NAME' and trashed = false"
            val listUrl = URL(
                "https://www.googleapis.com/drive/v3/files?q=${URLEncoder.encode(query, "UTF-8")}&fields=files(id)"
            )
            val listConn = (listUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Authorization", "Bearer $token")
            }
            if (listConn.responseCode in 200..299) {
                val body = listConn.inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "{}"
                val arr = JSONObject(body).optJSONArray("files")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val fId = arr.optJSONObject(i)?.optString("id", "") ?: ""
                        if (fId.isNotEmpty()) {
                            runCatching {
                                val delConn = (URL("https://www.googleapis.com/drive/v3/files/$fId").openConnection() as HttpURLConnection).apply {
                                    requestMethod = "DELETE"
                                    connectTimeout = 15_000
                                    readTimeout = 15_000
                                    setRequestProperty("Authorization", "Bearer $token")
                                }
                                delConn.responseCode
                            }
                        }
                    }
                }
            }
        }
    }

    fun openDriveSyncFolderInBrowser(context: Context, folderId: String) {
        runCatching {
            val targetUrl = if (folderId.isNotBlank()) {
                "https://drive.google.com/drive/folders/$folderId"
            } else {
                "https://drive.google.com/drive/my-drive"
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
