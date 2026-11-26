# ArtikelMeister Mistral Proxy (Cloudflare Worker)

Bu klasör, **Mistral Studio API anahtarınızı Android veya macOS uygulamalarının içine gömmeden** güvenle kullanabilmeniz için sıfır bağımlılıklı (zero-dependency) bir Cloudflare Worker vekil sunucusu (proxy) içerir.

## Sunulan Uç Noktalar (Endpoints)

- `GET /health` → `{"ok": true, "provider": "mistral"}`
- `GET /v1/models` → İzin verilen Mistral modellerini listeler (`mistral-small-latest`, `mistral-medium-latest`, `mistral-large-latest`, `open-mistral-nemo`).
- `POST /v1/chat/completions` → İstek gövdesini doğrular, `max_tokens` değerini en fazla `2048` ile sınırlar ve `https://api.mistral.ai/v1/chat/completions` adresine `MISTRAL_API_KEY` ile iletir.

---

## Adım Adım Kurulum (Daha Önce Cloudflare Workers Kullanmayanlar İçin)

> **Gereksinim:** Bilgisayarınızda Node.js (ve `npx`) kurulu olmalıdır.

### 1. Ücretsiz Cloudflare Hesabı Açma
1. [dash.cloudflare.com](https://dash.cloudflare.com/) adresine gidin ve ücretsiz bir hesap oluşturun.
2. E-posta doğrulamasını tamamlayın. (Ücretsiz plan günde 100.000 isteğe kadar ücretsizdir; bu uygulama için fazlasıyla yeterlidir.)

### 2. Wrangler ile Giriş Yapma
Terminalde bu klasöre geçin ve Cloudflare hesabınıza giriş yapın:

```bash
cd cloudflare-worker
npx wrangler login
```

Tarayıcınızda açılan sayfada **Allow** (İzin Ver) butonuna tıklayın.

### 3. Mistral API Anahtarını Güvenle Kaydetme
Mistral Studio API anahtarınız **asla kodun içine yazılmaz**; Cloudflare'ın şifreli secret kasasında saklanır:

```bash
npx wrangler secret put MISTRAL_API_KEY
```

Komut sizden anahtarı yapıştırmanızı isteyecektir. Mistral Console'dan aldığınız API anahtarını yapıştırıp `Enter` tuşuna basın.

### 4. Tek Komutta Yayınlama (Deploy)
```bash
npx wrangler deploy
```

Komut tamamlandığında terminalde size özel bir adres göreceksiniz, örneğin:
`https://artikelmeister-mistral-proxy.<alt-alan-adin>.workers.dev`

### 5. Adresi ArtikelMeister Uygulamasına Girme
Elde ettiğiniz `https://artikelmeister-mistral-proxy.<alt-alan-adin>.workers.dev` adresini kopyalayın ve ArtikelMeister uygulamasındaki **AI Ayarları → Proxy URL** alanına (veya build varsayılanına) yapıştırın.

---

## İsteğe Bağlı Güvenlik Adımları

### A. Uygulama İçi Paylaşılan Token (`APP_CLIENT_TOKEN`) Ekleme
Proxy adresinizi sadece kendi uygulamanızın kullanmasını isterseniz ek bir gizli token tanımlayabilirsiniz:

```bash
npx wrangler secret put APP_CLIENT_TOKEN
```

Bu secret tanımlandığında, Worker gelen her `/v1/*` isteğinde `X-ArtikelMeister-Token` başlığını (header) kontrol eder. Tanımlamazsanız Worker doğrudan çalışmaya devam eder.

### B. Mistral Console'dan Harcama Limiti Koyma
Beklenmedik kullanımlara karşı [console.mistral.ai](https://console.mistral.ai/) üzerinden aylık harcama limiti (spending limit) belirlemeniz önerilir.
