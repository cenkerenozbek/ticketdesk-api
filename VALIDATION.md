# Üçüncü aşama doğrulaması — 18 Ekim 2026

## Otomatik testler

`./mvnw verify -Ppostgres-it`: **56 test, 0 hata, 0 başarısızlık, 0 atlama**.

| Katman | Test sayısı | Kapsam |
| --- | --- | --- |
| Domain | 10 | Durum geçişleri ve reddedilen geçişte kaydın korunması |
| Servis | 9 | Oluşturma, sahiplik, rol, geçmiş, yorum ve rapor erişimi |
| HTTP/security | 23 | 201/400/401/403/405/500 davranışları, validasyon ve sayfa sınırları |
| Gerçek PostgreSQL | 14 | Migration, kalıcılık, filtre/sayfalama/sıralama, sahiplik, yorumlar, rapor, transaction ve eşzamanlılık |

Son test çalıştırması PostgreSQL **17.11** üzerinde yapıldı. Host Java 21.0.5,
Maven Wrapper 3.3.4 ve Maven 3.9.9 kullanıldı. Yerel Maven önbelleği `work/`
klasörüne yönlendirildi. Önceki aşamada PostgreSQL 16.14 ile de doğrulama yapıldı.

Eşzamanlılık testinde bir transaction talep satırını kilitler. İki HTTP isteğinin
PostgreSQL'de aynı anda kilit beklediği gözlemlendikten sonra kilit bırakılır.
Sonuçlar 200 ve 409; talep IN_PROGRESS, geçmiş kaydı sayısı tam olarak birdir.

## Docker ve kalıcı volume

- Güncel Dockerfile ile `docker compose up -d --build --wait` başarılı oldu.
  Uygulama kaynakları Java 21 JDK build aşamasında Docker içinde derlendi.
  Önceden indirilen Maven bağımlılıkları BuildKit cache mount ile yeniden kullanıldı.
- İzole proje: `ticketdesk-check-01a14bef`. Makinedeki diğer servislerin portları
  kullanıldığı için DB_PORT=55433 ve API_PORT=18080 verildi.
- Linux arm64 API konteyneri root olmayan UID 999 ile çalıştı. Veritabanı PostgreSQL 17.11.
- `scripts/compose_smoke.py seed` başarılı oldu: talep açma, sahiplik, rol,
  401/403/404/409 yanıtları, filtre/sayfalama, eşzamanlı 200/409 geçişi,
  tek geçmiş kaydı, USER/AGENT yorumları ve durum raporu kontrol edildi.
- `docker compose down` ile API ve DB konteynerleri kaldırıldı; named volume korundu.
- `docker compose up -d --wait` ile iki konteyner de yeniden oluşturuldu.
  Önceki ve sonraki konteyner kimliklerinin farklı olduğu ayrıca doğrulandı.
- `scripts/compose_smoke.py check` başarılı oldu: aynı talep, zaman damgaları,
  IN_PROGRESS durumu, iki yorum, bir geçmiş kaydı ve rapor korundu.
  Demo hesapları çalışmaya devam etti ve seed verileri çoğalmadı.
- Yeniden oluşturmadan sonra DB'de başarılı V1/V2 migration kayıtları, üç talep,
  iki yorum ve bir durum geçmişi kaydı vardı.
- `docs/sql-practice.sql` içindeki beş sorgu PostgreSQL 17 üzerinde hatasız çalıştı.

## Docker testinde yakalanan ve düzeltilen hata

Linux'ta oluşturma yanıtı nanosaniye içerebiliyordu; PostgreSQL'den sonraki okuma
mikrosaniye hassasiyetinde dönüyordu. Düzeltme öncesi gerçek örnek:

```text
POST createdAt: 2026-10-17T22:48:38.717508382Z
GET  createdAt: 2026-10-17T22:48:38.717508Z
```

Talep, yorum ve durum geçmişi zamanları oluşturulurken mikrosaniyeye getirildi.
Böylece API oluşturma yanıtı ve sonraki okumalar aynı zamanı taşıyor. Talep/yorum
PostgreSQL testlerine POST/GET zaman eşitliği kontrolleri eklendi. Son Docker
smoke testi bu eşitlikleri konteyner yeniden oluşturulduktan sonra da doğruladı.
PostgreSQL'in zaman hassasiyeti [resmî belgede](https://www.postgresql.org/docs/17/datatype-datetime.html) açıklanır.

## Önceki aşamadan korunan doğrulama

Ayrı bir test veritabanında V1 şemasına kullanıcı, talep ve durum geçmişi yazıldı;
uygulama yeniden başlatılarak V2 yorum migration'ı uygulandı. Mevcut veriler
korundu; yeni yorumlar ve rapor uçtan uca doğrulandı.

## Sınırlar ve temizlik

- GitHub üzerinde CI çalıştırılmadı. Workflow hem PostgreSQL testlerini hem
  Docker `seed → down → up → check` akışını içeriyor; eşdeğer adımlar yerelde geçti.
- Kontrollerden sonra yalnız bu görev için açılmış geçici konteyner, ağ ve test
  volume'u kaldırılır. Kullanıcının diğer Docker projelerine dokunulmaz.

Kabul senaryolarının testlerle eşlemesi: [docs/acceptance.md](docs/acceptance.md).
