// İşler — mobil görev kontrol düzleminin web karşılığı. Android
// `MobileTaskScreen` ile aynı akış, aynı Türkçe durum sözlüğü ve aynı
// onay/duraklat/iptal kuralları. Aynı gateway uçlarını kullandığı için
// telefonda başlatılan görev burada, burada başlatılan telefonda görünür.
import React, { useState } from "react";
import { AlertTriangle, Check, RotateCcw, X } from "lucide-react";
import { Card, Chip, SectionLabel, EmptyPanel } from "../ui/primitives.jsx";
import { Icons } from "../lib/icons.mjs";
import {
  statusLabel, statusTone, availableCommands, COMMAND_LABEL,
  eventLabel, eventSummary, isTerminal,
} from "../lib/tasks.mjs";

const TONE_CLASS = { success: "ok", warning: "warn", danger: "err", accent: "accent", muted: "" };
const CMD_ICON = { pause: Icons.pause, resume: Icons.resume, cancel: Icons.cancel };

function TaskComposer({ value, onChange, onSubmit, busy, disabled }) {
  return (
    <Card>
      <div className="ch-t">Telefonunda ne yapmamı istersin?</div>
      <div className="hint">
        Görevi ayrıntılarıyla yaz. NOVA cihazı inceler, plan çıkarır ve riskli adımlarda
        <b> senden onay ister</b>. Onaysız hiçbir yıkıcı işlem yapılmaz.
      </div>
      <textarea
        className="textarea"
        rows={3}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder="Örn. Ayarlar'ı aç ve Android sürümünü bul"
        disabled={disabled}
      />
      <div className="row end">
        <button className="btn primary" onClick={onSubmit} disabled={busy || disabled || value.trim().length < 3}>
          {busy ? "Başlatılıyor…" : "Görevi başlat"}
        </button>
      </div>
    </Card>
  );
}

function ConfirmationCard({ confirmation, prompt, onDecide, busy }) {
  return (
    <Card className="accent" style={{ borderColor: "rgba(255,200,87,.4)", background: "rgba(255,200,87,.07)" }}>
      <div className="card-head">
        <div className="ch-ic" style={{ color: "var(--warning)", background: "rgba(255,200,87,.14)" }}>
          <AlertTriangle size={18} />
        </div>
        <div className="ch-tx">
          <div className="ch-t">{confirmation.riskLevel} onayı bekleniyor</div>
          <div className="ch-d">{confirmation.actionSummary || prompt || "Ajan riskli bir adım için izin istiyor."}</div>
        </div>
      </div>
      <div className="row end">
        <button className="btn danger" onClick={() => onDecide("reject")} disabled={busy}>
          <X size={14} /> Reddet
        </button>
        <button className="btn primary" onClick={() => onDecide("approve")} disabled={busy}>
          <Check size={14} /> Onayla ve devam et
        </button>
      </div>
    </Card>
  );
}

function Timeline({ events, prompt }) {
  if (!events.length) {
    return <div className="hint">Henüz olay yok. Görev ilerledikçe adımlar burada canlı akar.</div>;
  }
  return (
    <div className="timeline">
      {events.map((ev) => {
        const label = eventLabel(ev);
        const tone = TONE_CLASS[statusTone(ev.status || (ev.data && ev.data.status))] || "";
        const at = ev.created_at || ev.createdAt;
        const summary = eventSummary(ev, prompt);
        return (
          <div className="tl-item" key={ev.id}>
            <span className={"tl-dot " + tone}>
              {ev.type === "confirmation.requested"
                ? <AlertTriangle size={11} />
                : ev.type === "worker.completed" ? <Check size={11} /> : null}
            </span>
            <div className="tl-body">
              <div className="tl-label">{label}</div>
              {summary && summary !== label && <div className="tl-sum">{summary}</div>}
              {at && (
                <div className="tl-time">
                  {new Date(at).toLocaleTimeString("tr-TR", { hour: "2-digit", minute: "2-digit", second: "2-digit" })}
                </div>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}

export function TasksView({
  connected, tasks, active, activeId, setActiveId, events, confirmation,
  error, busy, loading, onCreate, onCommand, onDecide, onReload, onOpenSettings, onClearError,
}) {
  const [prompt, setPrompt] = useState("");

  if (!connected) {
    return (
      <div className="panel-scroll view-enter">
        <SectionLabel>İşler</SectionLabel>
        <EmptyPanel
          icon={Icons.gateway}
          title="Gateway oturumu gerekiyor"
          desc="Mobil görevler kimlik doğrulanmış gateway bağlantısıyla çalışır. Ayarlar → Gateway bölümünden Keycloak ile giriş yap ya da erişim belirtecini gir."
          action={<button className="btn primary" onClick={onOpenSettings}>Bağlantıyı ayarla</button>}
        />
      </div>
    );
  }

  const commands = active ? availableCommands(active.status) : [];

  const submit = async () => {
    const r = await onCreate(prompt);
    if (r && r.ok) setPrompt("");
  };

  return (
    <div className="panel-scroll view-enter">
      <div className="row" style={{ justifyContent: "space-between" }}>
        <SectionLabel>Yeni görev</SectionLabel>
        <button className="btn sm ghost" onClick={onReload} disabled={loading} title="Listeyi yenile">
          <RotateCcw size={13} /> {loading ? "…" : "Yenile"}
        </button>
      </div>

      <TaskComposer value={prompt} onChange={setPrompt} onSubmit={submit} busy={busy} />

      {error && (
        <div className="list-row" style={{ borderColor: "rgba(251,113,133,.3)" }}>
          <AlertTriangle size={13} style={{ color: "var(--danger)" }} />
          <span className="lr-name" style={{ color: "var(--danger)" }}>{error}</span>
          <button className="icon-act" onClick={onClearError}><X size={13} /></button>
        </div>
      )}

      {confirmation && (
        <ConfirmationCard
          confirmation={confirmation}
          prompt={active && active.prompt}
          onDecide={onDecide}
          busy={busy}
        />
      )}

      <div>
        <SectionLabel>Görevler</SectionLabel>
        {tasks.length === 0 ? (
          <EmptyPanel
            icon={Icons.tasks}
            title="Henüz görev yok"
            desc="Yukarıdan bir görev başlat. Telefon uygulamasında başlattığın görevler de burada görünür."
          />
        ) : (
          <div className="list">
            {tasks.map((t) => {
              const tone = TONE_CLASS[statusTone(t.status)] || "";
              return (
                <div
                  key={t.id}
                  className={"list-row" + (t.id === activeId ? " on" : "")}
                  style={t.id === activeId
                    ? { borderColor: "rgba(var(--accent-rgb),.4)", background: "rgba(var(--accent-rgb),.07)" }
                    : { cursor: "pointer" }}
                  onClick={() => setActiveId(t.id)}
                >
                  <Icons.tasks size={13} />
                  <span className="lr-name" title={t.prompt}>{t.prompt || "(boş görev)"}</span>
                  <Chip tone={tone}>{statusLabel(t.status)}</Chip>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {active && (
        <div>
          <SectionLabel>Seçili görev</SectionLabel>
          <Card>
            <div className="card-head">
              <div className="ch-ic"><Icons.tasks size={18} /></div>
              <div className="ch-tx">
                <div className="ch-t">{active.prompt || "Görev"}</div>
                <div className="ch-d">Durum: {statusLabel(active.status)}</div>
              </div>
              <Chip tone={TONE_CLASS[statusTone(active.status)] || ""}>{statusLabel(active.status)}</Chip>
            </div>

            {commands.length > 0 && (
              <div className="row wrap">
                {commands.map((c) => {
                  const Ic = CMD_ICON[c] || Icons.check;
                  return (
                    <button
                      key={c}
                      className={"btn sm" + (c === "cancel" ? " danger" : "")}
                      onClick={() => onCommand(c)}
                      disabled={busy}
                    >
                      <Ic size={13} /> {COMMAND_LABEL[c]}
                    </button>
                  );
                })}
              </div>
            )}
            {isTerminal(active.status) && (
              <div className="hint">Bu görev tamamlandı; komut kabul etmiyor. Yeni bir görev başlatabilirsin.</div>
            )}

            <div>
              <SectionLabel>Olay akışı</SectionLabel>
              <Timeline events={events} prompt={active.prompt} />
            </div>
          </Card>
        </div>
      )}
    </div>
  );
}
