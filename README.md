# Ezan Vakti ve Kıble

Samsung Galaxy Watch Ultra 2 (Wear OS) için ezan vakitleri ve internetsiz kıble bulucu.

## Ezan vakitleri

Uygulama vakitler sayfasıyla açılır: ortada bir sonraki vakte kalan süre, dış halkada şimdiki vaktin
ne kadarının geçtiği, altta günün altı vakti. Soldan sağa kaydırınca kıble pusulası açılır.

- **Kaynak:** Diyanet vakitleri, saat telefona (Bluetooth) veya Wi-Fi'ye bağlıyken
  ezanvakti.emushaf.net'ten ilçe başına 32 günlük olarak indirilip saatte saklanır; veri 10 günün
  altına inince yenilenir. Bağlantı yoksa vakitler saatte **Diyanet yöntemiyle hesaplanır**
  (İmsak 18°, Yatsı 17°, temkinler: Güneş −7, Öğle +5, İkindi +4, Akşam +7 dk). Hesap, 10 il × 32
  günlük Diyanet verisinin hepsinde en fazla 1 dakika farklıdır.
- **Konum:** Ana konum varsayılan olarak Ankara merkezdir (Konum sayfasından değiştirilebilir).
  Bulunduğunuz yer ana konumdan 20 km'den uzak ve farklı bir Diyanet bölgesindeyse oranın vakitleri
  geçici olarak kullanılır; dönünce ana konuma geçilir. Konum, uygulama açılınca ve günde bir kez
  arka planda sistemin son bilinen konumundan kontrol edilir (ek izin istenmez).
- **Bildirimler:** Her vakit için "vakit girince" ve "çıkmadan önce" bildirimi ayrı ayrı açılıp
  kapatılır; uyarı süresi 15–120 dk arasında seçilir (vakitler sayfasında sayaca dokunun).

## Pil

Saat pili için uygulamada bilinçli tercihler:

- Aynı anda yalnızca **bir alarm** kuruludur (günde en fazla ~10 uyanma); arka plan işi günde bir
  kezdir ve indirme yalnızca veri azaldığında, ağ varken yapılır.
- Geri sayım yalnızca vakitler sayfası görünür ve ekran açıkken saniyede bir güncellenir.
- Pusula sensörleri yalnızca pusula sayfasında ve ekran açıkken, 25 Hz'de çalışır; ekran en fazla
  2 dk açık tutulur.
- GPS her açılışta çalışmaz: bilinen konum 6 saatten eskiyse en fazla 1 dk, ekranı açık tutmadan
  arar; 100 m doğrulukta durur.

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
  prayer/   # ezan vakitleri: Diyanet verisi, çevrimdışı hesap, bildirim planı
  notify/   # alarm, bildirim ve günlük arka plan işi
  data/     # konum (GPS), il/ilçe tablosu, ayarlar, ViewModel
  ui/       # pusula, ayrıntılar, konum, il/ilçe seçimi, kalibrasyon
qibla/src/main/assets/   # WMM.COF ve tr_districts.csv (Diyanet kimlikleriyle)
qibla/tools/             # Diyanet ilçe eşlemesini üreten betik
```
