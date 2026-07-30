// Sohbet dışa aktarma (MD / JSON / PDF) + paylaşılabilir yerel link.
// Paylaşım linki sunucuya hiçbir şey yüklemez: içerik URL hash'inde taşınır,
// görseller boyut nedeniyle dışarıda bırakılır ve bu durum alıcıya yazılır.
import { newId, escapeHtml, download, safeFileName, stamp } from "./format.mjs";

export const SHARE_HASH = "#nova-share=";
export const MAX_SHARE_CHARS = 150000;

export function b64urlEncodeText(text) {
  const bytes = new TextEncoder().encode(text);
  let bin = "";
  for (let i = 0; i < bytes.length; i += 0x8000) bin += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export function b64urlDecodeText(text) {
  const b64 = text.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((text.length + 3) % 4);
  const bin = atob(b64);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return new TextDecoder().decode(bytes);
}

export function sharePayload(conv) {
  const messages = (conv.messages || []).map((m) => ({
    role: m.role === "user" ? "user" : "assistant",
    content: m.content || "",
    ...(m.route ? { route: m.route } : {}),
    ...(m.stats && m.stats.model ? { stats: { model: m.stats.model } } : {}),
    ...(m.images && m.images.length ? { omittedImages: m.images.length } : {}),
  }));
  return {
    v: 1,
    app: "nova-agent",
    title: conv.title || "NOVA Sohbet",
    exportedAt: new Date().toISOString(),
    messages,
  };
}

export function convFromSharePayload(payload) {
  if (!payload || payload.app !== "nova-agent" || !Array.isArray(payload.messages)) return null;
  const messages = payload.messages
    .filter((m) => m && (m.role === "user" || m.role === "assistant"))
    .map((m) => {
      const omitted = Number(m.omittedImages) || 0;
      const note = omitted ? `\n\n[${omitted} görsel paylaşım linkine eklenmedi.]` : "";
      return {
        role: m.role,
        content: String(m.content || "") + note,
        ...(m.route ? { route: String(m.route) } : {}),
        ...(m.stats && m.stats.model ? { stats: { model: String(m.stats.model) } } : {}),
      };
    });
  if (!messages.length) return null;
  return {
    id: newId(),
    title: ("Paylaşılan · " + String(payload.title || "NOVA Sohbet")).slice(0, 80),
    messages,
    imported: true,
    updatedAt: Date.now(),
  };
}

export function readSharePayloadFromHash() {
  if (typeof window === "undefined" || !window.location.hash.startsWith(SHARE_HASH)) return null;
  try {
    return JSON.parse(b64urlDecodeText(window.location.hash.slice(SHARE_HASH.length)));
  } catch (e) {
    return null;
  }
}

export function clearShareHash() {
  try {
    if (window.location.hash.startsWith(SHARE_HASH)) {
      window.history.replaceState(null, "", window.location.pathname + window.location.search);
    }
  } catch (e) {}
}

/** Yazdırma penceresi üzerinden PDF (tarayıcının "PDF olarak kaydet"i). */
export function printChat(conv, safe, ts) {
  const rows = (conv.messages || [])
    .map((m, i) => {
      const who = m.role === "user" ? "Sen" : "NOVA";
      const route = m.route ? `<span class="route">${escapeHtml(m.route)}</span>` : "";
      const imgs = (m.images || [])
        .map((src) => `<img src="${escapeHtml(src)}" alt="ek ${i + 1}" />`)
        .join("");
      return `<section class="msg ${m.role === "user" ? "user" : "assistant"}"><h2>${who}${route}</h2>` +
        `${imgs ? `<div class="imgs">${imgs}</div>` : ""}<pre>${escapeHtml(m.content || "")}</pre></section>`;
    })
    .join("");
  const html = `<!doctype html><html><head><meta charset="utf-8"><title>${escapeHtml(conv.title || "NOVA Sohbet")}</title><style>
      @page{margin:18mm;}*{box-sizing:border-box;}body{margin:0;color:#17202a;background:#fff;font:14px/1.55 system-ui,-apple-system,Segoe UI,sans-serif;}
      header{border-bottom:1px solid #d8dee8;margin-bottom:18px;padding-bottom:12px;}h1{font-size:22px;margin:0 0 6px;}header p{margin:0;color:#687386;font-size:12px;}
      .msg{break-inside:avoid;border:1px solid #e4e8f0;border-radius:10px;padding:14px;margin:0 0 14px;background:#fff;}
      .msg.user{background:#f7fbff}.msg h2{font-size:13px;margin:0 0 8px;color:#0f172a;display:flex;gap:8px;align-items:center}.route{font-size:10px;color:#64748b;font-weight:500}
      pre{white-space:pre-wrap;word-break:break-word;margin:0;font:13px/1.55 ui-monospace,SFMono-Regular,Consolas,monospace}.imgs{display:flex;gap:8px;flex-wrap:wrap;margin-bottom:10px}.imgs img{max-width:220px;max-height:180px;border:1px solid #d8dee8;border-radius:8px}
    </style></head><body><header><h1>${escapeHtml(conv.title || "NOVA Sohbet")}</h1><p>NOVA sohbet dışa aktarımı · ${escapeHtml(ts)} · ${escapeHtml(safe)}.pdf</p></header>${rows || "<p>Boş sohbet.</p>"}<script>window.onload=()=>setTimeout(()=>window.print(),120);<\/script></body></html>`;
  const w = window.open("", "_blank", "width=900,height=1000");
  if (!w) {
    download(`${safe}-${ts}.html`, html, "text/html");
    return;
  }
  try { w.opener = null; } catch (e) {}
  w.document.open();
  w.document.write(html);
  w.document.close();
}

/** fmt: "md" | "json" | "pdf" */
export function exportChat(conv, fmt) {
  if (!conv) return;
  const ts = stamp();
  const safe = safeFileName(conv.title);
  if (fmt === "json") {
    download(`${safe}-${ts}.json`, JSON.stringify({ title: conv.title, messages: conv.messages }, null, 2), "application/json");
    return;
  }
  if (fmt === "pdf") {
    printChat(conv, safe, ts);
    return;
  }
  const md =
    `# ${conv.title || "NOVA Sohbet"}\n\n` +
    (conv.messages || [])
      .map((m) => `**${m.role === "user" ? "Sen" : "NOVA"}**${m.route ? " · `" + m.route + "`" : ""}:\n\n${m.content || ""}`)
      .join("\n\n---\n\n");
  download(`${safe}-${ts}.md`, md, "text/markdown");
}

/** Panoya paylaşılabilir link kopyalar; çok büyükse JSON indirir. Not döner. */
export async function shareChat(conv) {
  if (!conv) return "";
  const ts = stamp();
  const safe = safeFileName(conv.title);
  const payload = sharePayload(conv);
  const encoded = b64urlEncodeText(JSON.stringify(payload));
  if (encoded.length > MAX_SHARE_CHARS) {
    download(`${safe}-${ts}-share.json`, JSON.stringify(payload, null, 2), "application/json");
    return "JSON";
  }
  const url = window.location.href.split("#")[0] + SHARE_HASH + encoded;
  try {
    await navigator.clipboard.writeText(url);
    return "Kopyalandı";
  } catch (e) {
    try {
      window.prompt("Paylaşılabilir local link", url);
      return "Hazır";
    } catch (err) {
      return "";
    }
  }
}
