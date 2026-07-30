// Alt dock: Sesli/Sohbet kipi, model seçici, çaba kademesi, düşünme/ajan/takım
// anahtarları. Yalnız sohbet ve ses görünümlerinde gösterilir.
import React from "react";
import { ChevronDown } from "lucide-react";
import { EFFORTS } from "../lib/constants.mjs";
import { Icons } from "../lib/icons.mjs";
import { ModelList } from "../ui/ModelList.jsx";

export function Dock({
  view, onView, curItem, models, modelId, onPickModel,
  live, modelsErr, onRefreshModels,
  openDD, setOpenDD, effort, onEffort, reasoning, onReasoning,
  agentMode, onAgentMode, teamMode, onTeamMode,
}) {
  const CurIcon = curItem.icon;
  return (
    <div className="dock" onClick={(e) => e.stopPropagation()}>
      <div className="dock-group">
        <button className={"mode-tab" + (view === "ses" ? " on" : "")} onClick={() => onView("ses")}>
          <Icons.voice size={15} /> Sesli
        </button>
        <button className={"mode-tab" + (view === "sohbet" ? " on" : "")} onClick={() => onView("sohbet")}>
          <Icons.chat size={15} /> Sohbet
        </button>
      </div>

      <div className="dock-group selector">
        <button
          className={"sel-btn" + (openDD === "model" ? " open" : "")}
          onClick={() => setOpenDD(openDD === "model" ? null : "model")}
        >
          <span className="sel-icon"><CurIcon size={18} /></span>
          <span className="sel-meta">
            <span className="lbl">MODEL</span>
            <span className="val">{curItem.name}</span>
          </span>
          <ChevronDown size={15} className="chev" />
        </button>
        {openDD === "model" && (
          <div className="dropdown" onClick={(e) => e.stopPropagation()}>
            <ModelList
              groups={models}
              modelId={modelId}
              onPick={(id) => { onPickModel(id); setOpenDD(null); }}
              live={live}
              err={modelsErr}
              onRefresh={onRefreshModels}
            />
            {/* Elle model adı yazma alanı kaldırıldı: liste PC'deki Ollama'dan
                canlı gelir, seçim buradan yapılır. */}
          </div>
        )}
      </div>

      <div className="dock-group">
        {EFFORTS.map((e) => {
          const Ic = e.icon;
          return (
            <button
              key={e.id}
              className={"eff-opt" + (e.id === effort ? " on" : "")}
              onClick={() => onEffort(e.id)}
              title={e.name}
            >
              <Ic size={14} /><span className="txt">{e.name}</span>
            </button>
          );
        })}
        <button
          className={"toggle-btn" + (reasoning ? " on" : "")}
          onClick={() => onReasoning(!reasoning)}
          title="Düşünme modu"
        >
          <span className={"switch" + (reasoning ? " on" : "")} style={{ pointerEvents: "none" }}>
            <span className="track" />
          </span>
          Düşünme
        </button>
        <button
          className={"toggle-btn ember" + (agentMode ? " on" : "")}
          onClick={() => onAgentMode(!agentMode)}
          title="Ajan: native tool-calling ile web araması + araçlar (Gateway + yerel modeller)"
        >
          <Icons.agent size={13} /> Ajan
        </button>
        <button
          className={"toggle-btn ember" + (teamMode ? " on" : "")}
          onClick={() => onTeamMode(!teamMode)}
          title="Takım: görevi paralel alt-ajanlara böl, sonra sentezle"
        >
          <Icons.team size={13} /> Takım
        </button>
      </div>
    </div>
  );
}
