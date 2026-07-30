import React from "react";
import { Check } from "lucide-react";

/**
 * Model seçim listesi. Canlı katalogda `available:false` gelen modeller
 * (sağlayıcı anahtarı yok) listede kalır ama tıklanamaz ve nedeni yazılır —
 * "desteklenmeyeni taklit etme" ilkesi. Mobil ModelsScreen ile aynı davranış.
 */
export function ModelList({ groups, modelId, onPick, live, err, onRefresh }) {
  return (
    <>
      <div className="dd-live-bar">
        <span className={"status-dot" + (live ? " ok" : " err")} />
        <span>{live ? "Canlı liste · gateway" : "Yedek liste · gateway'e ulaşılamadı"}</span>
        <button
          className="btn sm ghost"
          style={{ marginLeft: "auto", height: 24, padding: "0 9px", fontSize: 10.5 }}
          onClick={(e) => { e.stopPropagation(); onRefresh(); }}
          title="Listeyi yenile"
        >
          Yenile
        </button>
      </div>
      {err && <div className="dd-live-err">{err}</div>}
      {groups.map((g) => (
        <div key={g.group}>
          <div className="dd-group-label">{g.group}</div>
          {g.items.map((it) => {
            const off = it.available === false;
            const Ic = it.icon;
            return (
              <div
                key={it.id}
                className={"dd-item" + (it.id === modelId ? " sel" : "") + (off ? " disabled" : "")}
                title={off ? it.reason : it.desc}
                onClick={() => { if (!off) onPick(it.id); }}
              >
                <div className="di-ic"><Ic size={16} /></div>
                <div className="di-txt">
                  <div className="t">
                    {it.name}
                    {it.tools && (
                      <span
                        className="tools-badge"
                        title={it.toolsVerified
                          ? "Model araç çağırmayı destekliyor (Ollama'ya soruldu). Seçilince ajan modu açılır."
                          : "Aile tahminine göre araç destekliyor — Ollama'ya sorulamadı."}
                      >
                        {it.toolsVerified ? "araç" : "araç?"}
                      </span>
                    )}
                  </div>
                  <div className="d">{off ? (it.reason || "kullanılamıyor") : it.desc}</div>
                </div>
                {it.id === modelId && !off && <Check size={16} className="di-check" />}
              </div>
            );
          })}
        </div>
      ))}
      {groups.length === 0 && <div className="dd-live-err">Model bulunamadı.</div>}
    </>
  );
}
