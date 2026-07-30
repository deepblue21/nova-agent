// Üst çubuk — mobil `NovaTopBar` ile aynı yapı: marka + durum satırı + hızlı
// model seçimi + ses/sohbet geçişi + ayarlar.
import React from "react";
import { ChevronDown, Menu } from "lucide-react";
import { Icons } from "../lib/icons.mjs";
import { NovaBadge } from "../ui/NovaMark.jsx";
import { ModelList } from "../ui/ModelList.jsx";

export function TopBar({
  subtitle, statusTone, modelName, modelId, connected,
  openDD, setOpenDD, models, selectedModelId, onPickModel, live, modelsErr, onRefreshModels,
  view, onToggleVoice, onOpenDrawer, onSettings,
}) {
  return (
    <header className="topbar">
      <div className="brand">
        <button
          className="icon-btn rail-only-narrow"
          onClick={(e) => { e.stopPropagation(); onOpenDrawer(); }}
          title="Menü"
          aria-label="Menü"
        >
          <Menu size={18} />
        </button>
        <NovaBadge size={38} title="NOVA" />
        <div className="brand-text">
          <h1>NOVA</h1>
          <div className="sub">
            <span className={"status-dot " + statusTone} />
            {subtitle}
          </div>
        </div>
      </div>

      <div className="topright">
        <div style={{ position: "relative" }}>
          <button
            className={"status-pill clickable" + (openDD === "hmodel" ? " open" : "")}
            onClick={(e) => { e.stopPropagation(); setOpenDD(openDD === "hmodel" ? null : "hmodel"); }}
            title="Modeli değiştir"
          >
            <span className={"status-dot" + (connected ? "" : " off")} />
            {modelName}{modelId ? " · " + modelId : ""}
            <ChevronDown size={13} style={{ marginLeft: 2, opacity: .6 }} />
          </button>
          {openDD === "hmodel" && (
            <div className="dropdown below" onClick={(e) => e.stopPropagation()}>
              <ModelList
                groups={models}
                modelId={selectedModelId}
                onPick={(id) => { onPickModel(id); setOpenDD(null); }}
                live={live}
                err={modelsErr}
                onRefresh={onRefreshModels}
              />
            </div>
          )}
        </div>

        {(view === "sohbet" || view === "ses") && (
          <button
            className="icon-btn"
            onClick={(e) => { e.stopPropagation(); onToggleVoice(); }}
            title={view === "ses" ? "Sohbete dön" : "Ses moduna geç"}
            aria-label={view === "ses" ? "Sohbete dön" : "Ses moduna geç"}
          >
            {view === "ses" ? <Icons.chat size={18} /> : <Icons.voice size={18} />}
          </button>
        )}

        <button
          className="icon-btn settings-motion"
          onClick={(e) => { e.stopPropagation(); onSettings(); }}
          title="Ayarlar"
          aria-label="Ayarlar"
        >
          <Icons.settings size={18} />
        </button>
      </div>
    </header>
  );
}
