# NOVA — Faz 1: çok kullanıcı çekirdeği

Bu faz, tek-paylaşılan-token modelinden **kullanıcı bazlı** modele geçişin
temelini ekler: kimlik (OIDC/JWT + kullanıcı başına API key), dağıtık rate limit
(Redis), kalıcı sohbet geçmişi, kullanım ölçümü + kota. Çekirdek gateway
(`gateway.mjs`) artık Faz 1/Faz 2 modüllerine bağlandı. Aşağıdaki "Entegrasyon"
bölümü gateway'de bağlı olan akışı özetler; canlı DB/Redis/OIDC testi deploy ortamında
tamamlanmalıdır.

## Eklenen dosyalar

| Dosya | Rol |
|---|---|
| `gateway/migrations/001_init.sql` | Şema: orgs, users, memberships, api_keys, conversations, messages, usage_events, quotas, provider_configs |
| `gateway/migrate.mjs` | `migrations/*.sql`'i sırayla, idempotent uygular (`npm run migrate`) |
| `gateway/lib/db.mjs` | Postgres pool + `q()` / `withTx()` |
| `gateway/lib/cache.mjs` | Redis + dağıtık fixed-window `rateLimit()` |
| `gateway/lib/keys.mjs` | Saf: bearer parse, API key üret/doğrula (sha-256, sabit-zaman) |
| `gateway/lib/pricing.mjs` | Saf: token→maliyet (micro-dolar), yaklaşık token sayımı |
| `gateway/lib/auth.mjs` | `principal()` middleware: API key **veya** JWT → `req.principal` |
| `gateway/lib/usage.mjs` | `checkQuota()` + `recordUsage()` |
| `gateway/lib/persistence.mjs` | Sohbet/mesaj CRUD (kullanıcıya kapsamlı) |
| `gateway/routes/history.mjs` | `/v1/conversations` REST uçları |
| `docker-compose.yml` | + postgres:16, redis:7, one-shot migrate servisi |

## İstek yaşam döngüsü (Faz 1)

```
istemci → Caddy → gateway
  → principal()         : JWT/API key doğrula → req.principal.userId
  → rateLimit(userId)   : Redis, örnekler arası tutarlı
  → checkQuota(userId)  : bütçe aşıldıysa 402
  → route + stream      : (mevcut gateway mantığı)
  → recordUsage(...)    : token→maliyet, usage_events + quotas güncelle
  → appendMessage(...)  : sohbeti kalıcılaştır
```

## Çalıştırma

```bash
cd gateway && npm install        # pg/ioredis/jose lock'a işlenir (Docker 'npm ci' bekliyor)
cd .. && docker compose up -d --build
#   postgres + redis ayağa kalkar → migrate çalışır → gateway başlar
docker compose logs -f migrate   # "applied 001_init.sql" görmelisin
```

Bare-metal: `gateway/.env`'de `DATABASE_URL` + `REDIS_URL` ayarla, `npm run migrate`, sonra `npm start`.

## Entegrasyon — `gateway.mjs` içinde bağlı olanlar

Aşağıdaki ~20 satır, mevcut streaming mantığını değiştirmeden Faz 1'i devreye alır.

**1) Import'lar (dosya başına, diğer import'ların yanına):**
```js
import { principal } from "./lib/auth.mjs";
import { rateLimit } from "./lib/cache.mjs";
import { checkQuota, recordUsage, approxTokens } from "./lib/usage.mjs";
import { history } from "./routes/history.mjs";
import { appendMessage } from "./lib/persistence.mjs";
```

**2) Kimlik:** `/health` middleware'inden SONRA, eski tek-token auth'un yerine
(`app.use((req,res,next)=>{ ... GATEWAY_TOKEN ... })` bloğunu kaldır veya yalnız
servis-içi çağrılar için sakla) şunu ekle:
```js
app.use((req, res, next) => (req.path === "/health" ? next() : principal()(req, res, next)));
app.use(history);   // /v1/conversations
```

**3) Rate limit'i Redis'e taşı:** mevcut in-memory `hits` Map bloğunu (madde 4)
kaldır; `/v1/chat/completions` handler'ının başında:
```js
const rl = await rateLimit(req.principal.userId, RATE_MAX, RATE_WINDOW_MS);
if (!rl.allowed) { res.setHeader("Retry-After", Math.ceil(rl.retryAfterMs/1000)); return res.status(429).json({ error: "rate limit exceeded" }); }
```

**4) Kota (yönlendirmeden önce):**
```js
const quota = await checkQuota(req.principal.userId);
if (!quota.allowed) return res.status(402).json({ error: "quota exceeded", used: quota.used, limit: quota.limit });
```

**5) Ölçüm + kalıcılık (yanıt bittikten sonra).** `relay()` üretilen metni `full`
değişkeninde topluyor; yanıt sonunda kullanıcı mesajını ve asistan yanıtını
kaydet, kullanımı işle:
```js
// stream tamamlandıktan sonra (örn. relay'in çağıranında):
const tokensIn  = approxTokens(messages.map(m => typeof m.content === "string" ? m.content : "").join("\n"));
const tokensOut = approxTokens(assistantText);
await recordUsage({ userId: req.principal.userId, route: full, tokensIn, tokensOut });
if (req.body.conversation_id) {
  await appendMessage(req.body.conversation_id, { role: "user", content: lastUserText });
  await appendMessage(req.body.conversation_id, { role: "assistant", content: assistantText, model, route: full, tokens_in: tokensIn, tokens_out: tokensOut });
}
```

## API key üretme + kota verme (manuel, ilk kullanıcı için)

```js
// node ile, gateway/ içinde:
import { newApiKey } from "./lib/keys.mjs";
console.log(newApiKey());   // { full: "nv_xxxxxx_...", prefix, token_hash }
```
```sql
-- kullanıcı + key + aylık 5$ kota:
INSERT INTO users (email, name) VALUES ('you@example.com','Local User') RETURNING id;
INSERT INTO api_keys (user_id, prefix, token_hash) VALUES ('<user_id>','<prefix>','<token_hash>');
INSERT INTO quotas (subject_id, period, limit_micros, resets_at)
  VALUES ('<user_id>','month', 5000000, date_trunc('month', now()) + interval '1 month');
```
İstemciye `full` key'i bir kez ver; sunucuda yalnız hash durur.

## Bilerek TODO bırakılanlar

- **Gerçek token kullanımı:** şu an `approxTokens` (≈4 char/token) tahmini. Sağlayıcı
  yanıtlarındaki gerçek `usage` alanlarını SSE sonunda yakalamak için `relay()`
  küçük bir genişletme ister (her sağlayıcının final mesajından `usage` oku).
- **OIDC sağlayıcı kurulumu:** Keycloak/Clerk/Auth0 seç, `OIDC_*` doldur.
- **Web istemci wiring:** UI'yi JWT ile login + `/v1/conversations` geçmişine bağla;
  tarayıcı-doğrudan sağlayıcı modunu kaldır (Faz 0 güvenlik maddesi).
- **Faturalandırma:** `usage_events` → Stripe metered (opsiyonel).
- **gateway.mjs modülerleştirme:** sağlayıcı adaptörlerini `providers/`'a böl.

## Doğrulama durumu (bu fazda yapılan)

- Şema `pg-mem` ile uygulanıp temel CRUD denendi (bkz. session doğrulaması).
- Tüm yeni `.mjs` modülleri `node --check` ile parse edildi.
- `keys.mjs` ve `pricing.mjs` saf fonksiyonları test edildi.
- `gateway.mjs` entegrasyonu yapıldı: `principal()`, history router, Redis rate limit, quota, usage persistence ve conversation append hook'u bağlı.
- Canlı uçtan uca (gerçek Postgres/Redis/OIDC) test, deploy ortamında yapılmalı.
