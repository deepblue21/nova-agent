// Semantik ikon sözlüğü. Anahtarlar design/nova-tokens.json içindeki `icons`
// bölümüyle aynıdır; Android tarafı aynı anahtarlar için Material karşılıklarını
// kullanır. Böylece "web'de dalga, mobilde zincir" gibi kaymalar olmaz.
//
// Ağaç sarsımı için ikonlar açıkça import edilir; geliştirme modunda kayıt
// defteri token dosyasıyla karşılaştırılır ve eksik/fazla anahtar uyarı verir.
import {
  LayoutDashboard, ListChecks, MessageSquare, Boxes, Mic, Settings,
  ShieldCheck, Smartphone, Link2, Cloud, Cpu, Waves, Workflow, Brain, Square,
  Send, History, Trash2, Download, Check, Pause, Play, X, RotateCcw,
} from "lucide-react";
import { ICON_NAMES } from "../design/tokens.generated.mjs";
import { NovaMark } from "../ui/NovaMark.jsx";

export const Icons = {
  // Marka: hazır bir lucide ikonu DEĞİL — Pulse Aperture işaretinin kendisi.
  // Uygulamadaki tek marka görseli budur; jenerik "parıltı" ikonu kullanılmaz.
  brand: NovaMark,
  control: LayoutDashboard,
  tasks: ListChecks,
  chat: MessageSquare,
  models: Boxes,
  voice: Mic,
  settings: Settings,
  shield: ShieldCheck,
  local: Smartphone,
  gateway: Link2,
  cloud: Cloud,
  cpu: Cpu,
  agent: Waves,
  team: Workflow,
  think: Brain,
  stop: Square,
  send: Send,
  history: History,
  delete: Trash2,
  download: Download,
  check: Check,
  pause: Pause,
  resume: Play,
  cancel: X,
  retry: RotateCcw,
};

if (import.meta.env && import.meta.env.DEV) {
  const declared = Object.keys(ICON_NAMES);
  const bound = Object.keys(Icons);
  const missing = declared.filter((k) => !bound.includes(k));
  const extra = bound.filter((k) => !declared.includes(k));
  // `brand` özel bileşendir, lucide displayName'i yoktur — ad denetiminden muaf.
  const wrong = declared
    .filter((k) => k !== "brand")
    .filter((k) => Icons[k] && Icons[k].displayName && Icons[k].displayName !== ICON_NAMES[k]);
  if (missing.length || extra.length || wrong.length) {
    // eslint-disable-next-line no-console
    console.warn(
      "[nova] ikon sözlüğü token dosyasıyla uyuşmuyor →",
      { eksik: missing, fazla: extra, adUyusmazligi: wrong },
      "design/nova-tokens.json ile web/src/lib/icons.mjs birlikte güncellenmeli.",
    );
  }
}

/** Gezinme hedefi id'si → ikon bileşeni. */
export const navIcon = (iconKey) => Icons[iconKey] || Icons.brand;
