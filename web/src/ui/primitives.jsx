// Paylaşılan görsel ilkeller. Compose tarafındaki SectionLabel / Card / Chip
// karşılıklarıyla aynı ölçü ve davranışa sahiptir.
import React, { useEffect, useRef, useState } from "react";
import { Check, ChevronDown, Copy, Eye, EyeOff } from "lucide-react";
import { copyText } from "../lib/format.mjs";

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

/**
 * Gizli değer alanı: varsayılan olarak maskeli, "göz" düğmesiyle açılır,
 * "kopyala" düğmesiyle panoya alınır. Android'deki NovaSecretField ile aynı
 * davranış (aynı ikonlar, aynı geri bildirim süresi).
 *
 * Maskeyi kalıcı açık bırakmak riskli olduğu için görünürlük
 * `revealTimeoutMs` sonunda kendiliğinden kapanır (0 = kapanma).
 */
export function SecretField({
  label,
  value,
  onChange,
  placeholder = "••••••••",
  readOnly = false,
  hint = "",
  revealTimeoutMs = 30000,
}) {
  const [shown, setShown] = useState(false);
  const [copied, setCopied] = useState("");   // "" | "ok" | "err"
  const timers = useRef({ hide: null, copy: null });

  useEffect(() => () => {
    clearTimeout(timers.current.hide);
    clearTimeout(timers.current.copy);
  }, []);

  const toggle = () => {
    clearTimeout(timers.current.hide);
    setShown((prev) => {
      const next = !prev;
      if (next && revealTimeoutMs > 0) {
        timers.current.hide = setTimeout(() => setShown(false), revealTimeoutMs);
      }
      return next;
    });
  };

  const copy = async () => {
    const ok = await copyText(value);
    clearTimeout(timers.current.copy);
    setCopied(ok ? "ok" : "err");
    timers.current.copy = setTimeout(() => setCopied(""), 1600);
  };

  const empty = !value;

  return (
    <div className="field">
      {label && <span>{label}</span>}
      <div className="secret-row">
        <input
          className="input mono"
          type={shown ? "text" : "password"}
          value={value}
          readOnly={readOnly}
          onChange={onChange ? (e) => onChange(e.target.value) : undefined}
          placeholder={placeholder}
          autoComplete="off"
          spellCheck={false}
        />
        <button
          type="button"
          className="icon-btn sm"
          onClick={toggle}
          disabled={empty}
          aria-pressed={shown}
          aria-label={shown ? "Gizle" : "Göster"}
          title={shown ? "Gizle" : "Göster"}
        >
          {shown ? <EyeOff size={15} /> : <Eye size={15} />}
        </button>
        <button
          type="button"
          className={"icon-btn sm" + (copied === "ok" ? " ok" : "") + (copied === "err" ? " err" : "")}
          onClick={copy}
          disabled={empty}
          aria-label="Panoya kopyala"
          title={copied === "err" ? "Kopyalanamadı — elle seçip kopyala" : "Panoya kopyala"}
        >
          {copied === "ok" ? <Check size={15} /> : <Copy size={15} />}
        </button>
      </div>
      {(hint || copied) && (
        <span className="secret-hint">
          {copied === "ok" ? "Panoya kopyalandı." : copied === "err" ? "Kopyalanamadı — alanı açıp elle seç." : hint}
        </span>
      )}
    </div>
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
