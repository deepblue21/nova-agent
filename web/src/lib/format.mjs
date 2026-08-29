// Saf yardımcılar — DOM'a bağlı olanlar (download/copy) dışında hepsi test edilebilir.

/** Sondaki slash'ları at: "http://x/v1/" → "http://x/v1" */
export const trim = (s) => (s || "").replace(/\/+$/, "");

/** Kısa, çakışma olasılığı düşük yerel id. */
export const newId = () => Date.now().toString(36) + Math.random().toString(36).slice(2, 6);

export const fmtNum = (n) => Number(n || 0).toLocaleString("tr-TR");

export const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/** ms → "820 ms" / "1.4 sn" */
export function fmtMs(ms) {
  const v = Number(ms || 0);
  return v >= 1000 ? (v / 1000).toFixed(1) + " sn" : Math.round(v) + " ms";
}

/** Bayt → "1.4 GB" (mobil ModelsScreen ile aynı okunuş). */
export function fmtBytes(bytes) {
  const b = Number(bytes || 0);
  if (b <= 0) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  const i = Math.min(units.length - 1, Math.floor(Math.log(b) / Math.log(1024)));
  const v = b / Math.pow(1024, i);
  return (i === 0 ? Math.round(v) : v.toFixed(v >= 10 ? 0 : 1)) + " " + units[i];
}

export function escapeHtml(s = "") {
  return String(s)
    .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
}

/** data URL → { mime, b64 } */
export function dataUrlParts(u) {
  const mm = /^data:([^;]+);base64,(.*)$/.exec(u) || [];
  return { mime: mm[1] || "image/png", b64: mm[2] || "" };
}

export function blobToB64(blob) {
  return new Promise((resolve, reject) => {
    const fr = new FileReader();
    fr.onloadend = () => resolve(String(fr.result).split(",")[1] || "");
    fr.onerror = reject;
    fr.readAsDataURL(blob);
  });
}

export function b64ToArrayBuffer(b64) {
  const bin = atob(String(b64 || ""));
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return bytes.buffer;
}

/** Yalnız http/https/mailto şemalarına izin verir; gerisi boş döner. */
export function safeLinkHref(value) {
  const raw = String(value || "").trim();
  if (!raw) return "";
  try {
    const u = new URL(raw, typeof window !== "undefined" ? window.location.href : "http://localhost/");
    return ["http:", "https:", "mailto:"].includes(u.protocol) ? u.href : "";
  } catch {
    return "";
  }
}

export function download(filename, content, mime = "text/plain") {
  try {
    const blob = new Blob([content], { type: mime + ";charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 2000);
  } catch (e) {}
}

/**
 * Panoya kopyala. `navigator.clipboard` YALNIZ güvenli bağlamda (https ya da
 * localhost) vardır — uygulamayı `http://100.x.y.z:8081` gibi bir tailnet/LAN
 * adresinden açtığında tanımsızdır. O yüzden gizli bir textarea + execCommand
 * yedeği var; yoksa telefondan bağlanınca kopyalama sessizce çalışmıyordu.
 *
 * @returns {Promise<boolean>} kopyalandıysa true
 */
export async function copyText(t) {
  const text = String(t == null ? "" : t);
  if (!text) return false;
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text);
      return true;
    }
  } catch (e) { /* yedeğe düş */ }
  try {
    const ta = document.createElement("textarea");
    ta.value = text;
    ta.setAttribute("readonly", "");
    ta.style.position = "fixed";
    ta.style.top = "-1000px";
    ta.style.opacity = "0";
    document.body.appendChild(ta);
    ta.select();
    ta.setSelectionRange(0, text.length);
    const ok = document.execCommand("copy");
    document.body.removeChild(ta);
    return !!ok;
  } catch (e) {
    return false;
  }
}

/** Dosya adı için güvenli gövde (Türkçe harfler korunur). */
export function safeFileName(title, fallback = "nova-sohbet") {
  return (title || fallback).replace(/[^\wçğışöüÇĞİŞÖÜ -]/gi, "").slice(0, 40).trim() || fallback;
}

export function stamp() {
  return new Date().toISOString().slice(0, 16).replace(/[:T]/g, "-");
}
