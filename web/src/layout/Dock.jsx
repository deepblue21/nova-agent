// Alt dock: Sesli/Sohbet kipi, model seçici, çaba kademesi, düşünme/ajan/takım
// anahtarları. Yalnız sohbet ve ses görünümlerinde gösterilir.
import React from "react";
import { ChevronDown, CircleHelp } from "lucide-react";
import { EFFORTS } from "../lib/constants.mjs";
import { Icons } from "../lib/icons.mjs";
import { ModelList } from "../ui/ModelList.jsx";
import { canDisableThinking, capabilityState } from "../lib/model-capabilities.mjs";

function CapabilityInfo({ text }) {
  return (
    <span tabIndex={0} className="cap-info" aria-label={text}>
      <CircleHelp size={13} aria-hidden="true" />
      <span className="cap-tip" role="tooltip">{text}</span>
    </span>
  );
}

export function Dock({
  view, onView, curItem, models, modelId, onPickModel,
  live, modelsErr, onRefreshModels,
  openDD, setOpenDD, effort, onEffort, reasoning, onReasoning,
  agentMode, onAgentMode, teamMode, onTeamMode,
}) {
  const CurIcon = curItem.icon;
  const thinkingState = capabilityState(curItem, "thinking");
  const toolsState = capabilityState(curItem, "tools");
  const thinkingCanDisable = canDisableThinking(curItem);
  const thinkingToggleDisabled = !thinkingState.supported || !thinkingCanDisable;
  const thinkingOn = thinkingState.supported && (!thinkingCanDisable || reasoning);
  const thinkingTitle = !thinkingState.supported
    ? thinkingState.reason
    : (!thinkingCanDisable ? `${curItem.name} düşünmeyi kapatamaz; en düşük düzey Hızlıdır.` : "Düşünme modu");
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

      <div className="dock-group capability-dock-group">
        {EFFORTS.map((e) => {
          const Ic = e.icon;
          return (
            <button
              key={e.id}
              className={"eff-opt" + (thinkingState.supported && e.id === effort ? " on" : "")}
              onClick={() => onEffort(e.id)}
              title={thinkingState.supported ? e.name : thinkingState.reason}
              disabled={!thinkingState.supported}
            >
              <Ic size={14} /><span className="txt">{e.name}</span>
            </button>
          );
        })}
        <button
          className={"toggle-btn" + (thinkingOn ? " on" : "")}
          onClick={() => onReasoning(!reasoning)}
          title={thinkingTitle}
          disabled={thinkingToggleDisabled}
        >
          <span className={"switch" + (thinkingOn ? " on" : "")} style={{ pointerEvents: "none" }}>
            <span className="track" />
          </span>
          Düşünme
        </button>
        {!thinkingState.supported && <CapabilityInfo text={thinkingState.reason} />}
        <button
          className={"toggle-btn ember" + (toolsState.supported && agentMode ? " on" : "")}
          onClick={() => onAgentMode(!agentMode)}
          title={toolsState.supported
            ? "Ajan: native tool-calling ile web araması + araçlar (Gateway + yerel modeller)"
            : toolsState.reason}
          disabled={!toolsState.supported}
        >
          <Icons.agent size={13} /> Ajan
        </button>
        <button
          className={"toggle-btn ember" + (toolsState.supported && teamMode ? " on" : "")}
          onClick={() => onTeamMode(!teamMode)}
          title={toolsState.supported
            ? "Takım: görevi paralel alt-ajanlara böl, sonra sentezle"
            : toolsState.reason}
          disabled={!toolsState.supported}
        >
          <Icons.team size={13} /> Takım
        </button>
        {!toolsState.supported && <CapabilityInfo text={toolsState.reason} />}
      </div>
    </div>
  );
}
