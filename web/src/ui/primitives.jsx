// Paylaşılan görsel ilkeller. Compose tarafındaki SectionLabel / Card / Chip
// karşılıklarıyla aynı ölçü ve davranışa sahiptir.
import React, { useState } from "react";
import { ChevronDown } from "lucide-react";

export const SectionLabel = ({ children }) => <div className="section-label">{children}</div>;

export function Card({ className = "", children, ...rest }) {
  return <div className={"card " + className} {...rest}>{children}</div>;
}

export function CardHead({ icon: Icon, title, desc, right }) {
  return (
    <div className="card-head">
      {Icon && <div className="ch-ic"><Icon size={18} /></div>}
      <div className="ch-tx">
        <div className="ch-t">{title}</div>
        {desc && <div className="ch-d">{desc}</div>}
      </div>
      {right}
    </div>
  );
}

export function Chip({ tone = "", icon: Icon, children, className = "", ...rest }) {
  return (
    <span className={`chip ${tone} ${className}`.trim()} {...rest}>
      {Icon && <Icon size={11} />}
      {children}
    </span>
  );
}

/** tone: "" | "ok" | "warn" | "err" | "off" */
export const StatusDot = ({ tone = "" }) => <span className={("status-dot " + tone).trim()} />;

export function Switch({ on, onChange, label, title }) {
  return (
    <button
      type="button"
      className={"switch" + (on ? " on" : "")}
      onClick={() => onChange(!on)}
      role="switch"
      aria-checked={!!on}
      title={title || label}
    >
      <span className="track" />
      {label && <span>{label}</span>}
    </button>
  );
}

export function Field({ label, children }) {
  return (
    <label className="field">
      {label && <span>{label}</span>}
      {children}
    </label>
  );
}

export function EmptyPanel({ icon: Icon, title, desc, action }) {
  return (
    <div className="empty-panel">
      {Icon && <Icon size={26} />}
      {title && <div className="ep-t">{title}</div>}
      {desc && <div className="ep-d">{desc}</div>}
      {action}
    </div>
  );
}

/** Ayarlar bölümü — varsayılan kapalı, içerik ilk açılışta render edilir. */
export function Accordion({ icon: Icon, title, desc, defaultOpen = false, children }) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <div className="m-acc">
      <button type="button" className={"m-acc-head" + (open ? " open" : "")} onClick={() => setOpen((o) => !o)}>
        {Icon && <Icon size={16} />}
        <span className="m-acc-t">
          {title}
          {desc && <span className="m-acc-d">{desc}</span>}
        </span>
        <ChevronDown size={15} className="chev" />
      </button>
      {open && <div className="m-acc-body">{children}</div>}
    </div>
  );
}

/** Yatay ilerleme çubuğu (kota, depolama). */
export function Meter({ value, max, note }) {
  const pct = max > 0 ? Math.min(100, (100 * Number(value || 0)) / Number(max)) : 0;
  return (
    <div>
      <div className="meter"><span style={{ width: pct + "%" }} /></div>
      {note && <div className="meter-note">{note}</div>}
    </div>
  );
}
