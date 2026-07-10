# NOVA — Faz 2: ölçek + ürün

Bu faz, çok kullanıcılı çekirdeğin üzerine production işletme katmanlarını ekler:
gözlemlenebilirlik, yatay ölçekleme (Kubernetes + HPA), medya için object storage,
kullanım-bazlı faturalandırma ve GPU'lu ses servisleri. Faz 2 gateway entegrasyonu
uygulandı; aşağıdaki bölüm artık `gateway.mjs` içinde bağlı olan parçaların özetidir.

## Eklenen dosyalar

| Dosya | Rol |
|---|---|
| `gateway/lib/observability.mjs` | pino yapısal log + request-ID middleware |
| `gateway/lib/metrics.mjs` | Prometheus sayaç/histogram + `/metrics` handler + saf `routeLabel` |
| `gateway/lib/storage.mjs` | S3/MinIO object storage (medya yükle/imzalı URL) |
| `gateway/routes/media.mjs` | `POST /v1/media` — base64 yerine referans + imzalı URL |
| `gateway/lib/billing.mjs` | Stripe metered: `flushUsage()` (usage_events → fatura) |
| `gateway/migrations/002_billing.sql` | billing_accounts + usage_events.reported_at |
| `k8s/*.yaml` | namespace, configmap, secret örneği, Deployment (+initContainer migrate), Service, HPA, Ingress |
| `docker-compose.faz2.yml` | MinIO + Prometheus + Grafana + faster-whisper + openedai-speech |
| `monitoring/prometheus.yml` | gateway `/metrics` scrape config |

## Entegrasyon — `gateway.mjs` içinde bağlı olanlar

```js
// import'lar
import { requestLogger, logger } from "./lib/observability.mjs";
import { metricsMiddleware, metricsHandler, llmTokens } from "./lib/metrics.mjs";
import { media } from "./routes/media.mjs";

// en başa (CORS'tan önce): her isteğe log + metrik
app.use(requestLogger());
app.use(metricsMiddleware());

// /health yanında public metrik ucu
app.get("/metrics", metricsHandler);

// principal() middleware'inden sonra (kimlik gerektirir)
app.use(media);                       // POST /v1/media

// recordUsage çağrısının yanında token sayaçlarını da artır:
llmTokens.inc({ route: full, direction: "in" },  tokensIn);
llmTokens.inc({ route: full, direction: "out" }, tokensOut);
```

Faturalandırmayı periyodik çalıştır (ayrı süreç / k8s CronJob önerilir):
```js
import { flushUsage } from "./lib/billing.mjs";
setInterval(() => flushUsage().then(r => logger.info(r, "billing flush")).catch(()=>{}), 3600_000);
```

## Yerel Faz 2 yığını

```bash
cd gateway && npm install            # yeni deps lock'a işlenir
cd .. && npm run build
docker compose -f docker-compose.yml -f docker-compose.faz2.yml up -d --build
#   + MinIO (S3 :9000, konsol :9001)  + Prometheus (:9090)  + Grafana (:3001, admin/admin)
#   + whisper (:8000)  + tts (:8001)
```
MinIO konsolundan `nova-media` bucket'ını oluştur (veya init script ekle).

## Kubernetes'e deploy

```bash
# imajı yayınla (CI bunu yapabilir)
docker build -t ghcr.io/OWNER/nova-gateway:latest gateway && docker push ghcr.io/OWNER/nova-gateway:latest

cp k8s/secret.example.yaml k8s/secret.yaml   # doldur (veya External Secrets/Vault)
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmap.yaml -f k8s/secret.yaml
kubectl apply -f k8s/gateway-deployment.yaml -f k8s/gateway-service.yaml
kubectl apply -f k8s/gateway-hpa.yaml -f k8s/ingress.yaml
```
- Deployment'taki initContainer migration'ları idempotent uygular (schema_migrations).
- HPA: CPU %70 / bellek %80'de 3→20 pod. SSE için Ingress'te `proxy-buffering: off`.
- Postgres/Redis production'da **managed** olmalı (RDS/ElastiCache vb.) — secret'taki URL'lerle.

## Gözlemlenebilirlik

- `/metrics`: `nova_http_requests_total`, `nova_http_request_duration_seconds`, `nova_llm_tokens_total` + Node default metrikleri.
- Prometheus gateway'i kazır; Grafana ile dashboard. Hata izleme için Sentry SDK eklenebilir.
- Her istek `reqId` ile loglanır (yapısal JSON); ingress→gateway boyunca `x-request-id` taşınır.

## Bilerek TODO bırakılanlar

- **Gerçek sağlayıcı token kullanımı** (hâlâ Faz 1'deki tahmin) → SSE `usage` alanlarını yakala.
- **Ses pipeline'ı**: `/stt /tts`'i kuyruğa (BullMQ) al; uzun transkripsiyonu async yap.
- **Web istemci**: medya yüklemeyi `/v1/media`'ya geçir (büyük base64 yerine referans).
- **Sentry/OTel trace** kurulumunu tamamla; Grafana dashboard'larını sürümle.
- **gateway.mjs modülerleştirme**: sağlayıcı adaptörlerini `gateway/providers/`'a böl.

## Doğrulama durumu (bu fazda)

- 002 migration'ı pg-mem'de 001'in üzerine uygulandı (billing_accounts + reported_at).
- Yeni `.mjs` modülleri `node --check`'ten geçti; `routeLabel`/`mediaKey`/`microsToCents` saf testleri geçti.
- Tüm k8s manifestleri + `docker-compose.faz2.yml` + `prometheus.yml` geçerli YAML.
- `gateway.mjs` entegrasyonu yapıldı: `/metrics`, request logging, media router, LLM token metrikleri ve billing flush scheduler bağlı.
- Production multi-user smoke: `/health=200`, `/metrics=200`, token'sız `/v1/models=401`.
- Canlı k8s/S3/Stripe testi deploy ortamında yapılmalı.
