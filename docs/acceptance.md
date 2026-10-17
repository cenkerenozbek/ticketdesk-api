# Kabul senaryoları

Kaynak proje planındaki davranışların mevcut doğrulama karşılıkları.
Bu tablo öğrenme ve işveren değerlendirmesinin yerine geçmez; çalışan uygulamanın
hangi davranışlarının sınandığını gösterir.

| Senaryo | Doğrulama |
| --- | --- |
| Talep doğru kullanıcıya ait oluşturulur | `TicketPostgresIT.createPersistsWithAuthenticatedOwner` |
| Boş başlık 400 döner | `TicketControllerTest.blankTitleIs400`, Compose smoke |
| USER listesi başka kullanıcıların kayıtlarını içermez | `TicketPostgresIT.listCombinesOwnershipFilterPaginationAndOrdering` |
| Başka kullanıcının detay/yorum/geçmişi okunamaz | PostgreSQL sahiplik testleri ve Compose smoke 404 kontrolleri |
| USER durum değiştiremez | `TicketControllerTest.userCannotPatchStatus`, Compose smoke 403 kontrolü |
| AGENT geçerli geçişi yapar | `TicketPostgresIT.agentTransitionCommitsStatusAndHistory` |
| Geçersiz geçiş veriyi değiştirmez | `TicketPostgresIT.invalidTransitionLeavesBothTablesUnchanged` |
| Durum ve geçmiş birlikte commit/rollback olur | PostgreSQL commit ve `transactionFailureRollsBackStatusAndHistoryTogether` testleri |
| Olmayan talep 404 döner | `TicketPostgresIT.missingTicketAndCommentsAre404` |
| Filtre/sayfalama/sıralama çalışır | PostgreSQL liste/yorum sayfalama testleri ve HTTP girdi kontrolleri |
| Durum raporu bilinen veride doğrudur | `TicketPostgresIT.groupByReportCountsEveryOwnersTicketsAndIncludesZeroStatuses`, Compose smoke |
| Veri yeniden başlatmada korunur | Compose smoke `seed → down → up → check`; hem API hem DB konteynerleri yeni kimlikle oluşturuldu |
| Eşzamanlı aynı geçiş tek kez uygulanır | `TicketPostgresIT.concurrentStatusRequestsProduceOneTransitionAndOneConflict`: gerçek kilit bekleyen iki istek, 200/409 ve tek geçmiş kaydı |
| Oluşturma ve sonraki okumalarda zaman değişmez | Talep/yorum PostgreSQL regresyon kontrolleri ve Linux Docker smoke |

Test komutları README'dedir. Güncel ortam, sonuçlar ve sınırlar `VALIDATION.md`
dosyasında kayıtlıdır. CI tanımlıdır; GitHub üzerinde çalıştırılması henüz doğrulanmadı.
