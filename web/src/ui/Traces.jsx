// Muhakeme ve araç kullanım izleri. Gerçek düşünme token'ı gelmediğinde
// geçmiş mesajlarda sahte adım gösterilmez — "desteklenmeyeni taklit etme".
import React, { useEffect, useState } from "react";
import { Brain, Check, ChevronDown, Cloud, Code2, GitBranch, Link2, Waves, Activity } from "lucide-react";
import { NovaMark } from "./NovaMark.jsx";
import { THINK_STEPS } from "../lib/constants.mjs";

export function ThinkTrace({ text, live }) {
  const [stage, setStage] = useState(0);
  const [open, setOpen] = useState(false);   // varsayılan kapalı — istenince açılır
  const hasReal = !!(text && text.trim());

  useEffect(() => {
    if (hasReal) return undefined;           // gerçek akış varsa sahte adım çalıştırma
    const iv = setInterval(() => setStage((s) => (s >= THINK_STEPS.length ? s : s + 1)), 520);
    return () => clearInterval(iv);
  }, [hasReal]);

  if (hasReal) {
    return (
      <div className="think-trace">
        <div className={"think-head" + (open ? "" : " closed")} onClick={() => setOpen((o) => !o)}>
          <Brain size={13} />
          <span className="think-title">{live ? "Düşünülüyor…" : "Muhakeme"}</span>
          <ChevronDown size={12} className="chev" />
        </div>
        {open && <div className="think-real">{text}</div>}
      </div>
    );
  }

  if (!live) return null;                    // geçmişte sahte iz gösterme

  return (
    <div className="think-trace">
      <div className="think-head"><Brain size={13} /><span className="think-title">Düşünülüyor</span></div>
      <div className="think-steps">
        {THINK_STEPS.map((s, i) => i < stage && (
          <div key={i} className={"think-step" + (i < stage - 1 ? " done" : "")} style={{ animationDelay: i * 0.05 + "s" }}>
            <span className="sc">{i < stage - 1 ? <Check size={10} /> : null}</span>{s}
          </div>
        ))}
      </div>
    </div>
  );
}

const TOOL_ICON = {
  web_search: GitBranch,
  weather_forecast: Cloud,
  calculator: Activity,
  code_run: Code2,
  fetch_url: Link2,
  subtask: NovaMark,        // alt-ajan da NOVA'dır: jenerik ikon yerine marka işareti
  synthesis: Waves,
  doc_search: Brain,
};

const TOOL_LABEL = {
  web_search: "Web araması",
  weather_forecast: "Hava tahmini",
  calculator: "Hesaplama",
  current_time: "Saat",
  code_run: "Kod sandbox",
  fetch_url: "Web sayfası",
  subtask: "Alt-ajan",
  synthesis: "Sentez",
  doc_search: "Belge araması",
};

const toolLabel = (name) =>
  TOOL_LABEL[name] || (name && name.startsWith("mcp__") ? name.replace(/^mcp__/, "").replace("__", " · ") : name);

export function ToolTrace({ tools }) {
  if (!tools || !tools.length) return null;
  return (
    <div className="tool-trace">
      <div className="tt-head"><Waves size={13} /> Araç kullanıldı</div>
      {tools.map((s, ti) => {
        const Ic = TOOL_ICON[s.name] || Check;
        return (
          <div key={ti} className="tt-step">
            <span className="tt-ic"><Ic size={12} /></span>
            <span className="tt-name">{toolLabel(s.name)}</span>
            {s.q ? <span className="tt-q">{s.q}</span> : <span />}
            {s.sources && s.sources.length > 0 && (
              <div className="tt-sources">
                {s.sources.slice(0, 4).map((src, si) => {
                  const label = `${src.n ? "[" + src.n + "] " : ""}${src.title || src.url || "Kaynak"}` +
                    `${src.score ? " · " + Math.round(src.score * 100) + "%" : ""}`;
                  return src.url
                    ? <a key={si} className="tt-source" href={src.url} target="_blank" rel="noreferrer">{label}</a>
                    : <span key={si} className="tt-source">{label}</span>;
                })}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}
