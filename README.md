# Saat Kadranları

Samsung Galaxy Watch Ultra 2 (Wear OS) için kadranlar. Her kadran ayrı bir APK'dır ve
**Watch Face Format (WFF)** ile yazılmıştır: kod içermez, yalnızca XML ve görsellerden oluşur; saat
kadranı kendisi çizer (pil dostu, ortam modu hazır).

| Modül | Kadran | Durum |
|---|---|---|
| `watchface-dijital` | **Sade Dijital** | hazır |
| `watchface-analog` | Sade Analog | sırada |
| `watchface-spor` | Hibrit / Spor | sırada |

## Sade Dijital

Büyük iki renkli saat (saat beyaz, dakika vurgu renginde), Türkçe tarih, pil, saniye halkası ve iki
komplikasyon yuvası:

- **Sonraki vakit** (geniş yuva): varsayılan olarak *Ezan Vakti ve Kıble* uygulamasının verisi
  ("İkindi 15:26 · 1sa 23dk"). Uygulama yüklü değilse saatin gün doğumu/batımı verisi gelir.
- **Alt bilgi**: varsayılan adım sayısı; istenen başka veri seçilebilir.
- **Renk**: Yeşil, Mavi, Altın, Beyaz (kadrana uzun bas → Özelleştir).
- **Ortam modu (AOD)**: dolgular ve saniye halkası kapanır, saat ince yazılır.

## Yükleme

```powershell
adb install -r watchface-dijital-release.apk
```

Ardından kadrana uzun basın, kaydırıp **Sade Dijital**'i seçin.

## Geliştirme

```bash
./gradlew :watchface-dijital:assembleRelease
# Önizleme (kadranın XML'inden, örnek verilerle):
python3 tools/wff_preview.py watchface-dijital/src/main/res/raw/watchface.xml onizleme.png --round \
    --slot "0=LONG_TEXT|İkindi 15:26 · 1sa 23dk" --slot "1=SHORT_TEXT|6.240"
```

- `watchface.xml` dosyaları resmi WFF v2 XSD şemasıyla doğrulanır
  (github.com/google/watchface, `third_party/wff/specification/documents/2`).
- `tools/wff_preview.py` kullanılan WFF alt kümesini yaklaşık çizer; kadran seçicideki
  `preview.png` bu betikle üretilir. Saatteki görüntüyle piksel piksel aynı değildir.
