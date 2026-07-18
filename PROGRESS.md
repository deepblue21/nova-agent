# NOVA — Durum & Sonraki Adımlar

> Bu dosya "nereden devam edeceğiz"i tutar. Her oturum sonunda güncellenir.
> **Son güncelleme:** 17 Haziran 2026.

## Oturum Hafızası / Handoff

Bu dosya yeni oturuma başlarken ilk okunacak hafıza dosyasıdır. Her çalışma sonunda
bu bölüm veya "SIRADAKİ ADIM" bölümü güncel bırakılmalı.

### Oturum — 17 Temmuz 2026 (Cihaz-üstü LLM: Faz 1-2-3 tamamlandı)

Hedef: telefonda çevrimdışı agentic yapay zeka + PC LLM'in telefondan kullanımı + hibrit.
Kullanıcının verdiği sırayla üç faz kod olarak bitirildi ve derleme yeşil
(`testDebugUnitTest` + `lintDebug` + `assembleDebug` = BUILD SUCCESSFUL). Dal
`codex/phase1-local-first`; bu oturumda `master`'a fast-forward ile birleştirildi.

- **Faz 1 — Yerel öncelikli:** `OnDeviceEngine` (LiteRT-LM 0.13.1, CPU, taze-konuşma iptali
  + gerçek `cancelProcess()`), sabit sürüm + SHA-256 doğrulamalı/sürdürülebilir indirme merkezi,
  `ExecutionPolicy`/`EngineRouter` (varsayılan GATEWAY_ONLY), Kontrol/İşler/Sohbet/Modeller
  bilgi mimarisi (+ Ses üst çubuktan), kod bloğu kopyalama, 3 tema, Qwen3 `<think>` ayrımı.
- **Faz 2 — Çevrimdışı + agentic çekirdek:** LOCAL_ONLY (devir tamamen kapalı), çevrimdışı
  `HorusToolSet` (saat, güvenli hesap makinesi, cihaz durumu, not defteri), kapılı Gemma 3 1B
  ve FunctionGemma 270M (HF token'ı Ayarlar'da, yalnız huggingface.co'ya gider), depolama bilgisi.
- **Faz 3 — Hibrit:** uzunluk + pil + ısı (THERMAL_STATUS_SEVERE+) + gizlilik (`PrivacyClassifier`:
  şifre/TCKN/IBAN/kart → telefonda kalır) kurallı yönlendirme; "PC ajanına devret" çipi
  (`openclaw/default`, Gateway ajan geçmişine kaydolur); sor/otomatik devir anahtarı.
- Güvence: mevcut Gateway sohbeti/görevleri/SSE regresyona uğramadı; onaysız istem cihaz dışına çıkmıyor.
- Teknik not: kütüphane Kotlin 2.3 metadata'sıyla derlendiği için proje Kotlin **2.2.21**'e yükseltildi.

**Faz 4 — Model otomasyonu (17 Temmuz 2026, aynı oturum):** `ModelRecommender` (RAM'e göre
uygunluk + kapısız-öncelikli öneri), `ModelMetricsStore` (cihazda yükleme süresi + ~tok/sn),
Modeller ekranında öneri banner'ı + uygunluk çipi + performans satırı. FunctionGemma 270M ve
Gemma 3 1B kapılı; kapısız Qwen3 ilk kurulum için varsayılan. Tümü commit'li, master'a taşındı.

**Faz 5 — Kalıcı sohbet geçmişi (17 Temmuz 2026, aynı oturum):** `ConversationStore` (cihazda JSON,
çoklu sohbet), otomatik kayıt + aç/sil/ara, Sohbet'te "Geçmiş" çipi ve `ChatHistoryPanel`. Tümü
commit'li (`8108069`), master'a taşındı. Push: `git push nova-upstream master:main`.

**SIRADAKİ ADIM:** Fiziksel ARM64 cihazda uçtan uca doğrulama (kullanıcı tarafında): model indir →
uçak modunda yerel sohbet + araç turu → Çevrimdışı devirsizlik → Hibrit rota rozetleri (uzunluk/pil/
ısı/gizlilik) → "PC ajanına devret" → sohbet geçmişi kaydet/aç/ara → Gateway regresyonu. Gelecek:
düşük RAM'de otomatik quant tercihi, çevrimdışı ses, görev devri derinleştirme.

### Oturum — 16 Haziran 2026 (README/kod inceleme + Faz 8 durum kontrolü)
- Kullanıcı "Nerede kaldık, README oku, tüm kodu incele ve öneri sun" dedi. README/README.tr/SECURITY/compose/gateway ana akışı/agent-tools/MCP/RBAC/knowledge/memory/scheduled/agent-runs/script yüzeyleri tarandı.
- **Durum:** README phase board'a göre Faz 8 hâlâ `Production hardening + deploy + agent deepening` aşamasında. Kod tarafında prod-check, loopback paneller, `docker-compose.prod.yml`, MCP introspection, `fetch_url`, agent run history ve workspace-scoped kaynaklar mevcut. Sıradaki ana iş: live-deploy hazırlığı + canlı smoke.
- **Doğrulama:** `npm.cmd --prefix gateway test` -> 89/89; `npm.cmd --prefix web test` -> 3/3; `node scripts/secret-scan.mjs` temiz; `npm.cmd run build` geçti. İlk build, eksik Vite/Rolldown optional native paketinden (`@rolldown/binding-win32-x64-msvc`) düştü; `npm.cmd --prefix web install` sonrasında düzeldi. `npm.cmd run security` sandbox'ta audit/log erişimi yüzünden düştü, sandbox dışı tekrar koşuda tüm kapılar geçti: syntax, gateway tests, secret scan, gateway audit 0 vuln, web audit 0 vuln, web build.
- **Beklenen prod-check notu:** mevcut shell'de `npm.cmd run prod-check` 4 hard fail verdi (`GATEWAY_TOKEN`, token-strength, `NODE_ENV=production`, `ALLOW_ORIGINS`) çünkü prod env yüklenmemişti. Bu kod hatası değil; gerçek prod/local-public `.env` source edildikten sonra koşulmalı.
- **Kapatıldı:** `gateway/lib/agent.mjs` artık agent/team modunda `think` ve effort parametrelerini Ollama `/api/chat` body'sine geçiriyor (`options.temperature`, `options.top_p`, `options.num_predict`). Regresyon testi eklendi.
- **Kapatıldı:** `weather_forecast` aracı için fallback davranışı mock Open-Meteo ile test edildi; geocode boş sonuç, forecast 5xx ve eksik numeric alanlarda `NaN` üretmeme edge testleri eklendi.
- **Kapatıldı:** scheduled task runner `gateway/lib/scheduled_runner.mjs` modülüne çıkarıldı. `agent:false` artık direkt provider chat yolunu, `agent:true`/varsayılan ise `runAgent` yolunu kullanıyor; unit testlerle korundu.
- **Kapatıldı:** Caddy doküman/gerçek davranış uyumsuzluğu düzeltildi. `Caddyfile` artık `/health`, `/v1/*`, `/stt`, `/tts` isteklerini gateway/voice servislerine yönlendiriyor; DEPLOY dokümanları buna göre güncellendi.
- **Kapatıldı:** public Keycloak host rotası compose/Caddy/prod-check tarafında tamamlandı. `KC_HOSTNAME_HOST` eklendi, prod overlay'de zorunlu hale getirildi ve `prod-check` artık `KC_HOSTNAME` varsa Caddy host değerini de hard check olarak doğruluyor.
- **Kapatıldı:** public CSP origin daraltması koda alındı. `CSP_CONNECT_SRC` Caddy/compose/prod overlay'e eklendi; prod overlay varsayılanı `'self'`. `prod-check` artık production'da `CSP_CONNECT_SRC` yoksa veya wildcard/local dev origin içeriyorsa hard fail veriyor.
- **Doğrulama güncellemesi:** son koşuda `npm.cmd --prefix gateway test` 99/99, `npm.cmd --prefix web test` 3/3 geçti. Güçlü örnek prod env ile `npm.cmd run prod-check` tüm required check'leri geçti (`CSP_CONNECT_SRC` dahil). `docker compose -f docker-compose.yml -f docker-compose.faz2.yml -f docker-compose.prod.yml config -q` geçti. Sandbox dışı `npm.cmd run security` tamamen geçti: syntax, gateway tests, secret scan, gateway audit 0 vuln, web audit 0 vuln, web build.
- **Canlı smoke durumu:** Faz 8'in tek kalan gerçek kapısı canlı WSL/Ollama/Docker smoke. Bu oturumda çalıştırılamadı: Windows Docker daemon `dockerDesktopLinuxEngine` bulunamadı, Windows tarafında `ollama` komutu yok, `wsl --list --verbose` kayıtlı distro göstermiyor ve `wsl -d Ubuntu` `WSL_E_DISTRO_NOT_FOUND` döndü.
- **Faz kararı:** Faz 8 kod/config/dokümantasyon tarafı tamamlandı; Faz 8 **tam bitmiş sayılmaz** çünkü runtime smoke dış ortam eksikleri nedeniyle bekliyor. Sonraki faz adayı **Faz 9 — public release handoff + first-run reliability**, ancak canlı smoke geçmeden Faz 9'a başlanmamalı.
- **Dokümantasyon temizliği:** `README.md`, `README.tr.md` ve `nova-android/README.md` içindeki komut bloğu yorumları kaldırıldı; gerekli açıklamalar normal metin veya tablo olarak korundu. Kontrol: kod bloklarında `#` yorum satırı kalmadı, sadece Markdown başlıkları kaldı.
- **Profesyonel README eşitlemesi:** `README.md` ve `README.tr.md` aynı bölüm yapısına getirildi (22 başlık), hızlı kurulum/faz panosu/güvenlik bölümleri aynı kararları içeriyor; kök README'lerde emoji tabanlı durum göstergeleri kaldırıldı.
- **Güvenlik kapanışı:** `keycloak/nova-realm.json` artık varsayılan kullanıcı/parola import etmiyor ve public client için `directAccessGrantsEnabled=false`. `scripts/security-check.mjs` buna statik kontrol ekledi; ileride realm'e default kullanıcı veya password grant eklenirse security gate kırılacak. Eski demo credential referansları dokümanlardan ve `secret-scan` allowlist'inden kaldırıldı.
- **Son doğrulama:** `node scripts/secret-scan.mjs --all` temiz; `npm.cmd --prefix gateway test` 99/99 geçti; `npm.cmd --prefix web test` 3/3 geçti; güçlü örnek prod env ile `npm.cmd run prod-check` geçti; prod compose config geçti. Sandbox içi `npm.cmd run security` syntax, gateway tests, secret scan, yeni static security config ve web build adımlarını geçirdi; npm audit adımları registry/cache erişimi nedeniyle düştü. Sandbox dışı tekrar koşu kullanım limiti nedeniyle reddedildi, bu yüzden audit'li tam security gate son kez dış ortamda tekrar koşulmalı.
- **Commit/push durumu:** `git update-index --chmod=+x nova-android/gradlew` ile Android wrapper executable bit'i korunmak istendi, ancak `.git` yazımı sandbox'ta izin hatasına düştü. Sandbox dışı izin denemesi Codex kullanım limiti nedeniyle reddedildi; bu yüzden commit/push bekliyor. Tekrar açıldığında önce `git add -A`, `git add --chmod=+x nova-android/gradlew`, commit ve push yapılmalı.

### Oturum — 14 Haziran 2026 (Faz 6 — çoklu ajan / team mode)
- **Saf çekirdek:** `gateway/lib/multiagent.mjs` — `mapLimit` (eşzamanlılık sınırı), `buildSynthesisPrompt`, `runTeam` (fan-out → paralel alt-ajan → sentez; `runOne`/`synthesize` enjekte → test edilebilir), `parsePlan` (planlayıcı JSON çıktısından alt-görevler). `gateway/test/multiagent.test.mjs` (core 4/4 sandbox; parsePlan inline 5 assert).
- **Gateway:** `team:true` chat dalı (agent dalından önce, yalnız ollama+resimsiz): `planSubtasks` (yerel modelden JSON plan) → yoksa tek ajana düş → `runTeam` (her alt-görev `runAgent` ile, tools açık) → sentez `runAgent` → stream (alt-görev event'leri) + kaynak rozetleri + `nova_team`. `TEAM_CONCURRENCY` (vars. 3).
- **UI:** dock'ta **Takım** toggle (Ajan yanında; `teamMode` state, kalıcı), gateway isteklerine `team:true` ekler (chat + voice).
- **Doğrulama:** multiagent core 4/4 + parsePlan inline OK. gateway.mjs/multiagent.mjs/nova-agent.jsx mount'ta bayat-truncated → sandbox build/parse edemedi; **diskte doğru** (nova-agent.jsx 2443 satır tam; düzenlemeler 2430'dan önce, kuyruk pre-existing). Kullanıcı `npm test` + `npm run build` + gateway rebuild ile doğrular.
- **KALAN (kullanıcı):** tam build/test/commit + canlı team denemesi (Gateway + yerel model, dock'ta **Takım** aç, çok-parçalı görev sor → planla→paralel→sentez).

### Oturum — 14 Haziran 2026 (Faz 5 phase kapanışı)
- Kullanıcı "Faz 5'i phase olarak bitir" dedi; kalan kod kalemleri kapatıldı:
  - **Grafana auto-provisioning:** `monitoring/grafana/provisioning/{datasources,dashboards}` + compose grafana mount → `prom-nova` datasource + NOVA dashboard otomatik yüklenir (manuel reimport yok).
  - **Opt-in hata raporlama:** `gateway/lib/errors.mjs` (`ERROR_WEBHOOK_URL`; fire-and-forget JSON; Sentry-relay/Slack/collector) global error handler'a bağlandı; saf `buildErrorEvent` + test (sandbox 1/1).
  - **K8s configmap** ajan/RAG/voice/scheduler/OTEL/routing/TTS_FORMAT/ALLOW_MODELS knob'larıyla güncellendi.
  - **prod-check:** `scripts/prod-check.mjs` + `npm run prod-check` — prod env doğrulaması (auth/NODE_ENV/CORS sert; ALLOW_MODELS/TRUST_PROXY/health/rate uyarı). Sandbox'ta fail+pass yolları doğrulandı.
- Geriye kalan Faz 5 = saf deploy-zamanı kararları (Keycloak prod mode, port kapatma, secret rotation) + opsiyonel (searxng/whisper/tts için ayrı K8s manifest, daha geniş provider integration testleri). README phase board/roadmap + işaretçi ✅ güncellendi.
- **Sırada:** Faz 6 devam (çoklu ajan / MCP entegrasyonu / Android).

### Oturum — 14 Haziran 2026 (Faz 6 — zamanlanmış/otomatik ajan görevleri)
- Kullanıcı Faz 6'da "zamanlanmış/otomatik ajan görevleri"ni seçti. Uçtan uca eklendi.
- **Saf çekirdek:** `gateway/lib/scheduler.mjs` — `parseSchedule` (`every:Nm/h/d`, `daily:HH:MM`; min 60sn), `nextRunAt`, `dueTasks`, `describeSchedule`. `gateway/test/scheduler.test.mjs` 4/4 (sandbox).
- **Backend:** migration `004_scheduled_tasks.sql` (user-scoped tablo, ms epoch çıktısı); `lib/scheduled_store.mjs` (list/create/update/delete + `listDue` + `markRun`); `routes/scheduled.mjs` (`/v1/scheduled` GET/POST/PATCH/DELETE, userId-scoped, schedule+UUID doğrulama). `gateway.mjs`: multi-user mount + **opt-in in-process runner** (`SCHEDULER_ENABLED=1`): due görevleri `runAgent` ile çalıştırır (araçlar açık), sonucu saklar; `SCHEDULER_TICK_MS` (vars. 30sn), `timer.unref()`.
- **UI:** Ayarlar'da "Zamanlanmış Görevler" paneli — başlık + görev metni + zamanlama (`<select>`: 30dk/saat/6saat/gün, günlük 09:00/18:00) ile oluştur; liste: başlık/zamanlama/son durum + duraklat (PATCH enabled) + sil. `kbBase()` + Bearer auth deseni.
- **Compose/env:** `SCHEDULER_ENABLED=1` + `SCHEDULER_TICK_MS=30000` (compose), `.env.example` belgelendi.
- **Doğrulama:** scheduler 4/4; yeni gateway `.mjs`'ler `node --check`; **web build sandbox'ta geçti** (tüm UI derlendi). Tam gateway suite + canlı koşum kullanıcı makinesinde (migration 004 otomatik uygulanır + `SCHEDULER_ENABLED`). Scheduler yalnız multi-user + DATABASE_URL ile koşar (Docker stack'te mevcut).
- **KALAN (kullanıcı):** gateway rebuild (migration 004 + runner) + web build + commit. Ayarlar → Zamanlanmış Görevler ile bir görev ekle, `docker compose logs gateway`'de "scheduled task ran" gör.

### Oturum — 14 Haziran 2026 (UI tasarım + ses + effort işlevselliği)
- Kullanıcı: arayüzü "göz alıcı" yap, sesli sohbeti iyileştir, web önizleme penceresi ekle, effort/agent gerçekten çalışsın, **her yeni koda test**, security'i bırakma.
- **Ses/UI:** Sesli modda **Durdur/barge-in** (`stopSpeaking`; konuşurken mikrofon STOP + mercan pulse halka; Web Audio + tarayıcı TTS kesilir). **Orb durum rengi** (`useOrb`: boşta cyan → dinleme azure → düşünme mor → konuşma mercan, yumuşak geçiş).
- **Web sitesi canlı önizleme:** tam HTML yanıtında `artifact` paneli **otomatik açılır** (`extractWebsite`), tarayıcı chrome'u (trafik ışığı + adres çubuğu), iframe `allow-scripts` ama `allow-same-origin` YOK (izole). `extractWebsite` → `web/src/lib/site.mjs` (saf/test edilebilir).
- **Tasarım:** hero giriş animasyonu + animasyonlu gradient başlık + yüzen marka, daha canlı aurora, mask-border ışıltılı öneri kartları, buton press feedback, AI avatar hover sheen, gradient AI baloncuk, kod bloğu hover, smooth scroll. Hepsi **saf CSS** (CSP'ye dokunulmadı, yeni bağımlılık yok).
- **Effort bar işlevsel yapıldı:** (1) **effort→params** (`pickParams`+`EFFORT_TIERS`): Maks=4096tok/0.3 … Hızlı=512/0.7 (açık değer verilmezse) → fixed local modelde bile Maks daha uzun/kararlı. (2) **effort→model** (`ROUTE_*` compose'da; mevcut `pickDynamicModel`): auto'da Maks→`qwen3.6:35b`, Derin→`qwen3.5:35b`, Dengeli→`gemma4:latest`, Hızlı→`gemma4:e2b` (yalnız auto/Dinamik Yönlendirme).
- **TTS netliği:** gateway `response_format` mp3→**wav** (yapılandırılabilir `TTS_FORMAT`). "Kelime yutma" = Türkçe-olmayan voice; açık karar (openedai-speech Türkçe voice config ya da tarayıcı tr-TR).
- **Testler:** gateway → effort-tier + ROUTE_* override + ttsFormat testleri; **web'e `node --test` harness'ı + `web/test/site.test.mjs`** (extractWebsite 3/3 sandbox'ta geçti). Tam suite kullanıcı makinesinde.
- **KALAN (kullanıcı, Windows/WSL):** `npm --prefix gateway test` + `npm --prefix web test` + `npm --prefix web run build` + `docker compose -f docker-compose.yml -f docker-compose.faz2.yml up -d --build gateway` + `git add -A && commit && push`. Türkçe TTS voice kararı.

### Oturum — 13 Haziran 2026 (Faz 5B — ajan/araç metrikleri)
- Abort fix + Faz 4C smoke `b05cb44` olarak commit+push edildi ve Docker gateway fix'li imajla yeniden kuruldu (kullanıcı doğruladı: `npm test` 40/40, push OK, container'lar healthy).
- Kullanıcı Faz 5B'de "sadece ajan metrikleri" dedi (zero-dep). `gateway/lib/metrics.mjs`'e 3 metrik eklendi: `nova_agent_runs_total`, `nova_agent_tool_calls_total{tool,status}`, `nova_agent_tool_duration_seconds{tool}` (mevcut prom-client; yeni bağımlılık yok).
- `gateway/lib/agent.mjs` `runAgent`: başta `agentRuns.inc()`, her `runTool` çağrısı `process.hrtime` ile sürelenip `agentToolDuration.observe` + `agentToolCalls.inc({tool,status})` ile sayılıyor (ok/error). `/metrics`'ten yayılıyor.
- `gateway/test/agent.test.mjs`: mock agent → `registry.metrics()` çıktısında `nova_agent_runs_total` + `nova_agent_tool_calls_total{tool="calculator",status="ok"}` + duration `_count` doğrulandı (test eklendi). Birleşik suite ~41 test (kullanıcı makinesinde).
- `monitoring/grafana-nova-gw.json`: 3 panel eklendi (id 5 araç çağrı hızı tool/status, id 6 araç p95 süresi, id 7 ajan çalıştırma+araç toplamı). JSON parse geçerliliği doğrulandı (7 panel).
- Doğrulama notu: sandbox mount taze-düzenlenen mevcut dosyalarda (agent.mjs/metrics.mjs/grafana json) bayat/kesik gösterdi; içerik Read ile (Windows truth) teyit edildi. Test/JSON kullanıcı makinesinde/CI'da koşar.
- **Trace eklendi (aynı oturum):** `gateway/lib/tracing.mjs` — zero-dep, opt-in OTLP/HTTP trace exporter (`OTEL_EXPORTER_OTLP_ENDPOINT`/`_TRACES_ENDPOINT`/`OTEL_SERVICE_NAME`). `gateway.mjs` chat handler'ında chat başına span (route/provider/model/agent/stream/status), `res.on('finish')`'te kapanır, tracing kapalıyken no-op. Testler: `buildOtlpSpan` yapısı/attribute tipleri/config gating + enabled-export/disabled-no-op (units.test.mjs, mock fetch ile sandbox'ta 2/2 doğrulandı). `.env.example` + README (config tablosu + observability) güncellendi.
- KALAN (kullanıcı): `npm test` (~43) → `git add -A && commit && push`; Grafana dashboard'unu reimport et. **Faz 5 kod tarafı tamam** (5A + 5B metrik + trace); kalan saf deploy/ops kararları (Keycloak prod mode, iç port kapatma, secret rotation, `ALLOW_MODELS` — SECURITY.md). Sıradaki kod fazı: **Faz 6 — Android**.

### Oturum — 13 Haziran 2026 (Faz 4C canlı smoke TAMAM + chat abort bug fix)
- Kullanıcı "Faz 4'te her şey bitsin, öyle 5'e geç" dedi. Faz 4C canlı smoke'ları (vision + voice queue) kullanıcının WSL'inde, **key'siz local** akışla yapıldı (Docker multi-user gateway yerine auth'u kapalı bare-metal gateway, port 8090).
- **Vision ✅:** `ollama show` ile vision-yetenekli modeller tespit edildi (qwen3.6:latest, qwen3.5:35b, gemma4:latest/26b, nemotron3:33b). Bare-metal gateway `VISION_MODEL=ollama/gemma4:latest` ile: görsel istek `x-nova-route=ollama/gemma4:latest`'e gitti ve model test görselini doğru tarif etti ("kırmızı ve mavi renkler"). Routing + multimodal uçtan uca doğrulandı. (Not: kod varsayılanı `qwen3.5-omni:latest` kullanıcının Ollama'sında yok; kurulu bir vision tag'ine ayarlanmalı.)
- **Voice queue ✅:** `tts` (faz2, host 8001) + yerel redis (host 6379) ayağa kaldırıldı; bare-metal gateway `VOICE_QUEUE_ENABLED=1 REDIS_URL=redis://localhost:6379 TTS_URL=...:8001` ile başlatıldı. `/health` `voice_queue:true` döndü; SMOKE_VOICE → async TTS job kuyruğa girdi, worker işledi, **11.5 KB ses üretildi** (`tts job 1 completed`). BullMQ/Redis/TTS async pipeline doğrulandı.
- **🐞 BUG bulundu + düzeltildi (chat'i kıran):** `gateway.mjs` upstream'i `req.on("close")` ile iptal ediyordu — bu, POST body okunur okunmaz (yanıttan önce) tetiklendiği için HER chat/vision çağrısını ~1ms'de abort ediyordu ("ollama This operation was aborted"). `res.on("close")`'a çevrildi. Minimal http repro + canlı smoke ile doğrulandı; `units.test.mjs`'e statik regresyon testi eklendi (`req.on('close')` geri gelirse CI kırılır). Not: Docker imajı 42 saatlik (eski, bug'sız) kod olduğu için canlı Docker chat çalışıyordu; bug current/commit'li kodda.
- **smoke-live.mjs genişletildi:** `SMOKE_VISION` (test görseli data URL), `SMOKE_VOICE` (async TTS job), ve Caddy proxy'si `/health`'i yayınlamadığı için health kontrolü proxy-toleranslı yapıldı (reachability = `/v1/models`).
- **Doğrulama:** local smoke `chat ✓ / agent ✓ (108) / vision ✓ / voice-queue ✓`, 0 fail. (gateway.mjs `node --check` geçti; units.test.mjs sandbox mount'ta bayat görünüyor ama diskte doğru — birleşik suite kullanıcı makinesinde ~40 test.)
- **⚠️ KALAN (kullanıcı, commit'lenmeli):** abort fix + smoke-live güncellemeleri + regresyon testi + bu doc'lar **henüz commit'li değil** (temiz commit `35d7d37` bug'ı içeriyor). Kullanıcı: `git add -A && git commit && git push --force origin main` + **Docker gateway imajını yeniden build et** (`docker compose ... up -d --build gateway`) ki canlı Docker gateway de fix'i alsın.
- Faz 4 tamamlandı (4A/4B/4C/4D). Sıradaki: Faz 5B (gözlemlenebilirlik) veya prod sertleştirme.

### Oturum — 13 Haziran 2026 (Faz 4D — git history temizliği hazırlığı)
- Faz 5A sonrası kullanıcı Faz 4D'yi (git history) seçti. Git **write** işlemleri bu sandbox'tan güvenilmez (`.git/index.lock` EPERM); bu yüzden gerçek rewrite kullanıcının makinesinde yapılacak, burada inceleme + doğrulanmış runbook üretildi.
- **İnceleme:** tek commit `da8bece`, author = kişisel ad + gmail adresi (kişisel e-posta commit metadata'sında), remote `github.com/deepblue21/nova-agent`, main + origin/main. `da8bece` içeriğinde kişisel e-posta, OS kullanıcı adı, eski demo parola, kişisel mutlak home path'leri ve 3 adet `web/vite.config.js.timestamp-*.mjs` var.
- **Çalışma ağacı (tracked) ek temizlik:** `web/src/nova-agent.jsx` varsayılan sistem promptundaki kişisel isim referansı kaldırıldı → "Sen NOVA adlı kişisel yapay zeka asistanısın" (generic). Tarama sonrası tree temiz: kişisel e-posta/kullanıcı adı/eski demo parola yok; timestamp dosyaları diskte zaten silinmiş (unstaged `D`) → clean-start onları zaten almaz; `WSL_DOCKER.md` `<WindowsUser>` placeholder kullanıyor.
- **Eklendi:** `HISTORY_CLEANUP.md` (runbook), `scripts/clean-history.sh` (WSL/bash) ve `scripts/clean-history.ps1` (PowerShell). Hepsi: backup bundle + `CONFIRM` onayı + secret-scan kapısı + **orphan clean-start** (tek köksüz commit, generic author) + push ÖNCESİ durup push seçeneklerini yazar.
- **Doğrulama (throwaway repo'larda):** orphan dizisi → 1 commit, generic author, eski commit history'den + (reflog expire + gc --prune sonrası) obje deposundan gitti. `clean-history.sh` uçtan uca koştu: 1 commit, generic author, eski commit yok, backup bundle oluştu. `bash -n` geçti.
- **Sandbox'tan rewrite denendi → YAPILAMIYOR (kanıtlandı):** orphan clean-start sandbox'ta denendi; `git checkout --orphan` sonrası `git add -A` mount yüzünden `fatal: index file corrupt` verdi. Hiç yıkıcı adım tamamlanmadı (main hâlâ `da8bece`, working tree sağlam). Kurtarma: bozuk `.git/index` silindi, `HEAD` symbolic-ref ile `main`'e döndürüldü, `git reset --mixed` ile index yeniden kuruldu — working tree korundu (85 değişiklik), `git tag` write testi tekrar geçti. **Sonuç: git history rewrite bu sandbox'tan yapılamaz; kullanıcının gerçek makinesinde çalışmalı.**
- **Backuplar hazır:** `nova-backup-pre-cleanup.bundle` (repo içinde, gitignore'lu — eski `da8bece` geçmişini içerir) + bu oturumun working-tree snapshot'ı `/tmp`'de. `.gitignore`'a `*.bundle` ve `*.tgz` eklendi.
- **✅ YAPILDI (13 Haz, kullanıcı WSL):** `clean-history.sh` çalıştırıldı → tek köksüz temiz commit `35d7d37` ("NOVA Agent — initial public release", author `NOVA Agent AI contributors <noreply@users.noreply.github.com>`, 102 dosya, secret-scan staged tree 97 dosya temiz). `git push --force origin main` → `da8bece..35d7d37 (forced update)`. Remote main artık temiz; kişisel e-posta git geçmişinden kalktı. Repo private.
- **Opsiyonel (public öncesi %100 garanti):** force-push sonrası `da8bece` GitHub'da GC'ye kadar full-SHA ile erişilebilir kalabilir. Sıfır iz istiyorsan repo'yu silip aynı adla boş oluştur + `git push -u origin main`. Yedekler: `nova-backup-pre-cleanup.bundle` (repo içi, gitignore'lu) + `../nova-backup-*.bundle`; iş bitince silinebilir.

### Oturum — 13 Haziran 2026 (Faz 5A — CI & güvenlik otomasyonu)
- Kullanıcı "README güncel, her şeyi oku, fazları incele ve kaldığımız yerden devam et" dedi; AskUserQuestion ile yön seçtirildi → **Faz 5A (CI & güvenlik otomasyonu)** seçildi (canlı 4C smoke ve 4D git history kullanıcının makinesini gerektiriyor; 5A saf kod).
- **CI sertleştirildi** (`.github/workflows/ci.yml`): Node sürüm matrisi `["20.19","22"]` (Vite 8 minimumu + güncel LTS); `npm ci` ile lockfile'dan temiz kurulum (`working-directory` formu); yeni adımlar **secret scan**, **gateway+web `npm audit --audit-level=moderate`** ve **canlı smoke** (`scripts/smoke-live.mjs`); mevcut production preflight-fail ve docker imaj build job'u korundu.
- **`scripts/secret-scan.mjs` eklendi** (sıfır bağımlılık): `git ls-files` ile izlenen dosyaları tarar; yüksek güvenli desenler (Anthropic/OpenAI/AWS/GCP/Stripe/Slack/GitHub/GitLab anahtarları, private key) + entropi tabanlı `secret = <değer>` sezgisi; dev placeholder allowlist'i. Self-test: planted AWS/Anthropic/generic yakalandı, temiz ağaçta 0 bulgu (67 dosya).
- **`scripts/smoke-live.mjs` eklendi**: çalışan gateway'e karşı uçtan uca smoke. Zorunlu kontroller `/health`, auth zorlaması (token'sız 401), `/v1/models`; opsiyonel `chat`/`agent`(SMOKE_AGENT=1)/`rag`(SMOKE_RAG=1). Bare-metal gateway'e karşı doğrulandı: health/auth/models ✓, chat Ollama yokken `warn` (graceful), exit 0.
- **`scripts/security-check.mjs` eklendi** (tek komut güvenlik kapısı): syntax (`node --check`) + gateway testleri + secret-scan + gateway/web audit + web build; `SKIP_BUILD=1`/`SKIP_AUDIT=1` bayrakları; cross-platform (`process.execPath` + `shell:true` ile Windows'ta `npm.cmd`).
- **Root `package.json` script'leri**: `test:gateway`, `secret-scan`, `audit`, `security`, `smoke:live`.
- **Ajan/RAG CI-smoke (mock'lu)**: `gateway/test/agent.test.mjs`'e 3 `runAgent` testi eklendi — URL'e göre yönlendiren `fetch` mock'u (Ollama `/api/chat` + SearXNG `/search`): araç çağrısı sonucu modele geri besleniyor, araçsız yol doğrudan dönüyor, `web_search` kaynakları sonuca taşınıyor. Canlı altyapı gerekmez → CI'da koşar.
- **Doğrulama:** `units.test.mjs` 21/21 (sandbox, taze); yeni `runAgent` testleri 3/3 (taze geçici koşu); secret-scan 0 bulgu; smoke-live canlı OK; `ci.yml` YAML geçerli (matris + secret scan + audit + smoke + preflight + docker); tüm yeni script'ler `node --check` geçti.
- **Sandbox kısıtı (kod hatası DEĞİL):** Windows tarafı Edit/Write doğru yazıyor ama Linux mount, taze düzenlenen MEVCUT dosyalar için eski/baytı kesik içerik gösteriyor (`agent.test.mjs`, `package.json` mount'ta bayat görünür; Windows'ta doğru). Bu yüzden birleşik `npm test` (39), `npm run security` ve `npm run build` (web esbuild Windows binary'si) **Windows'ta** çalıştırılmalı.
- **Kalan:** Faz 5B prod sertleştirme kararı (Keycloak prod, port kapatma, secret rotation, Sentry/OTel); Faz 4C canlı vision/voice smoke (kullanıcı WSL/Docker/Ollama); Faz 4D git history (tek commit `da8bece` hâlâ kişisel iz içeriyor → public öncesi orphan/squash); Faz 6 Android gradle wrapper.

### Oturum — 13 Haziran 2026 (derin güvenlik sertleştirme)
- Kullanıcı "security açısından açık kalmamalı" dedi; repo güvenlik denetimi gateway, frontend, dependency, Docker/config ve public hygiene yüzeylerinde yapıldı.
- Dependency açığı kapatıldı: `web` tarafındaki Vite/esbuild audit bulgusu `vite@8.0.16` ve `@vitejs/plugin-react@6.0.2` yükseltmesiyle kapandı. Yeni web/root Node minimumu `>=20.19.0`.
- Gateway sertleştirildi: `/health` production'da minimal hale geldi; `HEALTH_DETAILS_ENABLED=1` production preflight'ta yasaklandı; `OIDC_JWKS_URL` set iken `OIDC_ISSUER` boşsa production başlangıcı reddedilir; JWT `sub` kontrolü ve email/name uzunluk sınırı eklendi.
- Input güvenliği artırıldı: `MAX_MESSAGE_CHARS`, JSON parse/body-limit hata maskeleme, remote image data URL base64+boyut kontrolü, stream okurken byte-limit, `/v1/media` için MIME allowlist + base64/size doğrulama (`gateway/lib/media_input.mjs`) eklendi.
- Route güvenliği artırıldı: admin endpoint'lerinde UUID/prefix/scope/quota doğrulama ve async error handling; history endpoint'lerinde UUID/title sınırı; global JSON error handler eklendi.
- Frontend güvenliği artırıldı: CSP `connect-src *` kaldırılıp local/provider allowlist'e çekildi; markdown linkleri `http/https/mailto` dışındaki scheme'lerde tıklanabilir olmaktan çıkarıldı.
- Test kapsamı güncellendi: media input ve yeni image edge testleri eklendi; `npm.cmd --prefix gateway test` 36/36 geçti. `npm.cmd --prefix gateway audit` ve `npm.cmd --prefix web audit` 0 vulnerability. `npm.cmd run build` Vite 8 ile geçti (büyük chunk uyarısı beklenen).
- README handoff güncellendi: "Son çalışma özeti — 13 Haziran 2026" ve "Sonraki aşama — adım adım" bölümleri eklendi; sıradaki işler public history temizliği, full-stack smoke, çok modlu/voice smoke, CI güvenlik hattı ve Android wrapper/test sırasına bağlandı.
- README fazlandırması güncellendi: "Faz panosu — nerede kaldık?" tablosu eklendi; yapılanlar ve yapılacaklar Faz -1/0/1/2/3/4A/4B/4C/4D/5/6 şeklinde durum, çıktı ve kalan kriterlerle okunabilir hale getirildi. "Sıradaki faz planı" da Faz 4D, Faz 4C, Faz 5A, Faz 5B ve Faz 6 olarak adım adım gruplandı.
- Kalan kritik public adım değişmedi: current tree temiz olsa da eski git commit geçmişinde kişisel test verileri/path'ler görünüyor; public release için clean start/squash/history rewrite yapılmalı.

### Oturum — 13 Haziran 2026 (public repo hijyeni / sanitization)
- Kullanıcı "devam et" dedi; önceki public repo risk değerlendirmesinden devam edildi. Canlı WSL/Ollama smoke ortam görünürlüğüne bağlı kaldığı için kodlanabilir sıradaki iş olarak public paylaşım hijyeni seçildi.
- Repo tarandı: gerçek provider API key veya `.env` içeriği bulunmadı; kişisel e-posta/kullanıcı adı ve yerel path izleri temizlendi.
- `keycloak/nova-realm.json` daha önce generic bir yerel test kullanıcısı içeriyordu; public riskini azaltmak için daha sonra varsayılan kullanıcı/parola import'u kaldırıldı.
- Yanlışlıkla izlenen Vite timestamp çıktıları (`web/vite.config.js.timestamp-*.mjs`) kaldırıldı; bu dosyalar eski sandbox absolute path'leri içeriyordu. `.gitignore` içine `*.timestamp-*.mjs` eklendi.
- `searxng/settings.yml` içindeki random görünümlü sabit secret, açıkça dev placeholder olan `change-me-searxng-dev-secret` değerine çekildi.
- Android artifact düzeni toparlandı: `nova-android.zip` arşivi gerçek `nova-android/` klasörüne çıkarıldı; kökteki kopya `MainActivity.kt` ve `README (2).md` kaldırıldı; README doküman listesi Android klasörüne bağlandı; `.gitignore` arşiv/timestamp artifact'lerini engelleyecek şekilde güncellendi.
- Doğrulama: current tree taramasında kişisel ad/e-posta/yol kalıpları, eski demo parola ve eski SearXNG secret değeri bulunmadı; `keycloak/nova-realm.json` JSON parse geçti; `npm.cmd --prefix gateway test` 35/35 geçti; `npm.cmd run build` geçti (Vite büyük Mermaid/wardley chunk uyarısı beklenen).
- Kalan public karar: Current tree temiz olsa da `git grep` eski ilk commit `da8bece` içinde kişisel test e-postası, eski demo parola ve Vite timestamp path'leri gösteriyor; mevcut git geçmişiyle public açılacaksa clean start/squash/history rewrite gerekir.

### Oturum — 13 Haziran 2026 (Faz 4C kod kalitesi: provider modülerleştirme)
- Kullanıcı "Devam" dedi; canlı smoke için WSL/Docker/Ollama görünürlüğü tekrar kontrol edilmedi, önceki engel nedeniyle sıradaki kodlanabilir işten devam edildi.
- `gateway/lib/providers.mjs` eklendi: provider routing (`routeModel`), generation param seçimi (`pickParams`), OpenAI-style SSE helpers, multimodal normalize (`normMsg`/`messageText`) ve provider çağrıları (Ollama, OpenAI, Gemini, Anthropic, OpenClaw) gateway dışına taşındı.
- `gateway/gateway.mjs` sadeleşti: endpoint/auth/quota/persistence akışı ana dosyada kaldı; provider çağrısı `providerClient.chat(...)` tek giriş noktasından yapılıyor.
- `gateway/test/units.test.mjs` içine provider helper testi eklendi: route parse, param passthrough ve data URL görsel normalize doğrulanıyor.
- Negatif/edge test kapsamı güncellendi: routing fallback, bilinmeyen provider/eksik API key hataları, voice upstream/malformed payload, kapalı voice queue, güvensiz remote image URL/content/redirect, doc extraction eksik/kısa/büyük girdi ve code sandbox boş/çok uzun kod senaryoları eklendi.
- Doğrulama: `node --check gateway/gateway.mjs` geçti; `node --check gateway/lib/providers.mjs` geçti; `node --check gateway/test/units.test.mjs` geçti; `node --check gateway/test/agent.test.mjs` geçti; `npm.cmd --prefix gateway test` 35/35 geçti; `npm.cmd run build` geçti (Vite büyük Mermaid/wardley chunk uyarısı beklenen).

### Oturum — 12 Haziran 2026 (public repo hazırlığı / README netleştirme)
- Kullanıcı "repo public olursa insanlar kullanabilir mi, minimum gereksinimler ve kurulum README'de var mı" diye sordu.
- Sonuç: Evet, repo public yapılınca insanlar yerel/dev modda kullanabilir; README'de artık üst kısımda ayrı **Public Kullanım Durumu** bölümü var.
- README'ye eklendi: bare-metal minimumları (Git, Node.js 18+, npm, tarayıcı, provider key veya Ollama), Docker tam stack minimumları (Docker Compose, WSL önerisi, RAM/port notları), public production uyarıları.
- Önemli açık karar: Public dağıtım için hâlâ `LICENSE` dosyası seçilmeli/eklenmeli. Lisans olmadan kod görülebilir ama yeniden kullanım hakları net değildir.
- Production uyarısı README'de görünür hale getirildi: default parolalar/tokenlar değişmeden, `ALLOW_ORIGINS`/TLS/Keycloak/secret ayarları yapılmadan internete açılmamalı.
- Kullanıcı niyeti netleştirdi: repo public olacaksa amaç yalnızca **local kullanım/self-hosted geliştirme**; production servis olarak yayınlama hedeflenmiyor. README dili buna göre güncellendi.
- MIT lisansı eklendi (`LICENSE`); README checklist ve License bölümü "MIT" durumuna güncellendi. Copyright satırı şimdilik `NOVA Agent AI contributors`.

### Oturum — 12 Haziran 2026 (Faz 4A kapandı, Faz 4B başladı)
- Kullanıcı "mevcut faz bitti mi bak; bitmediyse bitir, bittiyse diğer faza geç" dedi.
- Durum kontrolü: Faz 4A bitmemişti; README'de artifact hata/kalite durumları ve paylaşılabilir link hâlâ sıradaydı.
- Faz 4A kapatıldı: hash tabanlı local paylaşım/import linki eklendi (`Link` düğmesi), büyük sohbetlerde JSON snapshot fallback'i var; link açıldığında paylaşılan sohbet mevcut kayıtları silmeden yeni konuşma olarak içeri alınır.
- Artifact paneli tamamlandı: boş içerik placeholder'ı, HTML fragment wrapper'ı, SVG daha iyi framing, Mermaid render hatasında görünür `hata` rozeti + kaynak fallback'i; iframe sandbox tüm türlerde scriptsiz kalır.
- Faz 4B'ye geçildi: gateway agent stream'i artık `tool_result` event'iyle `sources` taşır; `doc_search` belge kaynaklarını `{title, score, type:"doc"}` olarak döndürür; UI araç kartlarında web kaynakları linkli, belge kaynakları skor rozetli görünür.
- Doğrulama: `npm.cmd run build` geçti; `npm.cmd --prefix gateway test` 13/13 geçti. Playwright smoke: paylaşım hash'i, hash import, HTML preview, Mermaid hata fallback'i, scriptsiz sandbox, web/doc kaynak rozetleri doğrulandı.

### Oturum — 12 Haziran 2026 (Faz 4B devam: PDF/DOCX bilgi tabanı)
- `gateway` bağımlılıkları eklendi: `pdf-parse@1.1.1`, `mammoth@1.12.0` (`package.json` + lockfile). İlk `mammoth@1.8.0` denemesinde moderate audit uyarısı vardı; kontrollü exact upgrade sonrası `npm.cmd --prefix gateway audit --audit-level=moderate` → 0 vulnerability.
- `gateway/lib/doc_extract.mjs` eklendi: düz metin, PDF ve DOCX girdilerini normalize eder; `MAX_DOC_BYTES` çıkarılmış metin sınırı, yeni `MAX_DOC_FILE_BYTES` ham dosya sınırı olarak kullanılır. `pdf-parse` debug yan etkisini önlemek için doğrudan `pdf-parse/lib/pdf-parse.js` import edilir.
- `/v1/knowledge` geriye uyumlu kaldı: `{title,text}` kabul etmeye devam eder; ayrıca `{title,file:{name,mime,b64}}` ile `.pdf/.docx` ve text dosyalarını kabul eder. Hatalar 400/413/415/422 kodlarıyla döner.
- Web Ayarlar → Bilgi Tabanı: dosya seçici `.pdf,.docx` kabul eder; PDF/DOCX seçilince binary içerik gateway'e gönderilir, seçili dosya adı ve extraction notu UI'da görünür; hatalar kullanıcıya gösterilir.
- Testler eklendi: düz metin dosyası normalize, minimal gerçek DOCX'ten metin çıkarma, unsupported dosya türü reddi.
- Doğrulama: `npm.cmd --prefix gateway test` 16/16 geçti; `npm.cmd run build` geçti; Playwright UI smoke `.pdf/.docx` accept, DOCX seçili dosya görünümü ve aktif ekleme butonunu doğruladı.

### Oturum — 12 Haziran 2026 (Faz 4B devam: code_run QuickJS sandbox)
- `gateway/lib/code_sandbox.mjs` eklendi: QuickJS WASM tabanlı local-only JavaScript sandbox. Shell/process/require/fs/network yok; `input` JSON verisi, `console.log`, `return`, timeout, memory, code/output limitleri var.
- `gateway/lib/tools.mjs`: opsiyonel `code_run` aracı eklendi. Varsayılan kapalı; `CODE_TOOL_ENABLED=1` olmadan `TOOL_SPECS` içinde görünmez. Doğrudan çağrılsa bile kapalı mesajı döner.
- Yeni env ayarları: `CODE_TOOL_ENABLED`, `CODE_TOOL_TIMEOUT_MS`, `CODE_TOOL_MEMORY_MB`, `CODE_TOOL_MAX_CODE_CHARS`, `CODE_TOOL_MAX_OUTPUT_CHARS`.
- Web tool trace UI: `code_run` çağrıları "Kod sandbox" etiketi ve kod ikonu ile görünür.
- Testler eklendi: default kapalı spec, kapalı çağrı mesajı, sandbox input/console/return, host `process/require/fetch` yokluğu, sonsuz döngü interrupt.
- Doğrulama: `npm.cmd --prefix gateway test` 20/20 geçti; `npm.cmd run build` geçti; `npm.cmd --prefix gateway audit --audit-level=moderate` → 0 vulnerability. Env kapalı/açık ayrı Node prosesleriyle doğrulandı (`code_run` yalnız `CODE_TOOL_ENABLED=1` ile spec'e giriyor).

### Oturum — 12 Haziran 2026 (Faz 4B tamamlandı: persona/prompt kütüphanesi)
- `web/src/nova-agent.jsx`: hazır persona/prompt kütüphanesi eklendi. Kartlar: Genel NOVA, Kod İnceleyici, SOC Analisti, Mimari Planlayıcı, Yerel LLM Koçu, Özel Persona.
- Persona seçimi `buildSystem()` içine "Seçili persona" satırı olarak bağlandı; model, effort, reasoning ve agent akışları değişmeden çalışır.
- `personaId` ve `customPersona` ayarları mevcut IndexedDB/local fallback storage'a eklendi; seçim header alt satırında görünür ve sayfa yenilemede kalır.
- Ayarlar paneline iki kolonlu persona seçici ve özel persona textarea'sı eklendi.
- Doğrulama: `npm.cmd run build` geçti. Chrome/Playwright smoke: 6 kart göründü, SOC seçimi header'a geçti ve reload sonrası kaldı, özel persona metni reload sonrası korundu. Konsoldaki `ERR_NETWORK_ACCESS_DENIED` kayıtları Google Fonts dış kaynağına ait, persona değişikliğiyle ilgili değil.

### Oturum — 12 Haziran 2026 (Faz 4C başladı: görsel anlama routing)
- `gateway/lib/routing.mjs` eklendi: görsel içerik tespiti (`image_url`/`image`) ve test edilebilir dinamik model seçimi.
- Gateway `auto` isteği görsel içeriyorsa artık `VISION_MODEL` değerine gider; Qwen2.5 kullanılmayacak şekilde varsayılan `ollama/qwen3.5-omni:latest`. Metin isteklerinde eski effort/context routing davranışı korunur.
- `gateway/.env.example`: `VISION_MODEL=ollama/qwen3.5-omni:latest` ve opsiyonel `ROUTE_VISION` eklendi; 3.6 sınıfı bir yerel tag varsa buradan override edilecek.
- `/health` artık aktif `vision` modelini döndürür; `/v1/models` listesine `ollama/qwen3.5-omni:latest` eklendi.
- Görsel içeren istekte `agent:true` açık olsa bile metin odaklı tool-calling döngüsü atlanır; resim doğrudan vision modele gider.
- Testler eklendi: görsel mesaj tespiti, image request → vision model, text request → effort routing. Doğrulama: `node --check gateway/gateway.mjs` geçti; `npm.cmd --prefix gateway test` 22/22 geçti.
- Canlı HTTP smoke denenmek istendi ancak araç güvenlik/usage denetimi komutu reddetti; aynı yolu zorlamadık. Sonraki oturumda WSL/Ollama açıkken UI'dan gerçek görsel soru smoke testi yapılmalı.
- Kullanıcı Qwen2.5 istemediğini, minimum Qwen 3.5/3.6 istediğini netleştirdi. Bunun üzerine web model menüsündeki Qwen2.5-VL kartı Qwen3.5 Omni kartına çevrildi; gateway/env/test/README varsayılanları da `ollama/qwen3.5-omni:latest` çizgisine alındı. `ROUTE_VISION` ile Qwen 3.6 tag'i seçilebilir.
- Bu Codex ortamında canlı local model doğrulaması yapılamadı: Windows PowerShell'de `ollama` komutu PATH'te yok; `wsl ollama list` bu çalışma bağlamında WSL distro görmedi. Gerçek makinede/WSL oturumunda model tag'i ayrıca doğrulanmalı.
- UI tarafı tamamlandı: Ayarlar > Gateway kartı `/health` üzerinden aktif `default` ve `vision` modelini gösterir; Gateway + Dinamik Yönlendirme seçiliyken görsel eklenirse composer içinde `Görsel auto route: ...` ipucu çıkar.
- Görsel routing aşamasındaki doğrulama: `npm.cmd run build` geçti; `npm.cmd --prefix gateway test` 22/22 geçti. Kalan Faz 4C işi gerçek WSL/Ollama oturumunda Qwen 3.5/3.6 vision modelini çekip uçtan uca görsel soru smoke testi yapmak.
- Faz 4C ses kuyruğu başladı: `bullmq@5.78.0` gateway'e eklendi; `npm audit` 0 vulnerability döndü.
- `gateway/lib/voice.mjs` eklendi: STT/TTS payload validasyonu, limitler, Whisper proxy ve TTS proxy ortak fonksiyonlara taşındı. Eski `/stt` ve `/tts` endpoint'leri bu ortak modülü kullanır.
- `gateway/lib/voice_queue.mjs` eklendi: `VOICE_QUEUE_ENABLED=1` ile BullMQ/Redis queue açılır; `/v1/voice/jobs`, `/v1/voice/jobs/:id`, `/v1/voice/jobs/:id/audio` endpoint'leri async STT/TTS işlerini takip eder. Default kapalıdır, direkt ses akışı bozulmaz.
- Web Ayarlar > Ses bölümüne `Kuyruk` toggle'ı ve `Job ucu` alanı eklendi; gerçek ses + kuyruk açıkken UI job durumunu poll eder ve `STT kuyruğu` / `TTS kuyruğu` durum metnini gösterir.
- README ve `.env.example` BullMQ ses kuyruğu ayarlarıyla güncellendi. Kalan iş: Redis + whisper/tts servisleri ayaktayken async STT/TTS canlı smoke testi.
- Son doğrulama: `node --check gateway/gateway.mjs` geçti; `npm.cmd --prefix gateway test` 24/24 geçti; `npm.cmd run build` geçti (Vite büyük Mermaid/wardley chunk uyarısı beklenen); `npm.cmd --prefix gateway audit --audit-level=moderate` → 0 vulnerability.
- Canlı smoke tekrar denendi ancak bu Codex bağlamında WSL distro görünmüyor, Docker daemon yok ve `ollama` PATH'te değil. Bu yüzden sıradaki kodlanabilir Faz 4C işi yapıldı.
- `gateway/lib/image_inputs.mjs` eklendi: local/non-OpenAI vision provider'ları için remote `image_url` girdileri opt-in olarak (`REMOTE_IMAGE_URLS_ENABLED=1`) indirilip `data:image/...;base64,...` formatına çevrilir. SSRF riskine karşı localhost/private IP/DNS hedefleri default engellenir, boyut limiti `REMOTE_IMAGE_MAX_BYTES`.
- Gateway `/health` artık `remote_images` durumunu döndürür; UI Gateway kartında `Uzak görsel: açık/kapalı` gösterilir. README ve `.env.example` remote görsel ayarlarıyla güncellendi.
- Son doğrulama: `node --check gateway/gateway.mjs` geçti; `npm.cmd --prefix gateway test` 26/26 geçti; `npm.cmd run build` geçti (Vite büyük Mermaid/wardley chunk uyarısı beklenen).

### Oturum — 12 Haziran 2026 (README/proje inceleme + Faz 4 önerisi)
- Kullanıcı "projeyi incele, README oku, sonraki faz önerileri sun" dedi.
- Okunan ana dosyalar: `README.md`, `PROGRESS.md`, `PHASE1.md`, `PHASE2.md`, `WSL_DOCKER.md`, `SECURITY.md`, `docker-compose.yml`, `docker-compose.faz2.yml`, `gateway/.env.example`, root/gateway/web `package.json`.
- Doğrulandı: `npm.cmd --prefix gateway test` → 13/13 geçti; `npm.cmd run build` → Vite production build geçti (1570 modül, 246.09 KB JS / 74.13 KB gzip).
- Çalışma ağacı kirli: `README.md` ve `web/src/nova-agent.jsx` değişmiş, `gateway/test/agent.test.mjs` yeni dosya. Bunlar kullanıcı/önceki oturum değişikliği varsayılmalı, geri alınmamalı.
- README'ye göre Faz 0/1/2 ve Faz 3 ajan yetenekleri canlı doğrulanmış; sıradaki ana ürün fazı **Faz 4 — ürünleşme & üretkenlik**.
- Önerilen Faz 4 sırası: (1) mevcut artifact paneli + sohbet MD/JSON dışa aktarmasını tamamla/test et, (2) RAG belge yüklemeye PDF/DOCX çıkarımı ekle, (3) araç sonuçlarını kaynak rozetleriyle UI'da görünür yap, (4) persona/prompt kütüphanesi, (5) paylaşılabilir sohbet linki/PDF export.
- Paralel sertleştirme hattı: `PROGRESS.md` üst/alt bölümleri README ile çelişiyor; önce doküman durumunu tek gerçek kaynağa indir. Ardından Keycloak prod modu, default parola rotasyonu, `ALLOW_MODELS`, port daraltma, Sentry/OTel, `gateway/providers/` modülerleştirme.

### Oturum — 12 Haziran 2026 (Superpowers geliştirme seti)
- Kullanıcı "Superpowers vardı, yazılım geliştirme için her şeyi kur" dedi.
- Durum: Superpowers eklentisi zaten yerel cache'te kurulu ve aktif; güncel cache yolu kullanıcı profilindeki `.codex/plugins/cache/.../superpowers/.../skills` altında.
- Mevcut Superpowers becerileri: `using-superpowers`, `brainstorming`, `writing-plans`, `executing-plans`, `test-driven-development`, `systematic-debugging`, `verification-before-completion`, `requesting-code-review`, `receiving-code-review`, `finishing-a-development-branch`, `using-git-worktrees`, `dispatching-parallel-agents`, `subagent-driven-development`, `writing-skills`.
- Computer Use eklentisi de kurulum için onaylandı ve kuruldu. Ancak bu turda doğrudan masaüstü kontrol araçları görünür hale gelmedi.
- Güvenlik notu: Gmail/Drive/Slack/CRM gibi tüm üçüncü taraf eklentileri topluca kurulmadı; bunlar kişisel/kurumsal veri erişimi istediği için tek tek açık onayla kurulmalı.
- Bundan sonraki yazılım geliştirme akışı: yeni özellikte önce brainstorming/plan, riskli veya davranış değişikliğinde TDD, hata çözümünde systematic-debugging, bitirmeden önce verification-before-completion.

### Oturum — 12 Haziran 2026 (yazılım/test eklenti araştırması)
- Kullanıcı isimleri bilmediğini, yazılım geliştirme ve test için gerekli eklentilerin araştırılıp kurulmasını istedi.
- Aktif/gerekli çekirdek set zaten mevcut görünüyor: Superpowers, Build Web Apps, Browser, GitHub, CodeRabbit, Codex Security, CircleCI, Jam, OpenAI Developers, Hugging Face, Netlify/Vercel/Cloudflare, Computer Use.
- Tool search sonrası kullanılabilir araçlar arasında GitHub CI/status, Jam hata kayıtları, node_repl/browser otomasyonu, Lovable, Canva ve Ace Knowledge Graph da göründü.
- Toplu "tüm eklentiler" kurulumu yapılmadı; Gmail/Drive/Slack/Notion/Figma/CRM gibi bağlayıcılar veri erişimi istediği için ancak kullanıcı tek tek isterse kurulmalı/bağlanmalı.
- Önerilen ek opsiyonlar: Figma (UI/design handoff gerekiyorsa), Linear (issue/project yönetimi), Slack (ekip bildirimleri), Sentry/Datadog benzeri gözlemlenebilirlik eklentisi mevcut olursa prod hata inceleme için.

### Oturum — 12 Haziran 2026 (kurulu eklenti cache kontrolü)
- Kullanıcı "vardı, ben kurmanı istiyorum" diyerek geliştirme/test eklentilerini kurulu görmek istedi.
- Yerel plugin cache kontrol edildi. `openai-curated` altında geliştirme/test için gerekenler zaten mevcut: `build-web-apps`, `circleci`, `cloudflare`, `coderabbit`, `codex-security`, `datadog`, `github`, `hugging-face`, `jam`, `linear`, `lovable`, `netlify`, `notion`, `openai-developers`, `slack`, `supabase`, `superpowers`, `test-android-apps`, `vercel`.
- `openai-bundled` altında `browser`, `chrome`, `computer-use`, `latex` mevcut.
- `openai-curated-remote` altında `canva`, `data-analytics`, `github`, `google-calendar`, `hugging-face`, `jam`, `lovable`, `product-design` gibi ek araçlar mevcut.
- Sonuç: yazılım geliştirme/test için gerekli eklenti dosyaları kurulu. Bundan sonrası "hesap bağlama/connector yetkisi" işi; veri erişimi isteyen bağlayıcılar topluca açılmamalı, tek tek kullanıcı onayıyla bağlanmalı.

### Oturum — 12 Haziran 2026 (ana projeye dönüş: Faz 4A)
- Kullanıcı "şimdi ana projeye devam et" dedi.
- Faz 4A üretkenlik hattındaki mevcut `web/src/nova-agent.jsx` artifact/dışa aktarma çalışması korundu ve sıkılaştırıldı.
- Artifact iframe güvenliği iyileştirildi: `allow-same-origin` kaldırıldı; sadece Mermaid önizlemesi `allow-scripts` alıyor, HTML/SVG önizlemeleri script izni olmadan sandbox'ta açılıyor; iframe'e `referrerPolicy="no-referrer"` eklendi.
- Mermaid preview içeriğinde HTML escape `&`, `<`, `>` kapsayacak şekilde genişletildi.
- README Faz 4A durumunu "ilk sürüm" olarak güncelledi: artifact paneli + MD/JSON sohbet dışa aktarma var; PDF export ve paylaşılabilir link hâlâ sırada.
- Doğrulama: `npm.cmd run build` geçti; `npm.cmd --prefix gateway test` 13/13 geçti.
- Browser smoke: `web/dist` statik server ile Chrome/Playwright'ta örnek konuşma açıldı; 2 adet `Önizle` butonu bulundu, HTML sandbox değeri boş/katı, Mermaid sandbox `allow-scripts`, panel görünür. Not: Mermaid CDN bu ortamda `ERR_NETWORK_ACCESS_DENIED` verdi; sonraki adımda Mermaid'i yerel bundle'a alma veya graceful fallback ekleme değerlendirilmeli.

### Oturum — 12 Haziran 2026 (Faz 4A devam: Mermaid CDN kaldırıldı)
- `web` paketine `mermaid` bağımlılığı eklendi (`npm.cmd --prefix web install mermaid`, yetkili ağ erişimiyle).
- `web/src/nova-agent.jsx`: Mermaid top-level import yapılmadı; `loadMermaid()` ile sadece `Önizle` tıklandığında dynamic import ediliyor. Böylece ana bundle küçük kalıyor, Mermaid ayrı lazy chunk olarak yükleniyor.
- Mermaid diyagramı parent uygulamada SVG'ye render ediliyor; iframe artık tüm artifact türlerinde scriptsiz sandbox kullanıyor (`sandbox=""`).
- Render hatasında kaynak kodu ve hata mesajı iframe içinde güvenli fallback olarak gösteriliyor.
- README Faz 4A notu CDN'siz yerel render olarak güncellendi.
- Doğrulama: `npm.cmd run build` geçti; ana app chunk ~248 KB / 75 KB gzip, Mermaid ayrı `mermaid.core` lazy chunk oldu. `npm.cmd --prefix gateway test` 13/13 geçti.
- Chrome/Playwright smoke: 2 `Önizle` butonu bulundu, HTML sandbox `""`, Mermaid sandbox `""`, Mermaid `srcdoc` içinde `<svg` görüldü, panel görünür. Kalan console network error'ları Mermaid değil, Google Fonts dış kaynağı (`fonts.googleapis.com`) kaynaklı.
- `npm.cmd --prefix web audit --audit-level=moderate` sonucu o oturumda Vite/esbuild dev-server bulgusu vermişti; 13 Haziran güvenlik sertleştirme oturumunda `vite@8.0.16` yükseltmesiyle kapatıldı.

### Oturum — 12 Haziran 2026 (Faz 4A devam: PDF export ilk sürüm)
- Kullanıcı "projeye devam et" dedi; Faz 4A dışa aktarma hattı sürdürüldü.
- `web/src/nova-agent.jsx`: sol panel dışa aktarma satırına `PDF` düğmesi eklendi. Bu düğme temiz bir printable sohbet sayfası açıp tarayıcının yazdır/PDF olarak kaydet akışını tetikliyor.
- Printable export mesaj rolünü, route bilgisini, metni ve varsa görselleri güvenli HTML escape ile yazıyor. Popup engellenirse `.html` fallback olarak indiriliyor.
- README Faz 4A notu güncellendi: Markdown/JSON indirme + PDF olarak yazdır/kaydet ilk sürümü hazır; paylaşılabilir link sırada.
- Doğrulama: `npm.cmd run build` geçti; `npm.cmd --prefix gateway test` 13/13 geçti.
- Chrome/Playwright smoke: örnek sohbet yüklendi; sol panelde `MD`, `JSON`, `PDF` düğmeleri göründü; `PDF` tıklaması printable popup açtı, popup title `PDF Export Smoke`, gövdede `NOVA sohbet dışa aktarımı` ve örnek mesaj metni görüldü.

### Oturum — 10 Haziran 2026 (Linux sandbox'ta canlı doğrulama)
- `gateway/.env` oluşturuldu (rastgele `GATEWAY_TOKEN`, `ALLOW_ORIGINS` Vite origin'leri, Ollama varsayılanları). Provider key'i yok; chat için kullanıcı bir key girmeli veya Ollama çalıştırmalı.
- Gateway uçtan uca doğrulandı (Node 22, Docker'sız, bare-metal): `/health`=200, token'sız `/v1/models`=401, token'lı `/v1/models`=200 (8 model), `/metrics`=200, izinli origin'den CORS preflight=204. Sağlayıcısız `chat/completions` beklendiği gibi Ollama'ya gidip graceful 500 ("ollama aborted") veriyor — hata değil, config.
- Birim testler 7/7 geçti (`node --test`); tüm `.mjs` dosyaları `node --check`'ten geçti.
- Web production build doğrulandı: 1570 modül, 213 KB JS / 66 KB gzip. UI + gateway birlikte servis edildi, `index.html`/JS bundle 200, web origin'den gateway'e CORS preflight 204.
- **Bilinen sandbox kısıtı (proje hatası DEĞİL):** Windows'ta üretilmiş `web/dist/` Linux mount'ta `EPERM`/`Operation not permitted` ile silinemiyor; bu yüzden sandbox'ta `--outDir /tmp/nova-dist` ile derlendi. Kullanıcının Windows makinesinde `npm.cmd run build` zaten geçiyor. İsteğe bağlı temizlik: Windows'ta `rmdir /s /q web\dist` sonra rebuild.

### Oturum — 10 Haziran 2026 (devam: tokens.mjs + admin entegrasyonu)
- ✅ `tokens.mjs` `gateway.mjs`'e bağlandı: chat handler'da `makeUsageAccumulator(provider)` oluşturuluyor, `ctx.usage` üzerinden tüm provider stream parser'larına (`sseLine` observer + Ollama NDJSON) gerçek usage gözlemi eklendi. `recordChatCompletion` artık provider usage raporladıysa GERÇEK token sayısını kullanıyor; raporlamadıysa `approxTokens`'a düşüyor.
- ✅ OpenAI isteğine `stream_options:{include_usage:true}` eklendi (son chunk'ta usage gelir).
- ✅ `routes/admin.mjs` `gateway.mjs`'e mount edildi (MULTI_USER bloğunda, history/media ile birlikte). Kullanım: `ADMIN_USER_IDS` env'ine admin user id'leri yaz.
- ✅ Hata düzeltildi: `admin.mjs` `ADMIN_USER_IDS`'i import anında okuyordu; gateway'in `.env` loader'ı importlardan SONRA çalıştığı için `.env`'deki değer görünmüyordu. Lazy okumaya (`admins()` fonksiyonu) çevrildi.
- Doğrulandı: tam dosya `node --check` geçti, birim testler 7/7, canlı smoke (`/health` 200, token'lı `/v1/models` 200, sağlayıcısız chat graceful 500).
- **Sandbox mount kısıtı notu:** Linux sandbox mount'u dosya güncellemelerinde eski bayt boyutunu önbelliyor (içerik güncel, boyut eski → kuyruk kesik görünüyor). Windows tarafındaki gerçek dosyalar TAM ve doğru. Sandbox'ta doğrulama `/tmp` kopyasıyla yapıldı. Windows'ta `node --check gateway\gateway.mjs` ile teyit edilebilir.

### Oturum — 10 Haziran 2026 (devam 2: CANLI MULTI-USER STACK TESTİ ✅)
- Sandbox'ta embedded Postgres 18.4 (npm `embedded-postgres`) ile **canlı multi-user stack testi yapıldı ve 10/10 geçti** (Docker'sız; Redis `RATE_MAX=0` ile atlandı — `rateLimit` max<=0'da Redis'e hiç dokunmuyor):
  1. Migration'lar gerçek Postgres'e uygulandı: `applied 001_init.sql` + `applied 002_billing.sql` ✅
  2. `bootstrap-user.mjs` çalıştı: user + API key + $5/ay kota ✅
  3. Gateway `MULTI_USER=on` başladı (`auth=multi-user`) ✅
  4. `/health` 200 (public) ✅ · token'sız `/v1/models` 401 ✅ · API key ile 200 ✅ · geçersiz key 401 ✅
  5. Admin (`ADMIN_USER_IDS` ile): key üretme 201 ✅ · key listeleme 200 (hash sızdırmıyor) ✅ · kota PUT 204 ✅
  6. History: `POST /v1/conversations` 201 ✅ · listeleme 200 ✅
  7. Kota: limit 0 yapılınca chat **402 "quota exceeded"** ✅
  8. Prometheus `/metrics`: route-bazlı sayaçlar doğru artıyor (`:id` cardinality collapse dahil) ✅
- Yani Faz 1 çekirdeği (auth + admin + kota + history + metering) artık **canlıda doğrulanmış** durumda. Docker'lı tam stack (Redis dağıtık rate limit, MinIO `/v1/media`, Grafana) testi kullanıcının WSL'inde yapılacak.
- 📄 **`WSL_DOCKER.md` eklendi:** WSL Ubuntu + native Docker Engine kurulumu, `.env`, compose up, migrate kontrolü, bootstrap ve smoke testleri için adım adım runbook.
- Canlıda test EDİLMEYENLER (Docker stack'inde yapılacak): Redis dağıtık rate limit (429 yolu), `/v1/media` (MinIO), Stripe billing flush, Grafana/Prometheus scrape.

### Oturum — 10 Haziran 2026 (devam 3: WSL'DE DOCKER STACK CANLI ✅)
- Kullanıcının WSL Ubuntu 24.04'ünde (native Docker Engine 29.1.3 + Compose v5.1.4) **tam Faz 2 stack'i kaldırıldı ve uçtan uca doğrulandı**:
  - 9 servis ayakta: postgres(healthy), redis(healthy), gateway(production modda, Caddy arkasında), caddy(80/443), minio, prometheus(9090), grafana(3001), whisper(8000), tts(8001).
  - Migrate one-shot: `applied 001_init.sql` + `applied 002_billing.sql` ✅
  - Bootstrap: yerel test kullanıcısı, $5/ay kota; üretilen user id `.env`'de `ADMIN_USER_IDS`'e yazıldı. API key kullanıcıda.
  - HTTPS üzerinden (Caddy, self-signed): token'sız 401 ✅ · API key ile models 200 ✅ · admin key listesi 200 ✅ · konuşma oluşturma 201 ✅ · **`/v1/media` 201 + MinIO imzalı URL** ✅ · **Redis dağıtık rate limit: RATE_MAX=3 ile 4. chat isteği 429** ✅ (test sonrası 120'ye geri alındı).
- **Öğrenilen ders:** `docker compose restart` `env_file`'ı yeniden OKUMAZ — `.env` değişince `docker compose up -d gateway` ile container'ı yeniden oluştur.
- **Düzeltme gereken eksik bulundu:** MinIO bucket'ı (`nova-media`) otomatik oluşmuyor; gateway container'ından `CreateBucketCommand` ile elle oluşturuldu. TODO: storage.mjs'e idempotent `ensureBucket()` ekle veya compose'a bir init job koy.
- Kalan canlı test: Stripe billing flush (STRIPE_SECRET_KEY yok), Grafana dashboard kurulumu, UI'dan uçtan uca chat (provider key veya WSL'den erişilebilir Ollama gerek; container içinden `localhost:11434` çalışmaz — `host.docker.internal` / `extra_hosts` gerekir).

### Oturum — 10 Haziran 2026 (devam 4: LOCAL LLM + UI UÇTAN UCA CANLI ✅)
- **Yerel agentic LLM bağlandı:** WSL'deki native Ollama (0.23.0) `OLLAMA_HOST=0.0.0.0` systemd override ile container'lara açıldı; compose'da gateway'e `extra_hosts: host.docker.internal:host-gateway` + `OLLAMA_URL=http://host.docker.internal:11434` eklendi. Model: **`qwen3.5-9b-agent:latest`** (6.6 GB, RTX 3070 8GB VRAM'e uygun, agent-tuned) — `DEFAULT_MODEL` yapıldı ve `/v1/models` listesine eklendi.
- **HATA BULUNDU + DÜZELTİLDİ (`pickModel`):** cloud key yokken `auto`, DEFAULT_MODEL yerine hardcoded `ollama/qwen3:14b` döndürüyordu → lokalde o tag yoksa 404/"upstream error". Üç fallback da `DEFAULT`'a çevrildi.
- **Caddy:** local test için `http://{$DOMAIN}` da servis ediliyor (self-signed interstitial'sız `http://localhost`); `.env` CORS'una `http://localhost` ve `https://localhost` eklendi.
- **UI uçtan uca DOĞRULANDI (Chrome, Claude eklentisiyle sürüldü):** `http://localhost` → ayarlar → Gateway base URL `http://localhost/v1` + API key → Sohbet → soru → **modelden gerçek Türkçe cevap** (rota: `ollama/qwen3.5-9b-agent:latest`). Postgres `usage_events`'te GERÇEK token sayıları (örn. 31/5) — tokens.mjs canlıda çalışıyor.
- ~~**Bilinen sorun (TODO):** Düşünme açıkken "(boş yanıt)"~~ → **ÇÖZÜLDÜ (devam 5):** gateway `relay()` artık Ollama `message.thinking`'i OpenAI tarzı `delta.reasoning_content` olarak aktarıyor; UI'ın gateway/openai parser'ı bunu `onThought`'a bağlıyor → "Muhakeme" trace'i canlı akıyor, cevap content olarak geliyor. Canlıda doğrulandı (25*17 testi: trace + doğru cevap 425). `REQ_TIMEOUT_MS` 180000'e çıkarıldı (uzun düşünmeler 60s'de kesilmesin).
- **ensureBucket eklendi (devam 5):** `storage.mjs` ilk `putMedia`'da bucket'ı lazy + idempotent oluşturuyor (BucketAlready* yakalanıyor); taze stack'te elle bucket oluşturma gereği kalktı. Web build artık WSL'de docker `node:22-alpine` ile yapılabiliyor (dist içeriği değiştirilir, dist klasörü silinmez — Caddy bind mount).

### Oturum — 10 Haziran 2026 (devam 6: GRAFANA DASHBOARD CANLI ✅)
- Grafana'da (localhost:3001) **Prometheus datasource (`prom-nova` → http://prometheus:9090) ve "NOVA Gateway" dashboard'u (uid `nova-gw`, /d/nova-gw) Grafana HTTP API'siyle programatik kuruldu**; 4 panel: HTTP istek hızı (route/status), LLM token hızı (route/direction), p95 gecikme (histogram_quantile), Toplam LLM token (stat, in/out).
- Hepsi canlı veriyle doğrulandı: gerçek token sayaçları (in 131 / out 316), chat spike'ı, 401/200 hatları görünüyor. Datasource health OK, `/api/ds/query` veri döndürüyor.
- Not: Grafana 12 panelleri lazy-load ediyor; sayfa ilk açılışta boş görünebilir — bir kez scroll/etkileşim + birkaç saniye gerekiyor (özellikle ilk plugin yüklemesi yavaş). Hata değil.
- Dashboard JSON'u repo'ya kaydedildi: `monitoring/grafana-nova-gw.json` ✅

### Oturum — 10 Haziran 2026 (devam 7: UI KALICILIK ✅)
- **Hata bulundu + düzeltildi:** `nova-agent.jsx`'teki `store` claude.ai artefakt köprüsü (`window.storage`) yoksa belleğe düşüyordu → her sayfa yenilemede API key, ayarlar ve sohbetler sıfırlanıyordu. `store`'a **IndexedDB** (`nova-store` DB, `kv` store) + localStorage fallback eklendi; mevcut hydrate/save (debounce 400ms) mekanizması olduğu gibi çalışıyor.
- Canlıda doğrulandı: ayarlar girildi → IndexedDB'de 42 karakterlik key görüldü → F5 → Base URL `http://localhost/v1` ve key alanı dolu geldi → kayıtlı key ile sohbet + thinking trace sorunsuz.
- Not: API key artık tarayıcıda kalıcı (IndexedDB). Paylaşılan makinede istenmiyorsa ayarlardan silinebilir; uzun vadeli doğru çözüm hâlâ OIDC/JWT login.
- Sıradaki büyük iş: OIDC sağlayıcı (Keycloak/Clerk/Auth0) + web'e JWT login + sunucu tarafı `/v1/conversations` geçmişine geçiş.

### Oturum — 11 Haziran 2026 (devam 12: FAZ 3 AJAN — web arama + tool calling + RAG ✅)
- **Tool-calling ajan döngüsü** (`lib/agent.mjs`): Ollama `/api/chat` + `tools`, model araç çağırır → gateway çalıştırır → sonucu geri besler → nihai cevap. Maks 4 tur. `agent:true` ile tetiklenir (yalnız ollama provider).
- **Araçlar** (`lib/tools.mjs`): `web_search` (SearXNG), `doc_search` (pgvector RAG, userId kapsamlı), `calculator` (güvenli eval), `current_time`.
- **SearXNG** compose'a eklendi (`searxng/settings.yml`, port 8080); `SEARXNG_URL` env. Canlı: 37 sonuç, "Ankara hava durumu" → web_search → MGM kaynaklı cevap.
- **RAG:** `pgvector/pgvector:pg16` imajı + migration `003_knowledge.sql` (documents, doc_chunks vector(768), ivfflat). `lib/embed.mjs` (nomic-embed-text), `lib/rag.mjs` (chunk/ingest/search), `routes/knowledge.mjs` (`/v1/knowledge` yükle/listele/sil). Canlı: belge yüklendi (201, 1 parça) → ajan `doc_search` → gizli kod "ZÜMRÜT-7" belgeden bulundu.
- **UI:** alt barda **Ajan** toggle (kalıcı), mesajda araç-kullanım kartı (yapısal, `onTool`), ayarlarda **Bilgi Tabanı** bölümü (başlık+metin/dosya yükle, belge listesi+sil).
- **Model notu:** araç çağırma için Qwen/Titus güvenilir, Gemma zayıf — UI'da Ajan açıkken Qwen/Titus seçilmeli. Embed modeli `nomic-embed-text` (274 MB) kurulu.
- Migration sırası doğrulandı: skip 001/002, **applied 003_knowledge.sql**.

### Oturum — 10 Haziran 2026 (devam 8: GEMMA 4 + TITUS MODELLERİ EKLENDİ)
- **Önemli mimari netleştirme:** gateway'in `route()` mantığı en baştan beri HER `ollama/<isim>` modelini destekliyordu — yani Gemma 4 gibi modeller "entegrasyon" gerektirmiyordu, sadece küratörlü listelerde (UI dropdown + `/v1/models`) görünmüyorlardı.
- **Gemma 4 eklendi** (kullanıcının kurulu modellerinden): UI dropdown'a `gemma4:latest`, `gemma4:e4b`, `gemma4:e2b` (8GB VRAM'e en uygun olan e2b işaretli); `/v1/models`'a da eklendi. Canlıda doğrulandı: `gemma4:e2b` UI'dan Türkçe cevap verdi ("SQL injection nedir?" → doğru tek cümle).
- **Titus Cybersecurity LLM** (HF: `AlicanKiraz0/Titus-CybersecurityLLM-v1.0-Q4_K_M-No-MTP-GGUF`, ~21 GB, qwen3.5-moe, Apache-2.0): Ollama'ya `ollama pull hf.co/...` + `ollama cp ... titus-cyber:latest` ile alınıyor (indirme büyük). UI'da "Güvenlik · Gateway" grubuna ve `/v1/models`'a `ollama/titus-cyber:latest` olarak eklendi (gateway üzerinden yönlendiriliyor — auth/metrik/persist dahil). İndirme tamamlanınca menüden seçilip test edilebilir.
- **UX notu:** Düşünme (think) açıkken küçük modeller (e2b gibi) uzun "thinking" üretip cevabı geciktiriyor → ekranda "..." uzun kalıyor. Hata değil; kısa cevap istenen yerlerde Düşünme kapatılmalı. (Gateway reasoning_content aktarımı doğru çalışıyor.)
- **Karışıklık önleme notu:** Gemma'lar UI dropdown'da `provider: "ollama"` (tarayıcı→Ollama doğrudan), Titus ise `provider: "gateway"` (`ollama/titus-cyber:latest`). İstenirse Gemma'lar da gateway'e alınabilir (auth/metrik için daha tutarlı olur) — TODO.

### Oturum — 10 Haziran 2026 (devam 9: OIDC/KEYCLOAK + JWT CANLI ✅)
- **Keycloak 26 compose'a eklendi** (`docker-compose.faz2.yml`): port 8081, `start-dev --import-realm`, hostname `http://localhost:8081`, `--hostname-backchannel-dynamic=true` (gateway JWKS'e `http://keycloak:8081` içinden ulaşır, token issuer browser URL'i olur). Admin: admin/admin.
- **Realm:** `keycloak/nova-realm.json` — realm `nova`, public client `nova-web` (PKCE S256, redirect `http://localhost/*` + `https://localhost/*` + `:5173`). Güncel public-ready durumda varsayılan kullanıcı/parola import edilmez ve password grant kapalıdır.
- **Gateway OIDC env'leri** faz2 compose'da: `OIDC_ISSUER=http://localhost:8081/realms/nova`, `OIDC_JWKS_URL=http://keycloak:8081/...certs`, audience boş.
- **HATA DÜZELTİLDİ (`auth.mjs` userFromJwt):** upsert sadece `oidc_sub` çakışmasını yakalıyordu; bootstrap'la oluşmuş kullanıcı (aynı email, `oidc_sub` NULL) JWT ile girince email unique index'ine takılıp 401 olurdu. Önce email-eşleşen hesaba `oidc_sub` bağlanıyor (account linking), yoksa insert.
- **Yaşanan pürüz:** ilk realm import'unda kullanıcıda `lastName` yoktu → Keycloak "Account is not fully set up" verdi. JSON'a `lastName` + `requiredActions: []` eklendi, volume silinip yeniden import edildi.
- **Canlıda doğrulandı (tarayıcıdan):** discovery OK → password grant ile JWT → JWT ile `/v1/models` **200** → JWT ile chat **200** + model cevabı. API key VE JWT artık aynı anda çalışıyor.
- ~~Kalan (Faz B): web UI'a "Keycloak ile giriş" butonu~~ → **TAMAMLANDI (devam 10)**, aşağıya bak.

### Oturum — 11 Haziran 2026 (devam 10: UI PAKETİ CANLI ✅ — login + kullanım + sunucu geçmişi + ses)
- **Keycloak ile Giriş (PKCE) UI'da canlı:** Ayarlar > Gateway'de "Keycloak ile Giriş" butonu → authorization code + S256 PKCE → dönüşte token exchange → JWT, gateway key alanına otomatik yazılır; oturum IndexedDB'de (`nova:auth:v1`), 30 sn'de bir süresi kontrol edilip refresh_token ile sessiz tazelenir; "Çıkış" + email rozeti var. Uçtan uca canlı test, o dönemde oluşturulmuş yerel test kullanıcısıyla doğrulandı.
- **Kullanım/kota paneli:** Yeni `GET /v1/usage` endpoint'i (`gateway/routes/usage.mjs`, MULTI_USER'da mount). Ayarlar panelinde "Kullanım — Bu Ay": giren/çıkan token, istek, maliyet kartları + kota progress barı + model bazlı dağılım. Canlıda gerçek veriyle görüldü (445/4.667 tok, 6 istek).
- **Sunucu geçmişi:** `ensureServerConv()` gateway+oturum varken sohbeti `POST /v1/conversations` ile sunucuda açıyor; chat isteklerine `conversation_id` ekleniyor → gateway mesajları Postgres'e yazıyor (canlıda doğrulandı: 2 mesaj). Açılışta sunucu sohbetleri çekmeceye ekleniyor (bulut ikonu), seçilince mesajları lazy yükleniyor.
- **Medya:** Görsel eklenince `/v1/media`'ya da arşivleniyor (MinIO); model çağrısı data URL ile sürüyor (gateway'in remote URL fetch'i yok — TODO).
- **Gerçek ses varsayılanları:** `/stt` ve `/tts` (same-origin, Caddy → whisper/tts). Eski kayıtlı `localhost:8088` uçları hydrate'te otomatik göç ediyor. Ayarlarda "Gerçek ses" toggle'ı zaten vardı.
- **REBOOT SONRASI ÖNEMLİ NOTLAR:**
  1. WSL Docker stack'i reboot'ta otomatik kalkmıyor → `sudo systemctl enable --now docker` yapıldı (artık WSL açılınca docker başlar) ama **compose ile `up -d` yine gerekli** (bir WSL penceresi aç + `docker compose ... up -d`).
  2. **`docker compose` eklentisi Docker Desktop entegrasyonundan geliyormuş** — Desktop kapalıyken "unknown shorthand flag: 'f'" hatası verdi. Kalıcı çözüm: `sudo apt-get install docker-compose-v2` (yapıldı; artık Desktop'a bağımlılık yok).
  3. GPU şüphesi test edildi: üretim sırasında VRAM 1213→7790 MiB — **LLM GPU'da çalışıyor**, dünkü "yanıt yok" sorununun sebebi stack'in kapalı olmasıydı.
- Bilinen küçük işler: Keycloak prod modda değil (start-dev); UI OIDC issuer'ı sabit `http://localhost:8081` (env'e alınabilir); gateway remote-URL görsel fetch.

### Oturum — 11 Haziran 2026 (devam 11: ARAYÜZ YENİLEME ✅ + varsayılan model Gemma 4)
- **Varsayılan model:** `auto` artık `ollama/gemma4:latest`'e gidiyor (compose + .env `DEFAULT_MODEL`); canlı doğrulandı (`x-nova-route: ollama/gemma4:latest`).
- **Kalıcı sol panel (≥980px):** NOVA marka + Yeni sohbet + **sohbet arama** + senkron rozeti (bulut) + altta "Ayarlar · kullanıcı" kısayolu. Dar ekranda eski çekmece (`.rail-hide` hamburger) korunuyor.
- **Mesaj istatistikleri:** her AI cevabının altında model, toplam süre, **TTFT** (ilk token), ~token, **tok/sn** ve saat çipleri (`m.stats`, complete() ölçüyor). Canlıda görüldü: `gemma4:latest · 49.7 sn · ⚡25.3sn · ~1.143 tok · 23 tok/sn`.
- **Header hızlı model menüsü:** sağ üstteki durum rozeti tıklanabilir oldu; tüm model grupları açılır listede, tek tıkla geçiş.
- Markdown + kod kopyalama ZATEN VARDI (`Markdown`/`CodeBlock` bileşenleri) — canlıda kod bloğu + başlık/liste render'ı doğrulandı.
- **Grammarly fix:** kompozere `data-gramm="false"` + grammarly elementlerini gizleyen CSS — kullanıcının "yazı kutusunda imleç/simge var" dediği şey Grammarly eklenti rozetiydi, temizlendi.
- **ÖNEMLİ ARAÇ NOTU:** sandbox'ın Linux mount'u `nova-agent.jsx` gibi büyüyen dosyalarda BAYAT içerik gösterebiliyor (1331 satır gösterdi, gerçek dosya 1600+; `Markdown` tanımı "yok" gibi görünüyordu). UI dosyası okumaları Windows tarafındaki dosya araçlarıyla (Read/Grep) yapılmalı, bash ile değil.
- Hatırlatma: API key tarayıcı belleğinde, sayfa yenilenince yeniden girilmeli (JWT login zaten yol haritasında).

### Son oturumda yapılanlar
- PowerShell'de doğrudan `npm` komutunun execution policy nedeniyle takıldığı görüldü; bu makinede `npm.cmd ...` kullanılmalı.
- Web build hatasının kök nedeni bulundu: `web/node_modules` farklı platformdan gelmişti ve Windows `.bin` shim'i yoktu.
- `web/package.json` script'leri `vite` shim'i yerine `node ./node_modules/vite/bin/vite.js` çağıracak şekilde düzeltildi.
- `web` ve `gateway` bağımlılıkları Windows ortamına uygun şekilde yeniden kuruldu.
- Doğrulandı: `npm.cmd run build` geçti, gateway transient çalıştırmada `/health` döndü, Browser'da üretim build render oldu, console error/warn yoktu, "Sohbet" modu ve textarea yazımı çalıştı.
- Deploy önkoşulu olarak `gateway/.env` dosyasının henüz olmadığı görüldü; bu nedenle `docker compose config` şu an beklenen şekilde `.env not found` ile duruyor.
- Faz 1 + Faz 2 gateway entegrasyonu yapıldı: `principal()`/history/media, Prometheus `/metrics`, request logging, Redis quota/rate-limit hook'u, usage persistence, LLM token metriği ve Stripe billing flush scheduler `gateway.mjs` içine bağlandı.
- `gateway/lib/cache.mjs` Redis bağlantısı lazy hale getirildi; yerel DB'siz kullanımda Redis yok diye gateway import aşamasında düşmez.
- `gateway/package.json` ve lock dosyasından gereksiz `nova-agent: file:..` bağımlılığı kaldırıldı; Docker context dışına referans kalmadı.
- Doğrulandı: gateway `.mjs` dosyaları `node --check` geçti, `npm.cmd run build` geçti, production multi-user smoke testte `/health=200`, `/metrics=200`, token'sız `/v1/models=401` döndü.
- Docker imaj build'i denenmek istendi ancak Docker Desktop/Linux engine çalışmadığı için doğrulanamadı.
- Docker işini WSL'de yapma fikri değerlendirildi; `wsl --status` WSL 2 altyapısının var olduğunu gösteriyor, ancak `wsl --list --all --verbose` kayıtlı Linux dağıtımı olmadığını söylüyor. Ubuntu/Debian/SUSE Appx paketi de görünmedi; WSL yolu için önce bir distro kurulmalı veya mevcut distro bu kullanıcı altında kaydedilmeli.

### Yeni oturumda ilk yapılacaklar
1. Docker için yol seç: Docker Desktop/Linux engine'i başlat veya önce WSL Ubuntu distro kur.
2. WSL seçilirse: Ubuntu kurulumunu tamamla, sonra Docker Desktop WSL integration ya da WSL içinde native Docker engine'i etkinleştir.
3. `gateway/.env.example` dosyasını `gateway/.env` olarak kopyala ve gerçek değerleri doldur: `ALLOW_ORIGINS`, en az bir provider/Ollama ayarı, S3/MinIO gerekiyorsa `S3_*`; multi-user için API key/JWT akışı kullanılacak.
4. `npm.cmd run build` ile web build'i tekrar doğrula.
5. `docker compose -f docker-compose.yml -f docker-compose.faz2.yml config` çalıştır; sonra `docker compose -f docker-compose.yml -f docker-compose.faz2.yml up -d --build` ile stack'i kaldır.
6. Migrate loglarını (`001_init.sql`, `002_billing.sql`), gateway healthcheck'i, `/metrics`, `/v1/media` ve UI'dan Gateway provider ile uçtan uca chat akışını test et.

## Hedef

NOVA'yı kişisel/yerel araçtan **herkese açık, çok kullanıcılı production** servisine
taşımak. Öncelikler: ayağa kaldırma · güvenlik · ölçeklenebilirlik · kod kalitesi ·
yeni özellikler · maliyet kontrolü.

## Genel ilerleme

| Faz | Durum | Özet |
|---|---|---|
| İnceleme | ✅ bitti | Mimari raporu + as-is/to-be diyagramları (`NOVA_Mimari_Inceleme.md`) |
| Faz 0 — gönderilebilir | ✅ iskelet hazır + doğrulandı | Docker + Caddy/TLS + token guard + CI (`DEPLOY.md`) |
| Faz 1 — çok kullanıcı | 🟡 gateway'e bağlandı, canlı stack testi bekliyor | DB/Redis/auth/usage/persistence modülleri + şema + compose (`PHASE1.md`) |
| Faz 2 — ölçek + ürün | 🟡 gateway'e bağlandı, canlı stack testi bekliyor | Gözlemlenebilirlik + object storage + billing + K8s/HPA + voice (`PHASE2.md`) |

## Tamamlananlar

### Faz 0 (gönderilebilir hale getirme)
- `gateway/Dockerfile` + `.dockerignore` — non-root, healthcheck'li üretim imajı.
- `Caddyfile` — TLS + `web/dist` servis + `/v1 /stt /tts` reverse proxy.
- `docker-compose.yml` — tüm stack (aşağıda Faz 1 ile genişletildi).
- `gateway.mjs` — production'da boş `GATEWAY_TOKEN` / `*` CORS'u reddeden preflight guard (98–111. satırlar).
- `.github/workflows/ci.yml` — install · web build · gateway smoke test · imaj build.
- **Doğrulandı:** guard 5 senaryoda test edildi (boş token/`*` → exit 1; token+domain → başlar).

### Faz 1 (çok kullanıcı çekirdeği — temel)
- Şema: `gateway/migrations/001_init.sql` (orgs, users, memberships, api_keys, conversations, messages, usage_events, quotas, provider_configs).
- Migration runner: `gateway/migrate.mjs` (`npm run migrate`).
- Modüller: `gateway/lib/{db,cache,auth,usage,persistence,keys,pricing}.mjs` + `gateway/routes/history.mjs`.
- `docker-compose.yml`'e postgres:16 + redis:7 + one-shot migrate eklendi.
- `package.json`'a `pg`, `ioredis`, `jose` + `migrate` script'i eklendi.
- `.env.example`'a Faz 1 değişkenleri (DATABASE_URL, REDIS_URL, OIDC_*) eklendi.
- **Doğrulandı:** şema pg-mem'de uygulandı (9 tablo + CRUD), 9 modül `node --check`'ten geçti, keys/pricing 16/16 saf test geçti, compose geçerli YAML.

### Faz 2 (ölçek + ürün — temel)
- Gözlemlenebilirlik: `gateway/lib/observability.mjs` (pino + reqId) + `gateway/lib/metrics.mjs` (Prometheus + `/metrics`).
- Object storage: `gateway/lib/storage.mjs` + `gateway/routes/media.mjs` (`POST /v1/media`, base64 yerine referans).
- Billing: `gateway/lib/billing.mjs` (Stripe metered `flushUsage`) + `gateway/migrations/002_billing.sql`.
- K8s: `k8s/` (namespace, configmap, secret örneği, Deployment + initContainer migrate, Service, HPA 3→20, Ingress + SSE buffering off).
- Faz 2 yerel yığını: `docker-compose.faz2.yml` (MinIO + Prometheus + Grafana + whisper + tts) + `monitoring/prometheus.yml`.
- `package.json`'a `@aws-sdk/client-s3`, `s3-request-presigner`, `pino`, `prom-client`, `stripe`.
- **Doğrulandı:** 002 migration'ı pg-mem'de 001 üstüne uygulandı, yeni modüller `node --check`'ten geçti, routeLabel/mediaKey/microsToCents saf testleri geçti, tüm k8s + faz2 compose + prometheus YAML geçerli.

## 👉 SIRADAKİ ADIM (buradan devam)

**Faz 4 + Faz 5 (kod) tamam.** 5A CI/güvenlik + 5B gözlemlenebilirlik (ajan/araç metrikleri + Grafana panelleri + opt-in OTLP trace exporter). Abort fix canlıda (`b05cb44` + Docker rebuild).

**⚠️ İLK İŞ — 5B'yi commit et (kullanıcı, Windows/WSL):** `metrics.mjs`/`agent.mjs`/`tracing.mjs`/`gateway.mjs`/`agent.test.mjs`/`units.test.mjs`/`grafana-nova-gw.json`/`.env.example`/docs değişiklikleri **henüz commit'li değil**:
1. `npm.cmd --prefix gateway test` → ~43 test geçmeli (ajan-metrik + 2 tracing testi dahil).
2. `git add -A && git commit -m "feat: agent metrics + Grafana panels + opt-in OTLP tracing (Faz 5B)" && git push origin main`.
3. Grafana'da (localhost:3001) NOVA Gateway dashboard'unu reimport et → 3 yeni panel. Trace istersen `OTEL_EXPORTER_OTLP_ENDPOINT`'i bir collector'a (Jaeger/Tempo/OTel Collector) ayarla.

**Faz 5'in kalanı = saf deploy/ops kararları (kod değil, `SECURITY.md`):** Keycloak prod mode (`start`), iç portları kapat, secret rotation, `ALLOW_MODELS` allowlist, Docker gateway env'inde `VISION_MODEL`'i kurulu bir vision tag'ine ayarla (`gemma4:latest`/`qwen3.6:latest`; kod default `qwen3.5-omni` çoğu kurulumda yok).

**Sıradaki kod fazı — Faz 6 (Android):** `nova-android/` içinde `gradle wrapper --gradle-version 8.9` ile `gradle-wrapper.jar` üret, Android Studio/Gradle test-build smoke, CI'daki android job'unu aç.

## Kalan TODO'lar

**Faz 1'i tamamlamak için:**
- ✅ Gerçek token kullanımı: `gateway/lib/tokens.mjs` eklendi VE `gateway.mjs`'e bağlandı (10 Haz 2026): stream'de `observe`, sonda gerçek usage; OpenAI'da `stream_options:{include_usage:true}`.
- ✅ API key yönetimi: `gateway/routes/admin.mjs` + bootstrap CLI hazır VE router `gateway.mjs`'e mount edildi (10 Haz 2026). Kalan: deploy'da `ADMIN_USER_IDS` env'ini set et.
- OIDC sağlayıcı seç + kur (Keycloak self-host veya Clerk/Auth0), `OIDC_*` doldur.
- Web istemciyi JWT login + `/v1/conversations` geçmişine bağla; tarayıcı-doğrudan sağlayıcı modunu kaldır.

**Kalite:** ✅ `gateway/test/units.test.mjs` + `gateway/test/agent.test.mjs` node:test kapsamı negatif/edge/security case'lerle genişledi; Faz 5A'da mock'lu `runAgent` ajan-döngüsü testleri eklendi → birleşik suite **39 test** (Windows'ta `npm.cmd --prefix gateway test`). ✅ CI güvenlik otomasyonu: Node 20.19+22 matris, secret scan, gateway/web audit, canlı smoke ve tek-komut `npm run security` kapısı eklendi.

**Faz 2'yi tamamlamak için** (temel hazır, kalanlar):
- ✅ Ses pipeline'ını kuyruğa (BullMQ) al; uzun transkripsiyonu async yap. Kalan: Redis/voice servisleriyle canlı smoke.
- Web istemciyi `/v1/media`'ya geçir (büyük base64 yerine referans); JWT login + geçmiş.
- Sentry/OpenTelemetry trace kurulumunu tamamla; Grafana dashboard'larını sürümle.
- ✅ Provider çağrılarını `gateway/lib/providers.mjs` modülüne taşı. Kalan: tool-calling passthrough ve provider modülleri için daha geniş integration test.

**Bilinen küçük engel:** Android `gradle/wrapper/gradle-wrapper.jar` eksik → `gradle wrapper --gradle-version 8.9` ile bir kez üret (DEPLOY.md).

## Doküman haritası

| Dosya | İçerik |
|---|---|
| `PROGRESS.md` | (bu dosya) durum + sıradaki adım |
| `NOVA_Mimari_Inceleme.md` | Tam mimari inceleme, as-is/to-be, faz yol haritası, trade-off'lar |
| `DEPLOY.md` | Faz 0 deploy runbook'u |
| `PHASE1.md` | Faz 1 modülleri + gateway.mjs entegrasyon patch'i + TODO'lar |
| `PHASE2.md` | Faz 2 (gözlemlenebilirlik, storage, billing, K8s/HPA, voice) + entegrasyon |
| `README.md` / `README (2).md` | Orijinal proje + Android istemci dokümanları |

## WSL / Docker Notu - 2026-06-09

- WSL motoru var (`wsl --status` WSL 2 gösterdi) fakat bu oturumdan bakınca kayıtlı Linux distro yok: `wsl --list --all --verbose` boş dönüyor.
- `wsl -d Ubuntu` bu oturumda `WSL_E_DISTRO_NOT_FOUND` ile döndü; yani çalışan/kayıtlı `Ubuntu` distro görünmüyor.
- Kullanıcı tarafında `wsl --install -d Ubuntu` komutu `Wsl/InstallDistro/ERROR_ALREADY_EXISTS` verdi. Bu, `Ubuntu` adının bir yerde rezerve/yarım kalmış olabileceğini düşündürüyor.
- Bu oturumda `wsl --install -d Ubuntu --name NovaUbuntu` yetkili olarak denendi ancak 2 dakikada zaman aşımına düştü; sonrasında `wsl --list --all --verbose` hâlâ kayıtlı distro göstermedi.
- `wsl --help` çıktısı bu makinedeki WSL sürümünde `--name`, `--no-launch` ve `--web-download` seçeneklerinin desteklendiğini doğruladı.
- Yetkili `Get-AppxPackage -AllUsers *Ubuntu*` teşhisi araç kullanım limiti nedeniyle çalıştırılamadı; kullanıcı PowerShell'de manuel çalıştırmalı.
- Destructive olmayan en güvenli sonraki adım: yeni adla kurmak: `wsl --install -d Ubuntu --name NovaUbuntu`.
- `wsl --unregister Ubuntu` sadece kullanıcı Ubuntu içinde kayıp veri olmadığını onaylarsa düşünülmeli; bu komut distro verisini silebilir.
