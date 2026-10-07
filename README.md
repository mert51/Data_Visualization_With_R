# SWE 307 – Big Data – Proje 1: R ile Veri Görselleştirme

Spring Boot (Java) + GraalVM (R / FastR) + MongoDB ile canlı veri görselleştirme uygulaması.

Java uygulaması MongoDB'den **her saniye bir satır** okur, okunan `double` değeri GraalVM Polyglot üzerinden **R fonksiyonuna** gönderir. R, `lattice` kütüphanesinin `xyplot()` fonksiyonuyla bir **SVG grafik** üretir ve tarayıcıya gönderir. Sayfa her saniye kendini yeniler, grafik dinamik olarak değişir.

---

## İçindekiler

1. [Ödev şartları ve bu projede nasıl karşılandıkları](#1-ödev-şartları-ve-bu-projede-nasıl-karşılandıkları)
2. [Mimari ve veri akışı](#2-mimari-ve-veri-akışı)
3. [Kullanılan sürümler ve ortam](#3-kullanılan-sürümler-ve-ortam)
4. [Kurulum A: WSL2 ve mirrored ağ modu](#4-kurulum-a-wsl2-ve-mirrored-ağ-modu)
5. [Kurulum B: GraalVM 22.3.0 (WSL)](#5-kurulum-b-graalvm-2230-wsl)
6. [Kurulum C: R (FastR) ve lattice (WSL)](#6-kurulum-c-r-fastr-ve-lattice-wsl)
7. [Kurulum D: Maven (WSL)](#7-kurulum-d-maven-wsl)
8. [Kurulum E: MongoDB ve Compass (Windows) + CSV içe aktarma](#8-kurulum-e-mongodb-ve-compass-windows--csv-içe-aktarma)
9. [Projeyi IntelliJ IDEA ile oluşturma](#9-projeyi-intellij-idea-ile-oluşturma)
10. [Proje dizin yapısı](#10-proje-dizin-yapısı)
11. [Kodlar ve açıklamaları](#11-kodlar-ve-açıklamaları)
12. [Uygulamanın çalışma mantığı (ayrıntılı)](#12-uygulamanın-çalışma-mantığı-ayrıntılı)
13. [Uygulamayı çalıştırma (her seferinde)](#13-uygulamayı-çalıştırma-her-seferinde)
14. [Beklenen çıktı](#14-beklenen-çıktı)
15. [Farklı sütun kullanmak](#15-farklı-sütun-kullanmak)
16. [Sorun giderme](#16-sorun-giderme)
17. [Sunum günü kontrol listesi](#17-sunum-günü-kontrol-listesi)

---

## 1. Ödev şartları ve bu projede nasıl karşılandıkları

| # | Ödev şartı | Bu projede |
|---|---|---|
| a | Her kişi GraalVM ve MongoDB kurmalı | GraalVM CE 22.3.0 (WSL), MongoDB (Windows) |
| b | CSV'den yalnızca **tek bir sütun** kullanılacak, CSV başta elle MongoDB'ye aktarılacak | `Col-2` kullanılıyor (`app.column`), CSV Compass ile içe aktarıldı |
| c | 100 satır var; Java her saniye **ardışık bir satırı döngüsel (cyclic) olarak** MongoDB'den okuyacak ve R fonksiyonuna gönderecek | `MongoDataService.next()` sayacı 0→99 gider, 100'e gelince başa döner; tarayıcı her saniye yenilenir |
| d | Mümkünse `lattice` kütüphanesinden `xyplot()`; x ekseni 0–99 tamsayı, y ekseni Java'dan gelen `double` | `plot.R` içinde `xyplot(y ~ x)`, `x = 0:99` |
| e | Çizgi tipi, çizgi rengi koyu kahverengi, grid kullan | `type = c("l", "g")`, `col.line = "#5C3317"` |
| f | Blok diyagram (Figure 1), örnek çıktı (Figure 2) | Ödev PDF'indeki Figure 1; çıktı tarayıcıda `localhost:8080/plot` |

> Not: "Okuma Java tarafından yapılır, R tarafından değil" şartı gereği MongoDB'ye yalnızca Java bağlanır. R'a sadece okunan sayı gönderilir.

---

## 2. Mimari ve veri akışı

```
                  Windows                                        WSL2 (Ubuntu)
 ┌───────────────────────────────────┐        ┌───────────────────────────────────────────────┐
 │                                   │        │   Spring Boot uygulaması (GraalVM JDK 17)     │
 │  Web tarayıcı                     │  HTTP  │  ┌─────────────────────────────────────────┐  │
 │  http://localhost:8080/plot  ◄────┼────────┼─►│ PlotController   (Web sunucusu: Tomcat) │  │
 │  (her 1 sn'de yenilenir)          │        │  │        │                  ▲             │  │
 │                                   │        │  │        ▼                  │ SVG         │  │
 │                                   │        │  │ MongoDataService     R fonksiyonu       │  │
 │  MongoDB Server  (port 27017)     │ TCP    │  │ (sıradaki satırı     (plot.R, FastR,    │  │
 │  admin.data_col  (100 doküman) ◄──┼────────┼──┤  okur, sayaç döner)   xyplot → SVG)     │  │
 │        ▲                          │        │  └─────────────────────────────────────────┘  │
 │        │ elle içe aktarma         │        │                                               │
 │  swe307_pro1.csv (Compass)        │        │   GraalVM Polyglot (Java ⇄ R)                 │
 └───────────────────────────────────┘        └───────────────────────────────────────────────┘
```

Bir tarayıcı isteğinin yolculuğu:

1. Tarayıcı `GET /plot` ister.
2. `PlotController`, `MongoDataService.next()` ile **sıradaki satırdaki** sütun değerini okur (ör. `-0.9`).
3. Değer `DataHolder` nesnesine konur ve R fonksiyonuna verilir (`plotFunction.apply(...)`).
4. R, son 100 değeri tuttuğu tamponu günceller, `xyplot` ile çizer ve **SVG metni** döndürür.
5. Controller SVG'yi `image/svg+xml` olarak ve `Refresh: 1` başlığıyla geri gönderir. Tarayıcı 1 saniye sonra aynı sayfayı yeniden ister → döngü sürer.

---

## 3. Kullanılan sürümler ve ortam

| Bileşen | Sürüm / Not |
|---|---|
| İşletim sistemi | Windows 11 + WSL2 (Ubuntu), WSL ağ modu: **mirrored** |
| GraalVM | **GraalVM CE 22.3.0, Java 17.0.5** (ödevin istediği sürüm; `gu` bu sürümden sonra kaldırıldı) |
| R | FastR 4.0.3 (GraalVM bileşeni, `gu install R` ile) |
| R paketi | `lattice` (FastR ile birlikte hazır geliyor) |
| Maven | 3.8.7 (`sudo apt install maven`) |
| Spring Boot | 4.1.1 (IntelliJ Spring Initializr ile) |
| Bağımlılıklar | Spring Web (`spring-boot-starter-webmvc`), Spring Data MongoDB, `org.graalvm.sdk:graal-sdk:22.3.0` |
| MongoDB | Windows'ta yerel kurulum, port 27017, kimlik doğrulama yok |
| MongoDB arayüzü | MongoDB Compass |
| IDE | IntelliJ IDEA (Windows'ta çalışır, projeyi WSL içindeki dosya sistemine açar) |

Proje WSL içinde şu konumdadır: `/home/mert/spring_r` (Windows'tan: `\\wsl.localhost\Ubuntu\home\mert\spring_r`).

---

## 4. Kurulum A: WSL2 ve mirrored ağ modu

### 4.1 Neden mirrored mod?

Uygulama WSL içinde, MongoDB ise Windows'ta çalışıyor. Varsayılan **NAT** modunda WSL içinden `localhost:27017` Windows'taki MongoDB'ye **ulaşmaz** (WSL'in kendi sanal ağı vardır) ve Windows tarafında `bindIp` ile güvenlik duvarı ayarı gerekir. **Mirrored** modda WSL ve Windows ağ arayüzlerini paylaşır; WSL'deki `localhost` Windows'taki `localhost` ile aynı olur. Böylece:

- MongoDB ayarlarına dokunulmaz, MongoDB ağa açılmaz.
- Bağlantı adresi sabit kalır: `mongodb://localhost:27017`.
- Windows tarayıcısı WSL'deki uygulamaya `http://localhost:8080` ile ulaşır.

Gereksinim: Windows 11 22H2 veya üstü.

### 4.2 `.wslconfig` dosyasını oluşturma

1. `Win + R` → `%UserProfile%` yaz → Enter.
2. `.wslconfig` dosyasını oluştur ya da aç (PowerShell'de: `notepad $env:UserProfile\.wslconfig`).
3. İçeriği şöyle olmalı:

```ini
[wsl2]
networkingMode=mirrored
```

> Dikkat: Dosya adı tam olarak `.wslconfig` olmalı (`.wslconfig.txt` olmamalı). Not Defteri'nde "Farklı Kaydet" kullanırsan tür olarak **Tüm Dosyalar** seç. Dosyada zaten `[wsl2]` bölümü varsa ikinci bir tane açma, satırı mevcut bölümün altına ekle.

### 4.3 WSL'i yeniden başlatma

Windows PowerShell'de:

```powershell
wsl --shutdown
```

Sonra Ubuntu'yu yeniden aç.

### 4.4 Doğrulama (Ubuntu terminalinde)

```bash
wslinfo --networking-mode
timeout 3 bash -c '</dev/tcp/127.0.0.1/27017' && echo "localhost OPEN" || echo "localhost CLOSED"
```

Beklenen çıktı:

```
mirrored
localhost OPEN
```

`localhost OPEN` görmek için MongoDB'nin Windows'ta çalışıyor olması gerekir (bkz. Bölüm 8).

---

## 5. Kurulum B: GraalVM 22.3.0 (WSL)

Tüm komutlar **Ubuntu (WSL)** terminalinde çalıştırılır.

### 5.1 İndirme ve açma

```bash
cd ~
wget https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-22.3.0/graalvm-ce-java17-linux-amd64-22.3.0.tar.gz
tar -xzf graalvm-ce-java17-linux-amd64-22.3.0.tar.gz
```

Sonuçta `/home/mert/graalvm-ce-java17-22.3.0` klasörü oluşur.

### 5.2 `.bashrc` düzenleme

```bash
nano ~/.bashrc
```

Dosyanın sonuna ekle (kullanıcı adın `mert` değilse yolu ona göre değiştir):

```bash
export JAVA_HOME=/home/mert/graalvm-ce-java17-22.3.0
export PATH=$JAVA_HOME/bin:$PATH
```

Kaydet (Ctrl + O, Enter), çık (Ctrl + X) ve uygula:

```bash
source ~/.bashrc
```

### 5.3 Doğrulama

```bash
java -version
echo $JAVA_HOME
which java
gu list
```

Beklenen:

- `java -version` → `OpenJDK Runtime Environment GraalVM CE 22.3.0 (build 17.0.5+8-jvmci-22.3-b08)`
- `which java` → `/home/mert/graalvm-ce-java17-22.3.0/bin/java`
- `gu list` → `graalvm`, `R` (FastR), `llvm`, `llvm-toolchain`, `native-image`

---

## 6. Kurulum C: R (FastR) ve lattice (WSL)

### 6.1 R'ı GraalVM'e kurma

```bash
gu install R
```

Bu komut hata verirse sistem kütüphaneleri eksik olabilir:

```bash
sudo apt update
sudo apt install -y build-essential gfortran libxml2 libpcre2-dev libpcre3-dev libssl-dev \
    libcurl4-openssl-dev zlib1g-dev libreadline-dev libbz2-dev liblzma-dev libicu-dev libc++-dev
$JAVA_HOME/languages/R/bin/configure_fastr
```

### 6.2 R ve lattice testi

```bash
which R
R --version
R -e 'library(lattice); print("lattice OK")'
```

Beklenen: `which R` GraalVM klasörünü gösterir, sürüm `R version 4.0.3 (FastR)`, ve `lattice OK` yazar.

> `Setting LC_COLLATE to C.UTF-8 failed, using default` gibi **locale uyarıları zararsızdır**, yok sayılabilir.

### 6.3 SVG cihazı testi (projenin kritik noktası)

Proje `svg()` grafik cihazını kullanır; FastR'da çalıştığını doğrula:

```bash
R -e 'library(lattice); svg(); p <- xyplot(y~x, data=data.frame(x=0:9,y=(0:9)^2), type=c("l","g"), col.line="#5C3317"); print(p); x <- svg.off(); print("svg OK")'
```

Beklenen: `[1] "svg OK"`.

---

## 7. Kurulum D: Maven (WSL)

```bash
sudo apt update
sudo apt install -y maven
mvn -v
```

Çıktıda şu satırı kontrol et (Maven'in GraalVM'i kullandığından emin olmak için):

```
Java version: 17.0.5, vendor: GraalVM Community, runtime: /home/mert/graalvm-ce-java17-22.3.0
```

`runtime` başka bir JDK'yı gösteriyorsa `JAVA_HOME` ayarı hatalıdır (Bölüm 5.2).

---

## 8. Kurulum E: MongoDB ve Compass (Windows) + CSV içe aktarma

### 8.1 MongoDB kurulumu

1. MongoDB Community Server'ı Windows için indirip kur (kurulumda **"Install MongoDB as a Service"** seçili olsun, **MongoDB Compass** da kurulsun).
2. Servisin çalıştığını doğrula: `Win + R` → `services.msc` → **MongoDB Server** durumu **Çalışıyor** olmalı.

### 8.2 Veriyi hazırlama

Ödev CSV dosyası: `swe307_pro1.csv`

Yapısı:

- 1. satır başlık: `,Col-1,Col-2,...,Col-21` (ilk başlık **boş**)
- 100 veri satırı: `Row-1, Row-2, ..., Row-100`
- 21 sayısal sütun (`Col-1` … `Col-21`)

### 8.3 Compass ile içe aktarma

1. Compass'ı aç, `mongodb://localhost:27017` adresine bağlan.
2. Yeni bir veritabanı ve koleksiyon oluştur:
   - Veritabanı: **`admin`**
   - Koleksiyon: **`data_col`**
   (Bu projede veritabanı adı `admin` olarak kullanıldı; farklı isim seçersen `application.properties` içindeki değerleri de değiştir.)
3. `data_col` koleksiyonunu aç → **ADD DATA → Import JSON or CSV file** → `swe307_pro1.csv` dosyasını seç.
4. Dosya türü **CSV** olsun. Alan tiplerinin sayısal sütunlar için **Double/Number** olduğunu kontrol et.
5. **Import** düğmesine bas.

### 8.4 Doğrulama

Compass'ta `SWE307 → admin → data_col` altında **Documents: 100** görünmelidir. İlk doküman şuna benzer olmalıdır:

```
_id: ObjectId('...')
"" : "Row-1"        ← satır adı, boş başlıklı ilk sütun yüzünden anahtarı boş string
Col-1: 0
Col-2: -1
Col-3: 0.000031
...
Col-21: 0
```

İkinci doküman `Row-2` (`Col-1: 0.25114798`, `Col-2: -0.9`, ...) olmalıdır.

> **Önemli:** Satır sırası, `_id` (ObjectId) sırasıyla aynıdır. Java kodu satırları `_id`'ye göre artan sıralayarak okur. Bu yüzden CSV'yi **bir kez, tek seferde** içe aktar; aynı veriyi tekrar eklersen koleksiyonda 200 doküman olur ve döngü bozulur. Hata yaparsan koleksiyonu silip yeniden içe aktar.

### 8.5 (Alternatif) Komut satırıyla içe aktarma

MongoDB Database Tools kuruluysa:

```bash
mongoimport --db admin --collection data_col --type csv --headerline --file swe307_pro1.csv
```

---

## 9. Projeyi IntelliJ IDEA ile oluşturma

IntelliJ Windows'ta çalışır, ama proje **WSL dosya sistemi içinde** oluşturulur.

### 9.1 New Project ekranı (Generators → Spring Boot)

| Alan | Değer |
|---|---|
| Server URL | `start.spring.io` |
| Name | `spring_r` |
| Location | `\\wsl.localhost\Ubuntu\home\mert` |
| Create Git repository | işaretsiz |
| Language | Java |
| Type | Maven |
| Group | `org.graalvm.demos` |
| Artifact | `spring_r` |
| Package name | `org.graalvm.demos.spring_r` |
| JDK | GraalVM CE 17.0.5 - VM 22.3.0 (WSL içindeki `\\wsl.localhost\Ubuntu\...`) |
| Java | 17 |
| Packaging | Jar |
| Configuration | Properties |

### 9.2 Dependencies ekranı

- Spring Boot: `4.1.1`
- **Spring Web**
- **NoSQL → Spring Data MongoDB** (reaktif olmayan sürüm)

**Create** düğmesine bas.

### 9.3 `pom.xml`'e GraalVM SDK ekleme

`<dependencies>` bölümünün içine ekle (R'ı Java'dan çağıran `Context` ve `Source` sınıfları için):

```xml
<dependency>
    <groupId>org.graalvm.sdk</groupId>
    <artifactId>graal-sdk</artifactId>
    <version>22.3.0</version>
</dependency>
```

Sonuçta `<dependencies>` bölümü şöyle görünür:

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-mongodb</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webmvc</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-mongodb-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webmvc-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.graalvm.sdk</groupId>
        <artifactId>graal-sdk</artifactId>
        <version>22.3.0</version>
    </dependency>
</dependencies>
```

Değişiklikten sonra IntelliJ'de `pom.xml`'e sağ tıkla → **Maven → Reload project**.

### 9.4 IntelliJ terminalini WSL'e ayarlama

IntelliJ terminalinde **`Local` sekmesi PowerShell**'dir. Maven ve R komutları için **`Ubuntu`** sekmesini kullan: terminal sekmesindeki `+` yanındaki `˅` oku → **Ubuntu**. Komut satırı şuna benzemelidir: `mert@V51:~/spring_r$`.

---

## 10. Proje dizin yapısı

```
spring_r/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── org/graalvm/demos/spring_r/
│   │   │       ├── SpringRApplication.java   ← Spring Boot başlangıç sınıfı (IntelliJ oluşturur)
│   │   │       ├── DataHolder.java           ← R'a giden veriyi taşıyan sınıf
│   │   │       ├── MongoDataService.java     ← MongoDB'den döngüsel okuma
│   │   │       └── PlotController.java       ← /plot adresi, R'ı çağırır
│   │   └── resources/
│   │       ├── application.properties        ← bağlantı ve sütun ayarları
│   │       └── plot.R                        ← R fonksiyonu (xyplot)
│   └── test/
│       └── java/org/graalvm/demos/spring_r/
│           └── SpringRApplicationTests.java  ← otomatik oluşur, dokunma
└── target/                                   ← derleme çıktısı (otomatik)
```

> Dikkat: `PlotController.java` mutlaka `src/main/java` altında olmalı. `src/test/java` altında oluşturulursa Spring bu sınıfı görmez ve `/plot` adresi 404 verir.

Ödev yardım sunumundaki yapı ile bire bir benzerdir (`DataHolder`, `PlotController`, `SpringRApplication`, `plot.R`, `application.properties`); ek olarak MongoDB okuma işini yapan `MongoDataService` bulunur.

---

## 11. Kodlar ve açıklamaları

### 11.1 `application.properties`

Yol: `src/main/resources/application.properties`

```properties
spring.application.name=spring_r
app.mongo.uri=mongodb://localhost:27017
app.mongo.database=admin
app.mongo.collection=data_col
app.column=Col-2
```

| Anahtar | Anlamı |
|---|---|
| `app.mongo.uri` | MongoDB adresi. Mirrored mod sayesinde `localhost` Windows'taki MongoDB'dir |
| `app.mongo.database` | Veritabanı adı (`admin`) |
| `app.mongo.collection` | Koleksiyon adı (`data_col`) |
| `app.column` | Grafiğe çizilecek sütun. **Grubuna ait sütunu buraya yaz** |

Bu değerler `@Value` ile okunur; böylece sütunu değiştirmek için kodu düzenlemek gerekmez.

### 11.2 `DataHolder.java`

Yol: `src/main/java/org/graalvm/demos/spring_r/DataHolder.java`

```java
package org.graalvm.demos.spring_r;

public class DataHolder {
    public double value;

    public DataHolder(double v) {
        value = v;
    }
}
```

Sadece **veri taşıyıcı**dır (model). Java'dan R'a tek bir `double` değer götürür. R tarafında `dataHolder$value` ile okunur. Alanın `public` olması R'ın (polyglot üzerinden) alana erişebilmesi için gereklidir.

### 11.3 `MongoDataService.java`

Yol: `src/main/java/org/graalvm/demos/spring_r/MongoDataService.java`

```java
package org.graalvm.demos.spring_r;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Sorts;
import jakarta.annotation.PreDestroy;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MongoDataService {

    private final MongoClient client;
    private final MongoCollection<Document> collection;
    private final String column;
    private int index = 0;   // siradaki satir: 0..99

    public MongoDataService(@Value("${app.mongo.uri}") String uri,
                            @Value("${app.mongo.database}") String database,
                            @Value("${app.mongo.collection}") String coll,
                            @Value("${app.column}") String column) {
        this.client = MongoClients.create(uri);
        this.collection = client.getDatabase(database).getCollection(coll);
        this.column = column;
    }

    /** Siradaki satirdan secili sutunu okur, son satirdan sonra basa doner. */
    public synchronized double next() {
        long total = collection.countDocuments();
        if (total == 0) {
            throw new IllegalStateException("Koleksiyon bos");
        }
        if (index >= total) {
            index = 0;
        }
        Document doc = collection.find()
                .sort(Sorts.ascending("_id"))   // import sirasi = satir sirasi
                .skip(index)
                .limit(1)
                .first();
        index = (index + 1) % (int) total;
        return doc.get(column, Number.class).doubleValue();
    }

    @PreDestroy
    void close() {
        client.close();
    }
}
```

Satır satır:

- `@Service`: Spring bu sınıftan tek bir nesne oluşturur ve `PlotController`'a enjekte eder.
- Yapıcı metot: MongoDB'ye bağlanır, veritabanı/koleksiyonu seçer, sütun adını saklar.
- `index`: Sıradaki satırın numarası (0'dan 99'a). Uygulama başladığında 0'dır.
- `next()`:
  1. `countDocuments()` ile toplam satır sayısını alır (bu projede 100).
  2. `index` toplamı aşmışsa başa (0) döner.
  3. `_id`'ye göre artan sıralayıp `skip(index).limit(1)` ile **yalnızca bir doküman** getirir.
  4. `index`'i bir artırır, toplama ulaşınca modül işlemi ile 0'a döndürür → **döngüsel okuma**.
  5. Seçili sütunun değerini `double` olarak döndürür. `Number` kullanılması önemlidir: MongoDB'de `0` ve `-1` gibi değerler `int` olarak, `0.25` gibileri `double` olarak saklanabilir, `Number` ikisini de karşılar.
- `synchronized`: Aynı anda gelen iki isteğin sayacı bozmasını engeller.
- `@PreDestroy`: Uygulama kapanırken MongoDB bağlantısını kapatır.

> Neden kendi `MongoClient`'ımız var? Spring Boot sürümleri arasında bağlantı ayar anahtarları değiştiği için (`spring.data.mongodb.uri` / `spring.mongodb.uri`), özel `app.mongo.*` anahtarlarıyla çalışmak sürümden bağımsız ve daha güvenlidir.

### 11.4 `plot.R`

Yol: `src/main/resources/plot.R`

```r
library(lattice)
data <<- numeric(100)

function(dataHolder) {
    svg()
    data <<- c(data[2:100], dataHolder$value)

    plot <- xyplot(y ~ x,
        data = data.frame(x = 0:99, y = data),
        main = "Data Visualization with R",
        xlab = "x (sample index)",
        ylab = "Value",
        type = c("l", "g"),
        col.line = "#5C3317")
    print(plot)
    svg.off()
}
```

Satır satır:

- `library(lattice)`: `xyplot()` fonksiyonunu yükler.
- `data <<- numeric(100)`: Uzunluğu 100 olan, sıfırlarla dolu bir **tampon** oluşturur. `<<-` ile global (fonksiyon dışı) değişken olur; böylece her çağrıda değer korunur.
- `function(dataHolder) { ... }`: Dosyanın **son ifadesi bir fonksiyondur**. Java `ctx.eval(source)` ile bu dosyayı çalıştırınca fonksiyonu geri alır ve `Function` olarak kullanır.
- `svg()`: Grafiği ekrana değil, bir SVG metnine çizen grafik cihazını açar.
- `data <<- c(data[2:100], dataHolder$value)`: **Kayan pencere**. En eski değer (1. eleman) atılır, yeni gelen değer sona eklenir. Tampon hep son 100 değeri tutar.
- `xyplot(y ~ x, data = data.frame(x = 0:99, y = data), ...)`: x ekseni 0–99 tamsayı, y ekseni tampondaki değerler.
- `type = c("l", "g")`: `"l"` = çizgi, `"g"` = grid (ızgara).
- `col.line = "#5C3317"`: **Koyu kahverengi**. R'da `"dark brown"` diye hazır bir renk adı yoktur, bu yüzden hex kod kullanılır.
- `print(plot)`: Lattice grafikleri `print()` ile çizilir (fonksiyon içinde otomatik çizilmez).
- `svg.off()`: Cihazı kapatır ve **SVG metnini döndürür**. Bu fonksiyonun son ifadesi olduğu için fonksiyonun dönüş değeri SVG'dir.

### 11.5 `PlotController.java`

Yol: `src/main/java/org/graalvm/demos/spring_r/PlotController.java`

```java
package org.graalvm.demos.spring_r;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.function.Function;

@RestController
public class PlotController {

    private final MongoDataService dataService;
    private final Function<DataHolder, String> plotFunction;

    @SuppressWarnings("unchecked")
    public PlotController(MongoDataService dataService,
                          @Value("classpath:plot.R") Resource rSource) throws IOException {
        this.dataService = dataService;
        Context ctx = Context.newBuilder().allowAllAccess(true).build();
        Source source = Source.newBuilder("R", rSource.getURL()).build();
        this.plotFunction = ctx.eval(source).as(Function.class);
    }

    @GetMapping(value = "/plot", produces = "image/svg+xml")
    public ResponseEntity<String> plot() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Refresh", "1");   // tarayici sayfayi her saniye yeniler

        String svg;
        synchronized (plotFunction) {
            svg = plotFunction.apply(new DataHolder(dataService.next()));
        }
        return new ResponseEntity<>(svg, headers, HttpStatus.OK);
    }
}
```

Satır satır:

- `@RestController`: Bu sınıf web isteklerini karşılar.
- Yapıcı metot:
  - `MongoDataService` otomatik enjekte edilir.
  - `@Value("classpath:plot.R") Resource rSource`: `resources/plot.R` dosyasını verir.
  - `Context.newBuilder().allowAllAccess(true).build()`: **GraalVM Polyglot bağlamı**. Java'nın R'ı çalıştırabilmesi ve Java nesnelerine (`DataHolder`) erişebilmesi için tüm erişimler açılır.
  - `Source.newBuilder("R", ...)`: Kaynağın dili `"R"` olarak belirtilir.
  - `ctx.eval(source).as(Function.class)`: R dosyasını çalıştırır, dosyanın sonundaki R fonksiyonunu Java `Function` arayüzüne çevirir.
- `@GetMapping("/plot", produces = "image/svg+xml")`: `http://localhost:8080/plot` isteğini karşılar, yanıt türü SVG'dir.
- `headers.set("Refresh", "1")`: Tarayıcıya "bu sayfayı 1 saniye sonra yeniden iste" der. Böylece grafik **her saniye** değişir. Ayrı bir zamanlayıcı yoktur; "saniyede bir" tarayıcı yenilemesinden gelir.
- `synchronized (plotFunction)`: R fonksiyonu aynı anda birden fazla iş parçacığı tarafından çağrılamaz (R bağlamı iş parçacığı güvenli değildir). Kilit bunu sağlar.
- `dataService.next()` → sıradaki değer, `new DataHolder(...)` → R'a gönderilecek nesne, `plotFunction.apply(...)` → R'ı çalıştırır, SVG metnini döndürür.

### 11.6 `SpringRApplication.java`

IntelliJ'in oluşturduğu başlangıç sınıfıdır, **değiştirilmez**:

```java
package org.graalvm.demos.spring_r;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SpringRApplication {
    public static void main(String[] args) {
        SpringApplication.run(SpringRApplication.class, args);
    }
}
```

Spring bu sınıfın bulunduğu paketi ve **alt paketlerini** tarar. Yeni sınıfların hepsi aynı pakette (`org.graalvm.demos.spring_r`) olduğu için otomatik bulunurlar.

---

## 12. Uygulamanın çalışma mantığı (ayrıntılı)

### 12.1 Kayan pencere (sliding window)

R'daki `data` tamponu her zaman 100 elemanlıdır. Başlangıçta hepsi sıfırdır. Her istekte:

```
önce:  [0, 0, 0, ..., 0, 0]               (100 eleman)
istek 1 (değer -1.0):    [0, 0, ..., 0, -1.0]
istek 2 (değer -0.9):    [0, ..., 0, -1.0, -0.9]
...
istek 100:               tamponun tamamı gerçek veri
istek 101:               en eski değer atılır, yeni değer sağdan girer
```

Bu yüzden grafik ilk ~100 saniye soldan düz bir çizgiyle (sıfırlar) başlar ve sağdan gerçek veriyle dolar. Ödev PDF'indeki Figure 2 de aynı davranışı gösterir. Ödevin farkı: x ekseni `-99…0` yerine **`0…99`**'dur.

### 12.2 Döngüsel okuma

`MongoDataService.index` 0'dan başlar ve her `next()` çağrısında bir artar; 100'e ulaşınca 0'a döner. Dolayısıyla veri sonsuza dek `Row-1, Row-2, ..., Row-100, Row-1, ...` sırasıyla okunur.

### 12.3 `Col-2` verisinin şekli

`Col-2` değerleri −1.0'dan başlayıp 0.1'lik adımlarla 0.9'a kadar çıkar, sonra tekrar −1.0'a düşer (20 satırlık bir "testere dişi", 100 satırda 5 kez). Tampon dolunca ekranda 5 testere dişi görünür.

### 12.4 Neden tek sekme?

Sayaç **sunucuda** tutulur ve her `/plot` isteği bir satır tüketir. İki sekme açık olursa grafik iki kat hızlı ilerler. Sunumda **tek sekme** kullan.

---

## 13. Uygulamayı çalıştırma (her seferinde)

Sırayla:

1. **MongoDB çalışıyor mu?** Windows'ta `services.msc` → **MongoDB Server** = Çalışıyor. (İstersen Compass ile `mongodb://localhost:27017` bağlantısını dene.)
2. **WSL mirrored modda mı?** Ubuntu terminalinde:
   ```bash
   wslinfo --networking-mode      # mirrored yazmalı
   ```
3. **8080 portu boş mu?**
   ```bash
   ss -ltnp | grep 8080           # hiçbir şey yazmamalı
   ```
4. **Uygulamayı başlat:**
   ```bash
   cd ~/spring_r
   mvn spring-boot:run
   ```
   Şu satırı görene kadar bekle (ilk açılışta R ortamı yüklendiği için biraz sürebilir):
   ```
   Started SpringRApplication in X seconds
   ```
   Bu terminal sekmesini **açık bırak**; uygulama burada çalışır.
5. **Tarayıcıda aç** (Windows tarayıcısı, tek sekme):
   ```
   http://localhost:8080/plot
   ```
6. **Durdurmak için:** uygulamanın çalıştığı terminalde `Ctrl + C`.

IntelliJ kullanıyorsan çalıştırma işi için `Ubuntu` terminal sekmesini kullan (Bölüm 9.4).

---

## 14. Beklenen çıktı

- Sayfa **her saniye kendini yeniler**.
- **Koyu kahverengi** çizgi, arka planda **grid**.
- Başlık: *Data Visualization with R*, x ekseni **0–99**, y ekseni *Value*.
- İlk saniyelerde soldan düz bir çizgi (0), sağdan gerçek veri girer. ~100 saniye sonra tampon tamamen dolar.
- `Col-2` için 5 adet testere dişi (−1.0 → 0.9) görünür ve veri akmaya devam eder (satırlar başa dönerken dalga kesintisiz sürer).

---

## 15. Farklı sütun kullanmak

Yalnızca `application.properties` içindeki satırı değiştir (ör. 7. sütun için):

```properties
app.column=Col-7
```

Kaydet, çalışan uygulamayı `Ctrl + C` ile durdur ve `mvn spring-boot:run` ile yeniden başlat. Java ya da R kodunda başka bir değişiklik gerekmez.

Not: Sütuna göre değer aralığı değişir (ör. `Col-1` 0'dan başlar ve −1…1 arasındadır, `Col-2` −1'den başlar). R eksen aralığını otomatik ayarlar.

---

## 16. Sorun giderme

### 16.1 `Non-readable POM ... input contained no data` (Maven)

`~/.m2` önbelleğindeki bir POM dosyası yarım/boş inmiş. Boş dosyaları temizleyip yeniden indir:

```bash
find ~/.m2/repository -type f -size 0 -print -delete
cd ~/spring_r
mvn -U dependency:resolve
find ~/.m2/repository -type f -size 0      # hiçbir şey yazmamalı
```

Belirli bir klasör inatla bozuksa onu da sil (ör. `rm -rf ~/.m2/repository/org/apache/apache/35`) ve `mvn -U dependency:resolve` tekrarla. Ardından IntelliJ'de **Maven → Reload project**.

### 16.2 `Web server failed to start. Port 8080 was already in use`

Uygulamanın eski bir kopyası hâlâ açık. Çalıştığı terminal sekmelerinde `Ctrl + C` yap. Kontrol:

```bash
ss -ltnp | grep 8080
```

Satır varsa süreci kapat:

```bash
kill $(ss -ltnp | grep 8080 | grep -o 'pid=[0-9]*' | cut -d= -f2)
```

Mirrored modda Windows tarafındaki bir program da 8080'i tutabilir (`netstat -ano | findstr :8080`). Bu durumda `application.properties` dosyasına `server.port=8081` ekleyip adresi `localhost:8081/plot` olarak kullan.

### 16.3 Tarayıcıda `404 Not Found` (`/plot`)

- `PlotController.java` `src/main/java` altında mı? (`src/test` altında oluşturulduysa taşı: sağ tık → Refactor → Move Class.)
- Dosya, `SpringRApplication` ile aynı pakette mi (`package org.graalvm.demos.spring_r;`)?
- Dosyayı ekledikten sonra uygulamayı yeniden başlattın mı?

### 16.4 IntelliJ'de `Cannot resolve symbol 'springframework'` (kırmızı hatalar)

- `pom.xml` → sağ tık → **Maven → Reload project** (ya da Maven sekmesinde 🔄).
- Hâlâ varsa **File → Invalidate Caches… → Invalidate and Restart**.
- Doğrulama için terminalde `mvn -q compile` çalıştır; çıktı boşsa kod sorunsuz derleniyor, IDE sadece senkronize olmamıştır.

### 16.5 `localhost CLOSED` (Mongo'ya erişilemiyor)

- Windows'ta MongoDB servisi çalışıyor mu? (`services.msc`)
- `wslinfo --networking-mode` çıktısı `mirrored` mı? `nat` ise `.wslconfig` dosyasını (Bölüm 4.2) kontrol et ve `wsl --shutdown` ile yeniden başlat. Dosya adı `.wslconfig.txt` olmamalı.
- Windows sürümün mirrored modu desteklemiyorsa (Windows 11 22H2+ gerekir) alternatif: `mongod.cfg` içinde `bindIp` değerine WSL'in gördüğü Windows adresini ekle ve güvenlik duvarında 27017'ye izin ver.

### 16.6 Uygulama açılıyor ama grafik gelmiyor / R hatası

- R'ın çalıştığını doğrula: Bölüm 6.2 ve 6.3 testleri.
- `plot.R` dosyasının `src/main/resources/` altında olduğundan emin ol.
- Uygulama GraalVM ile çalışıyor mu? `mvn -v` çıktısındaki `runtime` GraalVM yolunu göstermeli.
- İlk istek yavaş olabilir (R'ın ilk çalışması); birkaç saniye bekle.

### 16.7 `Command 'mvn' not found`

Maven kurulu değil: `sudo apt install maven` (Bölüm 7).

### 16.8 Terminal komutları çalışmıyor

IntelliJ terminalinde **`Local` sekmesi PowerShell**'dir. `mvn`, `R`, `find`, `ss` gibi komutlar için **`Ubuntu`** sekmesini kullan.

### 16.9 Grafik 100'den fazla doküman gösteriyor / döngü bozuk

CSV'yi iki kez içe aktarmış olabilirsin. Compass'ta `data_col` içinde **Documents: 100** olmalı. Fazlaysa koleksiyonu silip CSV'yi bir kez tekrar içe aktar.

### 16.10 `Setting LC_COLLATE to C.UTF-8 failed`

R başlarken çıkan zararsız locale uyarısıdır, yok sayılabilir.

---

## 17. Sunum günü kontrol listesi

- [ ] Windows'ta **MongoDB Server** servisi çalışıyor
- [ ] Compass'ta `admin.data_col` içinde **100 doküman** var
- [ ] `application.properties` içinde `app.column` = grubunun sütunu (şu an `Col-2`)
- [ ] Ubuntu terminali açık: `wslinfo --networking-mode` → `mirrored`
- [ ] `ss -ltnp | grep 8080` boş
- [ ] `cd ~/spring_r && mvn spring-boot:run` → `Started SpringRApplication ...`
- [ ] Tarayıcıda **tek sekme**: `http://localhost:8080/plot`
- [ ] Grafik: koyu kahverengi çizgi, grid, x ekseni 0–99, saniyede bir yenileniyor
- [ ] Ekran görüntüleri alındı (tampon dolmadan ve ~100 saniye sonra)
- [ ] Blok diyagram (Figure 1) hazır
- [ ] Teslim dosyaları: `pom.xml`, 4 Java dosyası, `plot.R`, `application.properties` (`target/` ve `.idea/` hariç)

Başka bir bilgisayarda sunulacaksa o makinede de sırasıyla şunlar gerekir: WSL2 + mirrored mod (Bölüm 4), GraalVM 22.3.0 (5), R/FastR (6), Maven (7), MongoDB ve CSV içe aktarma (8), proje dosyalarının kopyalanması ve `mvn spring-boot:run`.
