// Hafif, bağımlılıksız markdown render: başlık, liste, alıntı, satıriçi
// (kod/kalın/italik/link) ve kopyalanabilir + önizlenebilir kod blokları.
import React, { useState } from "react";
import { Check, Copy, Eye } from "lucide-react";
import { safeLinkHref } from "../lib/format.mjs";
import { PREVIEWABLE } from "../lib/artifact.mjs";

function renderInline(text, kb) {
  const nodes = [];
  let last = 0;
  let i = 0;
  const re = /(`[^`]+`)|(\*\*[^*]+\*\*)|(\*[^*\n]+\*)|(\[[^\]]+\]\([^)]+\))/g;
  let m;
  while ((m = re.exec(text))) {
    if (m.index > last) nodes.push(text.slice(last, m.index));
    const tok = m[0];
    if (tok.startsWith("`")) nodes.push(<code className="md-ic" key={kb + i}>{tok.slice(1, -1)}</code>);
    else if (tok.startsWith("**")) nodes.push(<strong key={kb + i}>{tok.slice(2, -2)}</strong>);
    else if (tok.startsWith("*")) nodes.push(<em key={kb + i}>{tok.slice(1, -1)}</em>);
    else {
      const mm = /\[([^\]]+)\]\(([^)]+)\)/.exec(tok);
      const href = safeLinkHref(mm[2]);
      nodes.push(
        href
          ? <a key={kb + i} href={href} target="_blank" rel="noreferrer">{mm[1]}</a>
          : <span key={kb + i}>{mm[1]}</span>,
      );
    }
    last = m.index + tok.length;
    i++;
  }
  if (last < text.length) nodes.push(text.slice(last));
  return nodes;
}

export function CodeBlock({ lang, code, onArtifact }) {
  const [copied, setCopied] = useState(false);
  const copy = () => {
    try {
      navigator.clipboard && navigator.clipboard.writeText(code);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch (e) {}
  };
  const previewable = PREVIEWABLE[(lang || "").toLowerCase()];
  return (
    <div className="code-block">
      <div className="code-bar">
        <span className="lang">{lang || "kod"}</span>
        <div style={{ display: "flex", gap: 4 }}>
          {previewable && onArtifact && (
            <button className="code-copy" onClick={() => onArtifact({ type: previewable, code, lang })}>
              <Eye size={12} /> Önizle
            </button>
          )}
          <button className="code-copy" onClick={copy}>
            {copied ? <><Check size={12} /> Kopyalandı</> : <><Copy size={12} /> Kopyala</>}
          </button>
        </div>
      </div>
      <pre><code>{code}</code></pre>
    </div>
  );
}

export function Markdown({ text, onArtifact }) {
  const out = [];
  const re = /```(\w*)\n?([\s\S]*?)```/g;
  let last = 0;
  let m;
  let k = 0;

  const pushText = (seg, key) => {
    seg.split(/\n{2,}/).forEach((blk, bi) => {
      const t = blk.trim();
      if (!t) return;
      const kb = key + "-" + bi + "-";
      const h = /^(#{1,4})\s+(.*)$/.exec(t);
      if (h) {
        const Tag = "h" + h[1].length;
        out.push(React.createElement(Tag, { key: kb + "h", className: "md" }, renderInline(h[2], kb)));
        return;
      }
      const lines = t.split("\n");
      if (lines.every((l) => /^\s*[-*]\s+/.test(l))) {
        out.push(
          <ul className="md" key={kb + "ul"}>
            {lines.map((l, li) => <li key={li}>{renderInline(l.replace(/^\s*[-*]\s+/, ""), kb + li)}</li>)}
          </ul>,
        );
        return;
      }
      if (lines.every((l) => /^\s*\d+\.\s+/.test(l))) {
        out.push(
          <ol className="md" key={kb + "ol"}>
            {lines.map((l, li) => <li key={li}>{renderInline(l.replace(/^\s*\d+\.\s+/, ""), kb + li)}</li>)}
          </ol>,
        );
        return;
      }
      if (lines.every((l) => /^\s*>\s?/.test(l))) {
        out.push(
          <blockquote className="md" key={kb + "bq"}>
            {renderInline(lines.map((l) => l.replace(/^\s*>\s?/, "")).join(" "), kb)}
          </blockquote>,
        );
        return;
      }
      const parts = [];
      lines.forEach((l, li) => {
        if (li) parts.push(<br key={"br" + li} />);
        parts.push(...renderInline(l, kb + li));
      });
      out.push(<p className="md" key={kb + "p"}>{parts}</p>);
    });
  };

  while ((m = re.exec(text))) {
    if (m.index > last) pushText(text.slice(last, m.index), "t" + k);
    out.push(<CodeBlock key={"c" + k} lang={m[1]} code={m[2].replace(/\n$/, "")} onArtifact={onArtifact} />);
    last = m.index + m[0].length;
    k++;
  }
  if (last < text.length) pushText(text.slice(last), "t" + k);

  return <div className="md">{out}</div>;
}
