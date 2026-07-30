# Claude Design Brief — NOVA / Project Horus

Sıfırdan arayüz tasarımı için Claude Design'a (claude.ai/design) verilecek aşamalı
komut seti. Brief İngilizce (araç böyle daha isabetli çalışır), ekran metinleri Türkçe.

## Nasıl kullanılır (TR)

1. **claude.ai/design** aç (Pro/Max/Team planı gerekir; Claude Desktop kenar çubuğunda da var).
2. Yeni proje: **"NOVA Horus Redesign"**.
3. Bağlam ekle (ne kadar çok, o kadar iyi):
   - Mevcut uygulamanın emülatör ekran görüntüleri ("şu an böyle görünüyor" referansı).
   - İstersen repoyu bağla (GitHub) veya Claude Code'dan `/design-sync` — ama kimlik
     sıfırdan istendiği için mevcut stile bağlanmasın; brief bunu zaten söylüyor.
4. **Aşama 1** promptunu yapıştır → 3 görsel yön çıkarır → birini seç.
5. **Aşama 2-4** promptlarını sırayla yapıştır (her aşama öncekinin üstüne kurulur).
6. İnce ayar kuralı: küçük/nokta değişiklik → tuval üstünde **inline yorum**
   ("Make this button padding larger"); yapısal değişiklik → **chat**
   ("Move the policy selector above the status card"). Belirsiz kalma:
   "bu olmamış" yerine "bu kartın iç boşluğunu 8px'e düşür" gibi somut yaz.
7. Farklı bir yönü denemeden önce: "Save what we have and try a completely
   different approach" — mevcut sürüm kaybolmaz.
8. Bitince **Export → Handoff to Claude Code** ile paket al; uygulamaya dökme işini
   o paketle bana (Cowork/Claude Code) getir.

---

## Aşama 1 — Kick-off: bağlam + 3 görsel yön

Aşağıyı olduğu gibi yapıştır:

```
I'm redesigning the UI of my self-hosted AI assistant "NOVA / Project Horus" from
scratch, for two platforms: a native Android app and a web app. In this first step
I only want visual direction exploration — no full screen sets yet.

## What the product is

NOVA is a privacy-first, self-hosted AI assistant. One Node gateway routes chats to
cloud providers (Anthropic/Gemini/OpenAI), a local PC LLM (Ollama), or an agent
layer. Two clients:

- **Android app ("NOVA Horus")** — the flagship. An offline-first agentic AI that
  runs LLMs *on the phone* (downloadable on-device models), can hand work off to
  the PC with explicit consent, and remote-controls long-running agent tasks on
  the PC. Works fully offline in airplane mode.
- **Web app** — a chat + voice UI for the gateway with agent tools (web search,
  RAG over documents), team mode, artifacts preview, and usage/cost tracking.

Audience: technically-minded self-hosters and power users who care about privacy
and control; they are not designers and value clarity over decoration. All UI copy
must be **Turkish** (use real Turkish strings in the mockups; I'll provide
corrections). Design language annotations can stay English.

## Product principles the design must express

1. **Honesty** — unsupported features are shown disabled with a reason, never faked.
2. **No silent handoff** — a prompt never leaves the phone without explicit consent;
   consent moments are first-class UI (cards/dialogs that explain *why*).
3. **Privacy by default** — on-device is the default story; sensitive prompts stay
   on the phone even in hybrid mode. The UI should make "where did this run?"
   glanceable at all times (a per-answer target badge: Telefon / PC / Bulut).
4. **Offline-first** — the app must feel complete with zero connectivity.
5. **Calm technical confidence** — dense information (model sizes, tok/s, task
   timelines) presented with strong hierarchy, not dashboard clutter.

## Constraints

- Dark theme first, with a light variant later. The app offers user-selectable
  accent themes (3 accents) — the palette is yours to define, we are NOT keeping
  the old colors.
- Android: Material-adjacent but with its own identity; must survive system font
  scale 1.3 (no clipped labels, composer stays above the keyboard); TalkBack
  accessibility; primary actions reachable one-handed.
- Web: responsive, desktop-first down to mobile; WCAG AA contrast; keyboard
  navigable.
- Performance: avoid heavy blur/glass and big shadow stacks (low-end phones and
  emulators jank); prefer flat surfaces and cheap effects.

## What I want in THIS step

Give me **3 clearly distinct visual directions**. For each direction:
- a style tile (palette incl. 3 accent options, typography, radius/spacing feel,
  iconography style, one signature visual motif),
- the **Android Chat screen** (streaming answer with a collapsible "düşünme"
  (thinking) section, a code block card with a copy button, a "Telefon" target
  badge, and a consent card offering PC handoff after a local failure),
- the **Android Control screen** (execution-policy selector with 4 modes:
  "PC / Gateway", "Yerel öncelikli", "Çevrimdışı", "Hibrit"; a hybrid-rules card;
  a gateway status row showing "PC hazır"),
- 2–3 sentences on the rationale and how it expresses the principles above.

Keep all three honest to the constraints. Don't produce other screens yet.
```

Yön seçtikten sonra tek cümle yeter: *"Direction N wins — apply it to everything
from now on."*

---

## Aşama 2 — Android ekran seti

```
Using the chosen direction, design the full Android screen set for NOVA Horus.
All UI copy in Turkish. Current IA is bottom navigation with 4 sections —
Kontrol / İşler / Sohbet / Modeller — plus Voice opened from the Chat top bar and
Settings as a sheet. You may propose IA improvements if clearly justified, but
keep these five surfaces.

Screens and required states:

1. **Sohbet (Chat)** — streaming markdown; collapsible "Düşünme" section; fenced
   code blocks as cards with per-block "Kopyala"; per-answer target badge
   (Telefon/PC); "PC ajanına devret" chip (hidden in offline mode); history chip
   opening a **Geçmiş panel** (search, open, delete, share-as-Markdown, multiple
   conversations). States: empty (first run), local-model generating, local
   failure → consent card (reason + "PC'ye gönder (izinli)" / "Yerelde tekrar
   dene"), offline mode (handoff affordances gone).
2. **Kontrol (Control)** — 4-mode policy selector with plain-language descriptions;
   hybrid rules card (uzun istem → PC, düşük pil → PC, aşırı ısınma → PC, hassas
   istem telefonda kalır; "her seferinde sor" vs "otomatik devret" toggle);
   gateway status (PC hazır / hata + neden). States: gateway unreachable, offline
   mode selected without an installed model (honest setup message + CTA).
3. **Modeller (Models)** — recommended-model banner (device-RAM based, one-tap
   install/activate); model cards: name, family, quantization, size, license,
   "kapılı" (gated) badge, suitability chip (Rahat/Sınırlı/Riskli), last
   performance (yükleme süresi, ~tok/sn); download states: progress %, paused →
   resume, SHA-256 doğrulanıyor, kuruldu, dürüst hata; storage summary (model
   klasörü boyutu + boş alan); "Yerel araçlar (deneysel)" toggle; gated models
   point to the HF token field in Settings.
4. **İşler (Tasks)** — task list with status chips (kuyrukta, çalışıyor,
   duraklatıldı, tamamlandı, hata, işlem bekliyor); create-task input; task detail:
   live event timeline (SSE replay), pause/resume/cancel controls, and **risk
   confirmation dialogs** (R2/R3: a risky agent action needs explicit approval —
   make this moment unmistakable but not alarmist).
5. **Ses (Voice)** — full-screen voice conversation: mic states (dinliyor /
   düşünüyor / konuşuyor), live transcript, a note when offline recognition is
   preferred, TTS playback control.
6. **Ayarlar (Settings)** — gateway address + masked token (accessible editing),
   Hugging Face token, persona (system-instruction) editor, data management
   (sohbet geçmişi/notlar/metrikleri temizle; isteğe bağlı indirilen modeller —
   with confirmations), accent theme picker (3 accents), app info.
7. **First-run** — a short 3-step onboarding that sets the privacy story and ends
   in either "model indir" or "Gateway bağla".

Also show the font-scale 1.3 variant of Chat and Tasks to prove nothing breaks.
```

---

## Aşama 3 — Web ekran seti

```
Same direction, now the web app (desktop-first responsive, Turkish UI copy):

1. **Ana sohbet** — provider/model picker ("sağlayıcı/model" + "auto" with effort
   levels hızlı/dengeli/derin/maks), streaming chat, artifacts side panel
   (HTML/SVG/Mermaid preview + download), source badges on RAG/web-search
   answers, image attach, voice mode entry, export (Markdown/JSON/PDF).
2. **Ajan/ekip modu** — live tool-use progress (web araması, belge arama,
   hesaplayıcı), team-mode parallel progress, agent run history list + detail.
3. **Bilgi tabanı (RAG)** — document upload/list/delete, workspace scope.
4. **Zamanlanmış görevler** — list + create/edit schedule.
5. **Çalışma alanları** — members + 3 roles (admin/editör/izleyici).
6. **Model karşılaştırma (eval)** — one prompt across N models: outputs side by
   side with latency/token/cost.
7. **Kullanım paneli** — per-model cost/usage.
8. **Ayarlar** — gateway token or Keycloak sign-in, API keys, MCP tools list.

Keep density comfortable: this is a power tool, not a marketing site. Show one
mobile-width variant of the main chat.
```

---

## Aşama 4 — Design system + Claude Code handoff

```
Consolidate everything into a design system I can hand to engineering:

- Tokens: color (dark + light, 3 accents), type scale, spacing, radius, elevation.
- Component inventory with states: buttons, chips/badges (target badge, status
  chips, suitability chips), cards (model card, consent card, hybrid-rules card),
  inputs (masked token field), dialogs (risk confirmation), navigation (Android
  bottom nav, web sidebar), timeline item, progress/download indicators, toasts.
- Accessibility notes per component (contrast, touch target, TalkBack labels).
- Then prepare the **Claude Code handoff bundle** so the design can be implemented
  in the existing codebase (Android: Kotlin + Jetpack Compose, Material 3 base;
  Web: React + Vite single-page app).
```

---

## İpucu sözlüğü (ince ayar için hazır cümleler)

| İstediğin | Claude Design'a yaz |
| --- | --- |
| 2-3 alternatif görmek | "Show me 2–3 alternative layouts for this screen." |
| Boşluk/yoğunluk | "Tighten the vertical spacing in this list to feel denser." |
| Erişilebilirlik denetimi | "Review this screen for contrast and accessibility issues." |
| Bir öğeyi değiştirmek | (öğeye inline yorum) "Change this to a dropdown." |
| Yönü sıfırlamak | "Save what we have and try a completely different approach." |

Kaynaklar: Anthropic duyurusu ve resmi kullanım kılavuzu (Temmuz 2026 itibarıyla;
Claude Design beta'dadır, kullanım plan limitlerinden düşer).
