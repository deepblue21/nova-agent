// Sol ray. Üst bölümü mobil NavigationBar ile aynı hedefleri (Kontrol / İşler /
// Sohbet / Modeller) aynı sıra, etiket ve ikonlarla gösterir; altı sohbet
// geçmişi + dışa aktarma + ayarlar.
import React from "react";
import { Download, Link2, Plus, Trash2, X } from "lucide-react";
import { DESTINATIONS } from "../design/tokens.generated.mjs";
import { Icons, navIcon } from "../lib/icons.mjs";
import { NovaBadge } from "../ui/NovaMark.jsx";

export function NavList({ view, onView, taskBadge, className = "rail-nav" }) {
  return (
    <nav className={className} aria-label="Bölümler">
      {DESTINATIONS.map((d) => {
        const Ic = navIcon(d.icon);
        return (
          <button
            key={d.id}
            className={"nav-item" + (view === d.id ? " on" : "")}
            onClick={() => onView(d.id)}
            aria-current={view === d.id ? "page" : undefined}
          >
            <Ic size={17} />
            <span>{d.label}</span>
            {d.id === "isler" && taskBadge > 0 && <span className="nav-badge">{taskBadge}</span>}
          </button>
        );
      })}
    </nav>
  );
}

export function SideRail({
  view, onView, taskBadge,
  convs, activeId, onOpenConv, onNewConv, onDeleteConv,
  search, onSearch, hasMessages, onExport, onShare, shareNote,
  onSettings, authEmail,
}) {
  return (
    <aside className="rail" onClick={(e) => e.stopPropagation()}>
      <div className="rail-brand">
        <NovaBadge size={30} radius={9} title="NOVA" />
        <span>NOVA</span>
      </div>

      <NavList view={view} onView={onView} taskBadge={taskBadge} />

      <button className="btn-accent-soft" onClick={onNewConv}><Plus size={16} /> Yeni sohbet</button>

      <div className="rail-search">
        <input value={search} onChange={(e) => onSearch(e.target.value)} placeholder="Sohbetlerde ara…" />
        {search && <button onClick={() => onSearch("")} aria-label="Aramayı temizle"><X size={13} /></button>}
      </div>

      <div className="rail-list">
        {convs.map((c) => (
          <div
            key={c.id}
            className={"conv-row" + (c.id === activeId ? " on" : "")}
            onClick={() => onOpenConv(c.id)}
          >
            <Icons.chat size={14} />
            <span className="cr-title">{c.title || "Yeni sohbet"}</span>
            {c.serverId && <Icons.cloud size={12} className="cr-cloud" title="Sunucuya senkron" />}
            <button
              className="cr-del"
              onClick={(e) => { e.stopPropagation(); onDeleteConv(c.id); }}
              aria-label="Sohbeti sil"
            >
              <Trash2 size={13} />
            </button>
          </div>
        ))}
        {convs.length === 0 && <div className="rail-empty">Sonuç yok</div>}
      </div>

      {hasMessages && (
        <div className="rail-export">
          <span>Dışa aktar:</span>
          <button onClick={() => onExport("md")} title="Markdown indir"><Download size={13} /> MD</button>
          <button onClick={() => onExport("json")} title="JSON indir"><Download size={13} /> JSON</button>
          <button onClick={() => onExport("pdf")} title="PDF olarak yazdır/kaydet"><Download size={13} /> PDF</button>
          <button onClick={onShare} title="Paylaşılabilir local link kopyala">
            <Link2 size={13} /> {shareNote || "Link"}
          </button>
        </div>
      )}

      <button className="btn block settings-motion" onClick={(e) => { e.stopPropagation(); onSettings(); }}>
        <Icons.settings size={15} /> Ayarlar{authEmail ? " · " + authEmail.split("@")[0] : ""}
      </button>
    </aside>
  );
}

/** Dar ekranlarda mobil alt gezinme çubuğunun birebir karşılığı. */
export function BottomNav({ view, onView, taskBadge }) {
  return (
    <nav className="bottom-nav" aria-label="Bölümler">
      {DESTINATIONS.map((d) => {
        const Ic = navIcon(d.icon);
        return (
          <button
            key={d.id}
            className={view === d.id ? "on" : ""}
            onClick={() => onView(d.id)}
            aria-current={view === d.id ? "page" : undefined}
          >
            <span className="bn-ic">
              <Ic size={19} />
            </span>
            <span>{d.label}{d.id === "isler" && taskBadge > 0 ? ` (${taskBadge})` : ""}</span>
          </button>
        );
      })}
    </nav>
  );
}

export function ConversationsDrawer({ convs, activeId, onOpenConv, onNewConv, onDeleteConv, onClose, view, onView, taskBadge }) {
  return (
    <>
      <div className="scrim" onClick={onClose} />
      <div className="drawer" onClick={(e) => e.stopPropagation()}>
        <div className="drawer-head">
          <div className="title">NOVA</div>
          <button className="icon-btn" style={{ width: 34, height: 34 }} onClick={onClose} aria-label="Kapat">
            <X size={16} />
          </button>
        </div>

        <NavList view={view} onView={(v) => { onView(v); onClose(); }} taskBadge={taskBadge} />

        <div style={{ height: 8 }} />
        <button className="btn-accent-soft" onClick={onNewConv}><Plus size={16} /> Yeni sohbet</button>

        <div className="rail-list" style={{ marginTop: 8 }}>
          {convs.map((c) => (
            <div
              key={c.id}
              className={"conv-row" + (c.id === activeId ? " on" : "")}
              onClick={() => onOpenConv(c.id)}
            >
              <Icons.chat size={14} />
              <span className="cr-title">{c.title || "Yeni sohbet"}</span>
              {c.serverId && <Icons.cloud size={12} className="cr-cloud" title="Sunucuya senkron" />}
              <button
                className="cr-del"
                onClick={(e) => { e.stopPropagation(); onDeleteConv(c.id); }}
                aria-label="Sohbeti sil"
              >
                <Trash2 size={14} />
              </button>
            </div>
          ))}
        </div>
      </div>
    </>
  );
}
