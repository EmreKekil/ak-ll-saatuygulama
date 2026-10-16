# Akıllı Saat Uygulamaları

Samsung Galaxy Watch Ultra 2 (Wear OS) için geliştirilen uygulamalar. Her uygulama kendi Gradle
modülündedir; yeni uygulamalar aynı projeye modül olarak eklenir.

| Modül | Uygulama | Açıklama |
|---|---|---|
| `watchinfo` | **Saat Bilgi** | AIDA64 benzeri donanım ve sistem bilgisi |

## Saat Bilgi (`watchinfo`)

Saatin donanımını ve yazılımını sade, koyu bir arayüzle gösterir. Ana ekranda cihaz adı, pil / RAM /
depolama halka göstergeleri ve her kategori için tek satırlık bir özet bulunur. Canlı kategoriler
(başlığın yanındaki nokta) ekran açıkken birkaç saniyede bir güncellenir; ekran kapanınca yoklama
durur, böylece pil harcanmaz.

| Kategori | Gösterilenler |
|---|---|
| Sistem | Cihaz adı, model, Wear OS / One UI Watch / Android sürümü, güvenlik yaması, çekirdek, açık kalma süresi, derin uyku oranı |
| İşlemci | Yonga (SoC), çekirdek kümeleri (ör. 1× Cortex-A78 + 4× Cortex-A55), çekirdek başına **canlı frekans**, frekans aralıkları, komut seti |
| Grafik | GPU adı, sürücü sürümü, OpenGL ES / GLSL / Vulkan sürümleri, azami doku boyutu |
| Bellek | Kurulu ve kullanılabilir RAM, kullanım oranı, zRAM takas alanı, uygulama yığın sınırları |
| Depolama | Kapasite, kullanım, dosya sistemi, şifreleme türü |
| Ekran | Çözünürlük, şekil, boyut ve ppi, yenileme hızları, HDR / geniş renk, parlaklık |
| Pil | Seviye, durum, sağlık, gerilim, **anlık akım ve güç**, tasarım ve tahmini gerçek kapasite, şarj döngüsü, sıcaklık |
| Sıcaklık | Termal kısıtlama durumu, termal yük tahmini, pil sıcaklığı, okunabilen termal bölgeler |
| Bağlantı | Etkin ağ, Wi-Fi bantları ve standartları, Bluetooth LE özellikleri, LTE / eSIM, GPS / GNSS, NFC, UWB |
| Sensörler | Tüm sensörlerin listesi; dokununca özellikleri ve **canlı ölçüm değerleri** |

Uygulama yalnızca kurulumda otomatik verilen izinleri kullanır (ağ ve Wi-Fi durumu). Telefon
gerektirmez, saatte tek başına çalışır.

### Bilinen sınırlamalar

Android, üçüncü taraf uygulamaların bazı sistem dosyalarına erişimini kısıtlar. Bu yüzden:

- **CPU kullanım yüzdesi** gösterilemez (`/proc/stat` Android 8'den beri kapalı); bunun yerine çekirdek frekansları gösterilir.
- **Termal bölgeler** çoğu cihazda "Erişim kısıtlı" görünür; pil sıcaklığı ve sistemin termal durumu her zaman okunur.
- **Nabız ve adım sayacı** gibi sensörler izin gerektirdiği için canlı değer vermeyebilir; özellikleri yine listelenir.
- **Tasarım kapasitesi** gizli bir sistem sınıfından okunur; erişim engellenirse satır gizlenir.
- Değeri okunamayan satırlar boş göstermek yerine tamamen gizlenir.

## Derleme

Gereksinimler: JDK 17+ ve Android SDK (platform 37). En kolayı projeyi **Android Studio**'da açmaktır.

```bash
./gradlew :watchinfo:assembleDebug        # watchinfo/build/outputs/apk/debug/
./gradlew :watchinfo:assembleRelease      # küçültülmüş sürüm (debug anahtarıyla imzalı)
./gradlew :watchinfo:testDebugUnitTest    # birim testleri
```

## Saate yükleme (Galaxy Watch Ultra 2)

1. Saatte **Ayarlar → Saat hakkında → Yazılım** bölümünde *Yazılım sürümü*'ne 5 kez dokunarak geliştirici seçeneklerini açın.
2. **Ayarlar → Geliştirici seçenekleri**'nde *ADB hata ayıklama* ve *Kablosuz hata ayıklama*'yı açın. Saat ve bilgisayar aynı Wi-Fi ağında olmalı.
3. *Kablosuz hata ayıklama → Yeni cihaz eşle* ekranındaki IP:port ve kodla eşleyip bağlanın:

   ```bash
   adb pair 192.168.1.50:37123      # eşleme kodunu girin
   adb connect 192.168.1.50:41235   # Kablosuz hata ayıklama ekranındaki ana port
   adb install -r watchinfo/build/outputs/apk/release/watchinfo-release.apk
   ```

Android Studio'da saat bağlıysa doğrudan **Run** ile de yükleyebilirsiniz.

## Proje yapısı

```
settings.gradle.kts          # modül listesi: include(":watchinfo")
gradle/libs.versions.toml    # tüm kütüphane sürümleri
watchinfo/
  src/main/java/com/emrekekil/watchinfo/
    data/                    # bilgi toplayıcılar (CPU, GPU, pil, ...) ve biçimlendirme
    model/                   # InfoItem / InfoGroup ve kategori tanımları
    ui/                      # Compose for Wear OS (Material 3) ekranları
  src/test/                  # saf mantık için JVM birim testleri
```

Yeni bir saat uygulaması eklemek için `watchinfo` klasörünü örnek alıp yeni bir modül oluşturun
(farklı `namespace` ve `applicationId` ile) ve `settings.gradle.kts` içine `include(":yeni-modul")`
satırını ekleyin.

Kullanılan başlıca teknolojiler: Kotlin, Jetpack Compose for Wear OS (Material 3), Wear Compose
Navigation. Simgeler [Material Symbols](https://fonts.google.com/icons)'tan alınmıştır (Apache 2.0).
