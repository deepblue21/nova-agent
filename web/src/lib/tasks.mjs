// Mobil görev kontrol düzlemi — Android `MobileTaskModels.kt` ile birebir aynı
// durum kümesi ve Türkçe etiketler. Web ve telefon aynı görevleri aynı
// kelimelerle gösterir; kopma buradan başlamasın.

export const TASK_STATUS = [
  "queued", "routing", "observing", "planning", "executing", "verifying",
  "waiting_for_confirmation", "waiting_for_device", "waiting_for_compute",
  "paused", "completed", "failed", "cancelled",
];

const LABELS = {
  queued: "Sıraya alındı",
  routing: "Yönlendiriliyor",
  observing: "Cihaz inceleniyor",
  planning: "Plan hazırlanıyor",
  executing: "Eylem uygulanıyor",
  verifying: "Sonuç doğrulanıyor",
  waiting_for_confirmation: "Onay bekleniyor",
  waiting_for_device: "Telefon bekleniyor",
  waiting_for_compute: "PC bekleniyor",
  paused: "Duraklatıldı",
  completed: "Tamamlandı",
  failed: "Başarısız",
  cancelled: "İptal edildi",
};

const TONES = {
  queued: "muted",
  routing: "warning",
  observing: "warning",
  planning: "warning",
  executing: "accent",
  verifying: "accent",
  waiting_for_confirmation: "warning",
  waiting_for_device: "warning",
  waiting_for_compute: "warning",
  paused: "muted",
  completed: "success",
  failed: "danger",
  cancelled: "muted",
};

const TERMINAL = new Set(["completed", "failed", "cancelled"]);

export const normalizeStatus = (v) => String(v || "").toLowerCase();
export const statusLabel = (v) => LABELS[normalizeStatus(v)] || "Görev güncellendi";
export const statusTone = (v) => TONES[normalizeStatus(v)] || "muted";
export const isTerminal = (v) => TERMINAL.has(normalizeStatus(v));
export const isRunning = (v) => !isTerminal(v) && normalizeStatus(v) !== "paused";

/** Olay tipi → kullanıcı etiketi (Android `MobileTaskEvent.userLabel` eşleniği). */
export function eventLabel(event) {
  if (!event) return "Görev güncellendi";
  const status = normalizeStatus(event.status || (event.data && event.data.status));
  if (LABELS[status]) return LABELS[status];
  switch (event.type) {
    case "task.created": return "Sıraya alındı";
    case "worker.claimed": return "Görev alındı";
    case "worker.executing":
    case "worker.running": return "Eylem uygulanıyor";
    case "worker.observing": return "Cihaz inceleniyor";
    case "worker.completed": return "Tamamlandı";
    case "confirmation.requested": return "Onay bekleniyor";
    case "confirmation.approved": return "Onaylandı";
    case "confirmation.rejected": return "Reddedildi";
    default: return "Görev güncellendi";
  }
}

/**
 * Olay özeti. Ham durum kodu ("EXECUTING") ya da tip adı gelirse kullanıcıya
 * onu göstermeyiz — Android'deki `userSummary` ile aynı geri düşüş.
 */
export function eventSummary(event, taskPrompt) {
  if (!event) return "";
  const raw = String(event.summary || "").trim();
  const looksLikeCode = !raw || raw === event.type || LABELS[normalizeStatus(raw)];
  if (!looksLikeCode) return raw;
  if (event.type === "confirmation.requested" && taskPrompt && taskPrompt.trim()) return taskPrompt.trim();
  return eventLabel(event);
}

/** Olaydaki bekleyen onay bilgisini çıkarır (varsa). */
export function pendingConfirmation(event) {
  if (!event || event.type !== "confirmation.requested") return null;
  const d = event.data || event;
  const id = d.confirmation_id || d.confirmationId;
  if (!id) return null;
  return {
    id,
    riskLevel: d.risk_level || d.riskLevel || "orta",
    actionSummary: d.action || d.action_summary || "",
  };
}

/** Görevin komut kabul edip etmediği (duraklat/sürdür/iptal düğmeleri). */
export function availableCommands(status) {
  const s = normalizeStatus(status);
  if (isTerminal(s)) return [];
  if (s === "paused") return ["resume", "cancel"];
  return ["pause", "cancel"];
}

export const COMMAND_LABEL = {
  pause: "Duraklat",
  resume: "Sürdür",
  cancel: "İptal et",
  steer: "Yönlendir",
};
