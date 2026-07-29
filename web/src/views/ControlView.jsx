// Kontrol merkezi — Android `ControlScreen` ile aynı bilgi mimarisi:
// yürütme hedefi → hedef kartı → aktif iş. Telefona özgü politikalar web'de
// pasif gösterilir ve nedeni yazılır (taklit edilmez).
import React from "react";
import { Check, ChevronRight, RotateCcw } from "lucide-react";
import { Card, CardHead, Chip, SectionLabel, StatusDot, EmptyPanel } from "../ui/primitives.jsx";
import { NovaThinkingMark } from "../ui/NovaMark.jsx";
import { Icons } from "../lib/icons.mjs";
import { EXECUTION_TARGETS } from "../lib/constants.mjs";
import { statusLabel, statusTone } from "../lib/tasks.mjs";

const TONE_CLASS = { success: "ok", warning: "warn", danger: "err", accent: "accent", muted: "" };

function ConnectionCard({ health, healthErr, baseUrl, signedIn, email, onRetry, onOpenSettings }) {
  const ok = !!health;
  return (
    <Card className="hoverable">
      <div className="card-head">
        <div className="ch-ic" style={{ position: "relative" }}>
          <Icons.gateway size={18} />
        </div>
        <div className="ch-tx">
          <div className="card-title-lg">{ok ? "Gateway bağlı" : "Gateway'e ulaşılamıyor"}</div>
          <div className="card-sub-accent">
            {ok
              ? `Varsayılan: ${health.default || "?"} · Görsel: ${health.vision || "?"}`
              : (healthErr || "PC'deki gateway açık mı?")}
          </div>
        </div>
        <StatusDot tone={ok ? "ok" : "err"} />
      </div>

      <div className="row wrap">
        <Chip className="t-mono">{baseUrl || "adres yok"}</Chip>
        {signedIn
          ? <Chip tone="ok" icon={Check}>{email || "oturum açık"}</Chip>
          : <Chip tone="warn">oturum yok</Chip>}
        {ok && health.voice_queue != null && (
          <Chip tone={health.voice_queue ? "ok" : ""}>Ses kuyruğu: {health.voice_queue ? "açık" : "kapalı"}</Chip>
        )}
        {ok && health.remote_images != null && (
          <Chip tone={health.remote_images ? "ok" : ""}>Uzak görsel: {health.remote_images ? "açık" : "kapalı"}</Chip>
        )}
      </div>

      <div className="row">
        <button className="btn sm" onClick={onRetry}><RotateCcw size={13} /> Yeniden dene</button>
        <button className="btn sm ghost" onClick={onOpenSettings}>Bağlantı ayarları <ChevronRight size={13} /></button>
      </div>
    </Card>
  );
}

function TargetPicker({ value, onChange }) {
  return (
    <div className="pick-grid">
      {EXECUTION_TARGETS.map((t) => {
        const Ic = t.icon;
        const disabled = !t.webSupported;
        const sel = value === t.id;
        return (
          <button
            key={t.id}
            type="button"
            className={"pick" + (sel ? " sel" : "") + (disabled ? " disabled" : "")}
            title={disabled ? t.reason : t.desc}
            aria-disabled={disabled}
            onClick={() => { if (!disabled) onChange(t.id); }}
          >
            <span className="pk-ic"><Ic size={17} /></span>
            <span>
              <span className="pk-t">{t.label}</span>
              <span className="pk-d">{disabled ? t.reason : t.note}</span>
            </span>
            {sel && <Check size={16} className="pk-check" />}
          </button>
        );
      })}
    </div>
  );
}

export function ControlView({
  health, healthErr, baseUrl, signedIn, email,
  target, onTarget, modelName, modelId, effortName, personaName, reasoning,
  agentMode, teamMode, chatBusy, activeTask, taskCount,
  onRetry, onOpenSettings, onOpenChat, onOpenTasks, onOpenModels,
}) {
  const tone = activeTask ? TONE_CLASS[statusTone(activeTask.status)] || "" : "";

  return (
    <div className="panel-scroll view-enter">
      <div>
        <SectionLabel>Bağlantı</SectionLabel>
        <ConnectionCard
          health={health} healthErr={healthErr} baseUrl={baseUrl}
          signedIn={signedIn} email={email}
          onRetry={onRetry} onOpenSettings={onOpenSettings}
        />
      </div>

      <div>
        <SectionLabel>Yürütme hedefi</SectionLabel>
        <TargetPicker value={target} onChange={onTarget} />
        <div className="hint" style={{ marginTop: 10 }}>
          Bulut modelleri de PC'deki Gateway üzerinden çağrılır; anahtarlar tarayıcıya inmez.
          Telefona özgü hedefler Android istemcisinde açılır.
        </div>
      </div>

      <div>
        <SectionLabel>Etkin yapılandırma</SectionLabel>
        <Card className="hoverable">
          <CardHead
            icon={Icons.brand}
            title={modelName}
            desc={modelId ? modelId : "model seçilmedi"}
            right={<button className="btn sm ghost" onClick={onOpenModels}>Değiştir <ChevronRight size={13} /></button>}
          />
          <div className="row wrap">
            <Chip tone="accent" icon={Icons.think}>{personaName}</Chip>
            <Chip>{effortName}</Chip>
            {reasoning && <Chip tone="accent">düşünme</Chip>}
            {agentMode && <Chip tone="warn" icon={Icons.agent}>ajan</Chip>}
            {teamMode && <Chip tone="warn" icon={Icons.team}>takım</Chip>}
          </div>
        </Card>
      </div>

      <div>
        <SectionLabel>Aktif iş</SectionLabel>
        {chatBusy ? (
          <Card className="accent">
            <div className="card-head">
              <div className="ch-ic" style={{ background: "none" }}><NovaThinkingMark size={34} /></div>
              <div className="ch-tx">
                <div className="ch-t">Sohbet yanıtı üretiliyor</div>
                <div className="ch-d">{modelName} · akış sürüyor</div>
              </div>
              <button className="btn sm ghost" onClick={onOpenChat}>Sohbete git <ChevronRight size={13} /></button>
            </div>
          </Card>
        ) : activeTask ? (
          <Card className="hoverable">
            <div className="card-head">
              <div className="ch-ic"><Icons.tasks size={18} /></div>
              <div className="ch-tx">
                <div className="ch-t" style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                  {activeTask.prompt || "Görev"}
                </div>
                <div className="ch-d">Durum: {statusLabel(activeTask.status)}</div>
              </div>
              <Chip tone={tone}>{statusLabel(activeTask.status)}</Chip>
            </div>
            <button className="btn sm ghost" onClick={onOpenTasks}>İşler paneline git <ChevronRight size={13} /></button>
          </Card>
        ) : (
          <EmptyPanel
            icon={Icons.tasks}
            title="Şu an çalışan iş yok"
            desc={taskCount > 0
              ? `${taskCount} kayıtlı görev var. İşler panelinden geçmişi inceleyebilir ya da yeni görev başlatabilirsin.`
              : "İşler panelinden telefonda çalışacak bir görev başlatabilirsin."}
            action={<button className="btn sm" onClick={onOpenTasks}>İşler paneli <ChevronRight size={13} /></button>}
          />
        )}
      </div>
    </div>
  );
}
