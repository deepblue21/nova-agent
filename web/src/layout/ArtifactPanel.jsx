import React from "react";
import { CircleDot, Code2, Copy, Download, X } from "lucide-react";
import { artifactSrcDoc, artifactSandbox, artifactFileName } from "../lib/artifact.mjs";
import { copyText, download } from "../lib/format.mjs";

/** Model çıktısındaki html/svg/mermaid için yan önizleme paneli. */
export function ArtifactPanel({ artifact, onClose }) {
  if (!artifact) return null;
  return (
    <div className="artifact-panel" onClick={(e) => e.stopPropagation()}>
      <div className="ap-head">
        <div className="ap-title">
          <Code2 size={15} /> Önizleme · <span className="ap-type">{artifact.lang || artifact.type}</span>
          {artifact.error && <span className="ap-warn">hata</span>}
        </div>
        <div className="ap-actions">
          <button className="ap-btn" onClick={() => copyText(artifact.code)} title="Kopyala"><Copy size={14} /></button>
          <button
            className="ap-btn"
            onClick={() => download(artifactFileName(artifact), artifact.code, "text/plain")}
            title="İndir"
          >
            <Download size={14} />
          </button>
          <button className="ap-btn" onClick={onClose} title="Kapat"><X size={15} /></button>
        </div>
      </div>

      {artifact.type === "html" && (
        <div className="ap-browser">
          <span className="ap-dot r" /><span className="ap-dot y" /><span className="ap-dot g" />
          <div className="ap-url"><CircleDot size={11} /> localhost · canlı önizleme</div>
        </div>
      )}

      <iframe
        className="ap-frame"
        title="artifact"
        sandbox={artifactSandbox(artifact)}
        referrerPolicy="no-referrer"
        srcDoc={artifactSrcDoc(artifact)}
      />
    </div>
  );
}
