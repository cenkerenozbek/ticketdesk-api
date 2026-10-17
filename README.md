# TicketDesk API

Java/Spring junior portfolyo projesi. Talep açma, filtreli listeleme, detay, durum değiştirme,
durum geçmişi, yorumlar ve durum bazında rapor özellikleri uygulanmıştır.
Teslimin doğrulama durumu [VALIDATION.md](VALIDATION.md) dosyasındadır.

## Teknolojiler

Java 21, Spring Boot 4.1.1, Spring MVC, Spring Security, Spring Data JPA/Hibernate,
Bean Validation, PostgreSQL 17, Flyway ve Maven. Bağımlılık sürümlerini Spring Boot yönetir.
Spring Boot 4.1.1'in Java 21 uyumu [resmî gereksinimlerde](https://docs.spring.io/spring-boot/system-requirements.html) doğrulandı.

## Yerel çalıştırma

Docker Desktop/Engine ve Docker Compose çalışıyor olmalı:

```sh
docker compose up --build
```

API: `http://localhost:8080/api/tickets`. PostgreSQL hazır olunca migration çalışır;
`demo` profili aşağıdaki kullanıcıları ve iki örnek talebi oluşturur.

5432 veya 8080 başka bir uygulama tarafından kullanılıyorsa portları değiştirebilirsin:

```sh
DB_PORT=55433 API_PORT=18080 docker compose up --build
```

Bu durumda API `http://localhost:18080` üzerinde açılır. `DB_PORT` yalnız host portunu
değiştirir; konteynerler birbirine `db:5432` üzerinden bağlanır.

| E-posta | Rol | Yerel demo parolası |
| --- | --- | --- |
| user@ticketdesk.local | USER | demo-password |
| other@ticketdesk.local | USER | demo-password |
| agent@ticketdesk.local | AGENT | demo-password |

Bu hesaplar sadece `demo` profilinde oluşturulur; parolalar BCrypt ile hash'lenir.
Compose yerel demo içindir, portları yalnız `127.0.0.1` üzerinde açar.
HTTP Basic kullanan bu sürüm curl/HTTP istemcileri içindir. Session/cookie yoktur,
CSRF kapalıdır; tarayıcı tabanlı bir arayüz eklenirse CSRF tasarımı ayrıca yapılmalıdır.
Uzaktan kullanım için HTTPS ve ayrı kimlik bilgileri gerekir.

```sh
# Durdur; veritabanı volume içinde kalır.
docker compose down
```

`docker compose down -v` volume'u ve verileri siler. Yeniden başlatma veriyi silmez;
demo seed işlemi mevcut kullanıcılar için tekrar talep oluşturmaz.

### Java ile çalıştırma

Java 21 gerekir; Maven Wrapper Maven'i indirir:

```sh
docker compose up -d --wait db
DB_PASSWORD=local-demo-db ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

| Değişken | Varsayılan / anlamı |
| --- | --- |
| DB_URL | jdbc:postgresql://localhost:5432/ticketdesk |
| DB_USER | ticketdesk |
| DB_PASSWORD | Java ile başlatırken zorunlu; Compose yerel demo değeri sağlar |
| PORT | 8080 |
| SPRING_PROFILES_ACTIVE | Varsayılan boş; `demo` örnek veriyi etkinleştirir |

Compose ayrıca host portları için `DB_PORT` (5432) ve `API_PORT` (8080) kullanır.
Java'yı doğrudan başlatırken özel DB portu için `DB_URL` değerini de buna göre ver.

Compose için isteğe bağlı `.env.example` dosyasını `.env` olarak kopyalayabilirsin.
Java doğrudan başlatıldığında `.env` otomatik okunmaz; çevre değişkenlerini kendin ver.
Gerçek secret değerlerini repoya ekleme.

## API sözleşmesi

| Metot ve yol | Yetki / davranış |
| --- | --- |
| POST /api/tickets | USER; 201 + Location |
| GET /api/tickets | USER kendi kayıtları, AGENT tümü |
| GET /api/tickets/{id} | Sahibi veya AGENT |
| PATCH /api/tickets/{id}/status | AGENT; geçerli geçiş ve geçmiş kaydı |
| GET /api/tickets/{id}/history | Sahibi veya AGENT; sayfalı, ID artan |
| POST /api/tickets/{id}/comments | Sahibi veya AGENT; 201 ve oluşturulan yorum |
| GET /api/tickets/{id}/comments | Sahibi veya AGENT; sayfalı, ID artan |
| GET /api/reports/tickets-by-status | AGENT; tüm kullanıcıların taleplerinin durum sayıları |

Liste filtreleri: `status=OPEN|IN_PROGRESS|RESOLVED`, `priority=LOW|MEDIUM|HIGH`.
`page` sıfırdan başlar; `size` 1–100, varsayılan 20.
Liste sıralaması `sort=id,asc`, `createdAt,desc` veya `title,asc` biçimindedir.
Yön `asc|desc`; varsayılan `createdAt,desc`. Eşit alanlar ID ile sıralanır.
Her istek tek sıralama alanı kabul eder.

Sayfa: `content`, `page`, `size`, `totalElements`, `totalPages`.
Entity'ler HTTP yanıtı olarak açılmaz; yanıtlar DTO'dur.

Başlık boş olamaz, en fazla 160 karakter; açıklama boş olamaz, en fazla 5000 karakter.
Öncelik zorunludur; yeni talebin durumu her zaman OPEN'dır.

Durum akışı: `OPEN → IN_PROGRESS → RESOLVED`. Aynı duruma geçiş dahil diğer geçişler 409'dur.
Başlangıç oluşturma işlemi geçmiş kaydı üretmez; geçmiş yalnız durum değişikliklerini tutar.

Yorum isteği: `{"body":"Bağlantıyı kontrol ediyorum"}`. İçerik boş olamaz, en fazla
5000 karakterdir; baştaki/sondaki boşluklar temizlenir. Yazar kimlik doğrulamasından alınır;
istemci `authorId` seçemez. Yanıt: `id`, `ticketId`, `authorId`, `body`, `createdAt`.
Yorum sayfalaması `page` ve `size` kullanır (varsayılan 0/20, boyut 1–100).
RESOLVED taleplere de yorum eklenebilir. Başka kullanıcıya ait veya olmayan talebin
yorumunu okuma/yazma isteği 404 döner.

Rapor tüm kullanıcıların taleplerini kapsar; SQL `GROUP BY` ile hesaplanır.
Yanıt sırası OPEN, IN_PROGRESS, RESOLVED'dır; boş durumlar sıfırla gösterilir:

```json
[{"status":"OPEN","count":2},{"status":"IN_PROGRESS","count":0},{"status":"RESOLVED","count":0}]
```

Yeni şema `V2__create_comments.sql` ile eklenir. Mevcut V1 veritabanı uygulama başlarken
güncellenir; kullanıcı, talep ve geçmiş verilerini sıfırlamak gerekmez.

Hatalar: geçersiz girdi 400, eksik/yanlış kimlik 401, rol ihlali 403,
olmayan veya kullanıcıya görünmeyen talep 404, geçersiz durum geçişi 409.

```json
{"status":404,"code":"TICKET_NOT_FOUND","message":"Ticket not found","fields":{}}
```

## Örnek akış

```sh
curl -i -u user@ticketdesk.local:demo-password \
  -H 'Content-Type: application/json' \
  -d '{"title":"Laboratuvar bilgisayarı açılmıyor","description":"B-204, masa 3","priority":"HIGH"}' \
  http://localhost:8080/api/tickets

curl -u user@ticketdesk.local:demo-password \
  'http://localhost:8080/api/tickets?status=OPEN&size=10&sort=createdAt,desc'

# Aşağıdaki 1 yerine oluşturma yanıtındaki id değerini kullan.
curl -u agent@ticketdesk.local:demo-password -X PATCH \
  -H 'Content-Type: application/json' -d '{"status":"IN_PROGRESS"}' \
  http://localhost:8080/api/tickets/1/status

curl -u user@ticketdesk.local:demo-password http://localhost:8080/api/tickets/1/history

curl -i -u user@ticketdesk.local:demo-password \
  -H 'Content-Type: application/json' -d '{"body":"Sorun hâlâ devam ediyor."}' \
  http://localhost:8080/api/tickets/1/comments

curl -u agent@ticketdesk.local:demo-password \
  'http://localhost:8080/api/tickets/1/comments?page=0&size=10'

curl -u agent@ticketdesk.local:demo-password \
  http://localhost:8080/api/reports/tickets-by-status
```

## Testler

```sh
# Veritabanı gerektirmez: domain, servis ve HTTP/security testleri.
./mvnw verify

# Gerçek PostgreSQL: migration, SQL filtreleri, sahiplik, commit ve rollback.
docker compose -f compose.test.yaml up -d --wait
./mvnw verify -Ppostgres-it
docker compose -f compose.test.yaml down
```

PostgreSQL testleri ayrı `ticketdesk_test` veritabanını ve 55432 portunu kullanır;
Compose test verisini geçici dosya sisteminde tutar. İsteğe bağlı `TEST_DB_URL`,
`TEST_DB_USER`, `TEST_DB_PASSWORD` ile **ayrı bir test veritabanı** verilebilir.
IT testleri kendi oluşturdukları kayıtları temizler. Normal `verify` bu testleri çalıştırmaz;
CI `-Ppostgres-it` ile ikisini birlikte çalıştırır. PostgreSQL yoksa IT komutu başarısız olur;
sessizce atlanmaz. Yeniden başlatmada veri kalıcılığı ayrıca Compose ile doğrulanmalıdır.

Test veritabanı imajı `TEST_POSTGRES_IMAGE` ile değiştirilebilir; varsayılan PostgreSQL 17'dir.
Farklı bir ana sürümle test çalıştırırsan doğrulama sonucunda o sürümü belirt.

Eşzamanlılık testinde bir transaction talep satırını kilitler; iki HTTP isteğinin
PostgreSQL'de kilit beklediği gözlemlenir. Kilit bırakılınca aynı `IN_PROGRESS` geçişi
için biri 200, diğeri 409 döner; veritabanında tek geçmiş kaydı olduğu kontrol edilir.
Bu test gerçek PostgreSQL bağlantıları kullanır; rastgele bir bekleme süresine dayanmaz.

### Docker ile uçtan uca kontrol

Python 3 ve Docker gerekir. Aşağıdaki akış yalnız test için ayrı bir Compose projesi
ve volume oluşturur. Betik demo kullanıcılarıyla bir talep ve iki yorum yazar:

```sh
export COMPOSE_PROJECT_NAME=ticketdesk-check
export DB_PORT=55433 API_PORT=18080
docker compose up -d --build --wait
python3 scripts/compose_smoke.py seed --base-url http://127.0.0.1:18080 --state /tmp/ticketdesk-smoke.json

# Konteynerleri kaldır; volume'u koru. Yeniden oluşturulduğunda aynı veriyi kontrol et.
docker compose down
docker compose up -d --wait
python3 scripts/compose_smoke.py check --base-url http://127.0.0.1:18080 --state /tmp/ticketdesk-smoke.json

# Yalnız yukarıdaki test projesinin örnek verilerini siler.
docker compose down -v
```

Kontrol betiği 401/403/404/409 yanıtlarını, filtre/sayfalamayı, eşzamanlı 200/409
durum geçişini, tek geçmiş kaydını, yorumları ve raporu denetler. `check` aşaması
talep, yorum, geçmiş ve demo hesaplarının konteynerler yeniden oluşturulduğunda
korunduğunu doğrular. CI'da aynı akış ayrı `compose-smoke` job'ında tanımlıdır.

### SQL alıştırmaları

`docs/sql-practice.sql` dosyasında kullanıcı talepleri, JOIN, GROUP BY,
yorumu olmayan talepler ve tarih sıralı sayfalama için beş salt okunur sorgu vardır:

```sh
docker compose exec -T db psql -U ticketdesk -d ticketdesk < docs/sql-practice.sql
```

## Tasarım kararları

- Controller HTTP/validasyon, service iş kuralları/transaction, repository veri erişiminden sorumlu.
- `users`, `tickets`, `comments` ve `ticket_status_history` ilişkileri FK ile korunur.
- Durum ve geçmiş aynı transaction içinde yazılır. Durum değişikliğinde satır kilidi,
  eşzamanlı iki isteğin eski durum üzerinden karar vermesini engeller.
- `open-in-view=false`; DTO dönüşümü service transaction'ı içinde yapılır.
- Talep, yorum ve geçmiş zamanları UTC `Instant` olarak tutulur. Yeni zamanlar
  PostgreSQL ile aynı mikrosaniye hassasiyetine getirilir; oluşturma yanıtı ile
  sonraki okumaların zamanı aynı kalır. Docker testinde yakalanan hata ve düzeltmesi
  `VALIDATION.md` içinde açıklanmıştır.
- `creator_id, created_at, id` indeksi kullanıcı listesi; `status, created_at, id`
  indeksi görevli filtresi; `ticket_id, id` indeksi geçmiş okuma içindir.
  İndeksler için ölçülmüş performans artışı iddiası yoktur.
- Yorumlardaki `ticket_id, id` indeksi belirli talebin yorumlarını sabit sırayla
  sayfalamak içindir. Yorumlarda da sahiplik kontrolü sorgudan önce yapılır.
- Rapor yalnız durum/sayı alanlarını SQL ile getirir; bütün talepleri belleğe yüklemez.
  Hem HTTP katmanında hem servis içinde AGENT kontrolü vardır.
- Demo kullanıcıları migration'a gömülmez; normal profilde bilinen demo parolaları oluşturulmaz.

## Sıradaki aşama

1. GitHub üzerinde CI çalıştırma ve kısa demo kaydı.
2. Kabul senaryolarını kullanıcının bağımsız olarak denemesi ve kodu açıklayabilmesi.

Bu uygulama, kaynak plandaki tüm teslim kontrollerinin tamamlandığı anlamına gelmez.
