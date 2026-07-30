// Artefakt (canvas) önizlemesi: model çıktısındaki html/svg/mermaid bloklarını
// yan panelde canlı gösterir.
import { escapeHtml } from "./format.mjs";

/** Önizlenebilir kod dilleri → artefakt türü. */
export const PREVIEWABLE = { html: "html", svg: "svg", xml: "svg", mermaid: "mermaid", mmd: "mermaid" };

let mermaidP;
export async function loadMermaid() {
  if (!mermaidP) {
    mermaidP = import("mermaid").then((m) => {
      const api = m.default || m;
      api.initialize({ startOnLoad: false, theme: "dark", securityLevel: "strict" });
      return api;
    });
  }
  return mermaidP;
}

/** Mermaid'i parent uygulamada SVG'ye çevirip artefakta gömer. */
export async function prepareArtifact(next) {
  if (!next || next.type !== "mermaid") return next;
  try {
    const id = "nova-mermaid-" + Date.now().toString(36) + "-" + Math.random().toString(36).slice(2, 7);
    const mermaid = await loadMermaid();
    const out = await mermaid.render(id, next.code);
    return { ...next, renderedSvg: out.svg };
  } catch (e) {
    return { ...next, error: (e && e.message) || String(e) };
  }
}

const SHELL = (body, style) =>
  `<!doctype html><html><head><meta charset="utf-8"><style>${style}</style></head><body>${body}</body></html>`;

export function artifactSrcDoc(a) {
  if (!a) return "";
  const code = String(a.code || "");
  const empty = !code.trim() && !a.renderedSvg;

  if (empty) {
    return SHELL(
      "<div>Önizlenecek içerik yok.</div>",
      "body{margin:0;background:#0d1326;color:#d7f6ff;font:13px/1.55 ui-monospace,SFMono-Regular,Consolas,monospace;display:grid;place-items:center;min-height:100vh;padding:24px;}div{border:1px solid rgba(255,255,255,.12);border-radius:12px;padding:16px 18px;background:rgba(255,255,255,.05);}",
    );
  }

  if (a.type === "mermaid") {
    if (a.renderedSvg) {
      return SHELL(
        a.renderedSvg,
        "body{margin:0;background:#0d1326;display:flex;align-items:center;justify-content:center;min-height:100vh;padding:24px;}svg{max-width:100%;height:auto;}",
      );
    }
    return SHELL(
      `<strong>Mermaid render edilemedi${a.error ? ": " + escapeHtml(a.error) : ""}</strong><pre>${escapeHtml(code)}</pre>`,
      "body{margin:0;background:#0d1326;color:#d7f6ff;font:13px/1.55 ui-monospace,SFMono-Regular,Consolas,monospace;padding:20px;}strong{display:block;color:#ffb86b;margin-bottom:12px;}pre{white-space:pre-wrap;word-break:break-word;background:rgba(255,255,255,.06);border:1px solid rgba(255,255,255,.12);border-radius:10px;padding:14px;}",
    );
  }

  if (a.type === "svg") {
    return SHELL(
      code,
      "body{margin:0;background:#fff;display:flex;align-items:center;justify-content:center;min-height:100vh;padding:18px;overflow:auto;}svg{max-width:100%;height:auto;}",
    );
  }

  if (/^\s*(<!doctype|<html[\s>])/i.test(code)) return code;

  return SHELL(
    code,
    "body{margin:0;min-height:100vh;padding:18px;background:#fff;color:#111;font:14px/1.5 system-ui,-apple-system,Segoe UI,sans-serif;}",
  );
}

/**
 * HTML önizlemesi etkileşimli olsun diye script'e izin verilir — ama ASLA
 * allow-same-origin verilmez: iframe null origin'de çalışır, uygulamanın
 * deposuna, çerezlerine veya gateway belirtecine erişemez. SVG/Mermaid kilitli.
 */
export const artifactSandbox = (a) => (a && a.type === "html" ? "allow-scripts" : "");

export const artifactFileName = (a) =>
  "nova-artifact." + (a.type === "mermaid" ? "mmd" : a.type === "svg" ? "svg" : "html");
