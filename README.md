# ArtikelMeister 🧠🩸

> **macOS Native Almanca Artikel (`der`, `die`, `das`) ve Kelime Ezberleme Uygulaması**  
> Anki SM-2 Aralıklı Tekrar Algoritması, Gemini AI Entegrasyonu, Deste Yönetimi ve Nöro-Hafıza Tasarımı ile.

---

## 🚀 Özellikler

- **🧠 Nöro-Hafıza & Kanlı Beyin Tasarımı**: Karanlık mod odaklı, modern ve dinamik açılış animasyonu ve özgün görsel kimlik.
- **⚡ Akıllı Artikel Tahmini**: Kelimenin önündeki kutucuğa artikeli yazarak tahmin etme. Doğruysa kelime canlı yeşil, yanlışsa kırmızı yanar; yanlış yapıldığında "İyi" ve "Kolay" butonları otomatik elenir.
- **🔄 Anki SM-2 Spaced Repetition Algoritması**: Unutma eğrisine göre aralıklı tekrar. Dakikalık ara tekrarlar, gün aşırı olgunlaşma planlaması ve süre sayacı.
- **✨ Gemini AI ile Otomatik Kelime Üretimi**:
  - A1 – C1 CEFR dil seviyelerine göre istediğiniz sayıda (1–100) kelime üretimi.
  - Listenizde HALİHAZIRDA BULUNAN kelimeler taranır ve kesinlikle tekrar üretilmez.
  - Kendi Gemini API anahtarınız veya Google OAuth hesabınızla doğrudan kullanım.
- **📁 Gelişmiş Deste / Kategori Sistemi**:
  - Sol menüden tek tıkla yeni deste oluşturma (`+ Yeni Deste Ekle`).
  - Destelere göre özel çalışma ve filtreleme.
- **🔥 Günlük Seri (Streak) & 35 Günlük Isı Haritası**:
  - Kesintisiz çalışma serinizi takip edin.
  - GitHub / Anki tarzı 35 günlük aktivite yoğunluk haritası.
- **💾 Yerel ve Bağımsız Depolama**:
  - İnternetsiz çalışma desteği.
  - Veriler kullanıcıya özel `~/Library/Application Support/ArtikelMeister/` altında saklanır.
- **📦 Tek Tıkla Yedekleme ve Paket Paylaşımı**:
  - `.json` formatında tam yedek alma / geri yükleme.
  - Arkadaşlarınızla paylaşmak için kişisel istatistiklerden arındırılmış temiz deste paketi dışa aktarma.
  - CSV içe ve dışa aktarma desteği (`artikel,kelime,anlam,deste`).
- **🔊 Doğal Almanca Telaffuz**: macOS yerel ses motoru (AVFoundation) ile kelimeleri seslendirme.

---

## 🛠️ Kaynak Koddan Derleme (Build from Source)

Uygulama harici üçüncü taraf bağımlılığı gerektirmez; macOS yerel Swift ve SwiftUI araçlarıyla derlenir.

```bash
# Depoyu klonlayın
git clone https://github.com/<kullanici-adiniz>/ArtikelMeister.git
cd ArtikelMeister

# Universal Binary (Apple Silicon arm64 + Intel x86_64) ve .dmg oluşturun
chmod +x scripts/build_dmg.sh
./scripts/build_dmg.sh
```

Derleme tamamlandığında proje dizininde kurulum için hazır `ArtikelMeister.dmg` üretilir.

---

## 📄 Lisans

Bu proje MIT lisansı ile lisanslanmıştır.
