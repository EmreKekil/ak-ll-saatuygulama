# Kıble Pusulası

Samsung Galaxy Watch Ultra 2 (Wear OS) için internetsiz, saatin kendi pusulası ve GPS'iyle çalışan
kıble bulucu.

## Nasıl çalışır

Kıble yönünü tamamen saatin üzerinde, **internet ve telefon olmadan** bulur. Pusula sayfasında
kadran gerçek kuzeye göre döner; kenardaki altın işaret Kâbe'yi, turuncu nokta güneşi gösterir.
Ortadaki yazı ne kadar dönmeniz gerektiğini söyler ("SAĞA DÖN 23°"); kıbleye hizalanınca ok
yeşile döner ve saat bir kez titrer. Sağa kaydırınca Ayrıntılar ve Konum sayfaları gelir.

**Nasıl hesaplıyor**

1. **Konum:** Saatin kendi GNSS alıcısı (`GPS_PROVIDER`; Wear OS'taki "fused" konum bağlı
   telefondan gelebildiği için bilinçli olarak kullanılmaz). GPS yoksa son bilinen konum; o da
   yoksa uygulamaya gömülü **81 il ve 973 ilçe merkezi** tablosundan elle seçim.
2. **Kıble açısı:** Konumdan Kâbe'ye (21,4225° K, 39,8262° D) büyük daire başlangıç yönü, gerçek
   kuzeye göre. Diyanet'in kıble sayfasının kullandığı formül ve koordinatın aynısıdır.
3. **Manyetik sapma:** Pusula manyetik kuzeyi gösterir; Türkiye'de fark +5,4° ile +6,7° arasıdır.
   NOAA'nın **WMM2025** modeli uygulamaya gömülüdür (Android 16'nın kendi modeli hâlâ WMM2020).
4. **Pusula:** Dönüş vektörü sensörü (jiroskop + manyetometre), eğime karşı dengelenir.
   Manyetometrenin kalibrasyon durumu ve ölçülen alanın WMM'nin beklediği değerle karşılaştırması
   sayesinde metal/mıknatıs paraziti fark edilip uyarı verilir.
5. **Güneşle doğrulama:** Güneşin o anki yönü kadranda gösterilir ve günün "kıble saati"
   hesaplanır. Pusulaya hiç güvenmeden kıbleyi bulmanın en sağlam yoludur.

İl/ilçe tablosu Diyanet'in tam sayı "Kıble Açısı" değerleri yerine **koordinat** olarak gömülüdür:
Diyanet'in sitesindeki tam sayı açı manyetik kuzeye göre ve yaklaşık 2008 yılının sapmasıyla
verildiği için 2026'da 1–2° kaymıştır. Açı her ilçe için koordinattan aynı formülle hesaplanır ve
il/ilçe listesinde gösterilir. İlçe merkezinden 30 km uzakta bile hata 1,2°'yi geçmez; GPS ile
hata pratikte sıfırdır.

**Doğrulama:** Birim testleri NOAA'nın 112 resmi WMM2025 test noktasını, Diyanet'le
karşılaştırılmış 21 noktanın kıble açısı ve kıble saatini ve ilçe tablosunun bütünlüğünü sınar.

**Saatte denenmesi gerekenler:** Galaxy Watch'ın düğmeler solda takıldığında ekranı nasıl
çevirdiği doğrulanamadı; ok güneşe göre ters görünürse Ayrıntılar → Ekran yönü'nden değiştirin.
Parazit eşikleri de gerçek cihaz ölçümleriyle ayarlanabilir.

**Veri kaynakları:** İlçe koordinatları GeoNames (CC BY 4.0) ve Wikidata (CC0), OpenStreetMap ile
bağımsız doğrulandı. Manyetik model NOAA NCEI / BGS World Magnetic Model 2025 (kamu malı).

## Derleme

Gereksinimler: JDK 17+ ve Android SDK (platform 37). En kolayı projeyi **Android Studio**'da açmaktır.

```bash
./gradlew :qibla:assembleRelease      # qibla/build/outputs/apk/release/
./gradlew :qibla:testDebugUnitTest    # birim testleri
```

## Saate yükleme (Galaxy Watch Ultra 2)

1. Saatte **Ayarlar → Saat hakkında → Yazılım** bölümünde *Yazılım sürümü*'ne 5 kez dokunarak geliştirici seçeneklerini açın.
2. **Ayarlar → Geliştirici seçenekleri**'nde *ADB hata ayıklama* ve *Kablosuz hata ayıklama*'yı açın. Saat ve bilgisayar aynı Wi-Fi ağında olmalı.
3. *Kablosuz hata ayıklama → Yeni cihaz eşle* ekranındaki IP:port ve kodla eşleyip bağlanın:

   ```bash
   adb pair 192.168.1.50:37123      # eşleme kodunu girin
   adb connect 192.168.1.50:41235   # Kablosuz hata ayıklama ekranındaki ana port
   adb install -r qibla/build/outputs/apk/release/qibla-release.apk
   ```

Android Studio'da saat bağlıysa doğrudan **Run** ile de yükleyebilirsiniz.

## Proje yapısı

```
qibla/src/main/java/com/emrekekil/qibla/
  geo/      # kıble formülü, WMM2025, güneş konumu (saf Kotlin, birim testli)
  sensor/   # pusula sensörleri, ekran yönü, manyetik parazit, titreşim
  data/     # konum (GPS), il/ilçe tablosu, ayarlar, ViewModel
  ui/       # pusula, ayrıntılar, konum, il/ilçe seçimi, kalibrasyon
qibla/src/main/assets/   # WMM.COF ve tr_districts.csv
```
