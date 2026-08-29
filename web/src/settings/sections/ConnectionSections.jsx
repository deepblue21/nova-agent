// Sağlayıcılar (+ Keycloak oturumu), ses (Whisper STT / TTS) ve ajan katmanı.
import React from "react";
import { Check, Link2 } from "lucide-react";
import { Accordion, Field, SecretField, Switch, Chip } from "../../ui/primitives.jsx";
import { Icons } from "../../lib/icons.mjs";
import { AGENTS, PROV_META, PROV_ORDER } from "../../lib/constants.mjs";

export function ProvidersSection({
  providers, onProv, provReady, auth, onLogin, onLogout, health,
}) {
  return (
    <Accordion
      icon={Icons.gateway}
      title="Model Sağlayıcıları"
      desc={auth ? `Oturum: ${auth.email || "açık"}` : "Gateway önerilir"}
      defaultOpen
    >
      {PROV_ORDER.map((id) => {
        const meta = PROV_META[id];
        const p = providers[id];
        const Ic = meta.icon;
        return (
          <div key={id} className="card" style={{ padding: 13, marginBottom: 10, gap: 11 }}>
            <div className="card-head">
              <div className="ch-ic" style={{ width: 34, height: 34 }}><Ic size={18} /></div>
              <div className="ch-tx">
                <div className="ch-t">
                  {meta.label} {provReady(id) && <Check size={13} style={{ color: "var(--accent)", verticalAlign: "middle" }} />}
                </div>
                <div className="ch-d">{meta.hint}</div>
              </div>
            </div>

            <Field label="Base URL">
              <input className="input mono" value={p.baseUrl} onChange={(e) => onProv(id, { baseUrl: e.target.value })} placeholder="base url" />
            </Field>

            {meta.keyLabel && (
              <SecretField
                label={meta.keyLabel}
                value={p.apiKey}
                onChange={(v) => onProv(id, { apiKey: v })}
                hint={id === "gateway"
                  ? "Aynı anahtar Android uygulamasındaki “Erişim belirteci” alanına girilir."
                  : ""}
              />
            )}

            {id === "gateway" && (
              <div className="row wrap">
                {auth ? (
                  <>
                    <Chip tone="ok" icon={Check}>{auth.email || "oturum açık"}</Chip>
                    <button className="btn sm ghost" onClick={onLogout}>Çıkış</button>
                  </>
                ) : (
                  <button className="btn sm primary" onClick={onLogin}><Link2 size={13} /> Keycloak ile Giriş</button>
                )}
              </div>
            )}

            {id === "gateway" && health && (
              <div className="hint">
                Default: <b>{health.default || "?"}</b> · Görsel: <b>{health.vision || "?"}</b> ·
                Uzak görsel: <b>{health.remote_images ? "açık" : "kapalı"}</b> ·
                Ses kuyruğu: <b>{health.voice_queue ? "açık" : "kapalı"}</b>
              </div>
            )}
          </div>
        );
      })}
    </Accordion>
  );
}

export function VoiceSection({ voiceCfg, onVoiceCfg }) {
  return (
    <Accordion icon={Icons.voice} title="Ses" desc={voiceCfg.real ? "Whisper STT + TTS" : "Tarayıcı sesi"}>
      <div className="card" style={{ padding: 13, gap: 11 }}>
        <div className="card-head">
          <div className="ch-ic" style={{ width: 34, height: 34 }}><Icons.agent size={18} /></div>
          <div className="ch-tx">
            <div className="ch-t">Gerçek ses</div>
            <div className="ch-d">
              Mikrofon → Whisper, yanıt → TTS. Orb gerçek dalgaya tepki verir.
              Kapalıyken tarayıcının yerleşik sesi kullanılır.
            </div>
          </div>
          <Switch on={voiceCfg.real} onChange={(v) => onVoiceCfg({ real: v })} title="Gerçek ses" />
        </div>

        {voiceCfg.real && (
          <>
            <div className="card-head">
              <div className="ch-ic" style={{ width: 34, height: 34 }}><Icons.team size={18} /></div>
              <div className="ch-tx">
                <div className="ch-t">Kuyruk</div>
                <div className="ch-d">BullMQ job takibi.</div>
              </div>
              <Switch on={voiceCfg.queued} onChange={(v) => onVoiceCfg({ queued: v })} title="Kuyruk" />
            </div>

            <Field label="STT ucu (Whisper)">
              <input className="input mono" value={voiceCfg.sttUrl} onChange={(e) => onVoiceCfg({ sttUrl: e.target.value })} placeholder="/stt" />
            </Field>
            <Field label="TTS ucu">
              <input className="input mono" value={voiceCfg.ttsUrl} onChange={(e) => onVoiceCfg({ ttsUrl: e.target.value })} placeholder="/tts" />
            </Field>
            {voiceCfg.queued && (
              <Field label="Job ucu">
                <input className="input mono" value={voiceCfg.jobUrl} onChange={(e) => onVoiceCfg({ jobUrl: e.target.value })} placeholder="/v1/voice/jobs" />
              </Field>
            )}
            <Field label="Ses (voice)">
              <input className="input mono" value={voiceCfg.voice} onChange={(e) => onVoiceCfg({ voice: e.target.value })} placeholder="alloy" />
            </Field>
          </>
        )}
      </div>
    </Accordion>
  );
}

export function AgentLayerSection({ agent, onAgent, agentUrl, onAgentUrl }) {
  const active = AGENTS.find((a) => a.id === agent) || AGENTS[0];
  return (
    <Accordion icon={Icons.agent} title="Ajan Katmanı" desc={active.name}>
      {AGENTS.map((a) => {
        const Ic = a.icon;
        return (
          <button
            key={a.id}
            type="button"
            className={"pick" + (a.id === agent ? " sel" : "")}
            style={{ width: "100%", marginBottom: 9 }}
            onClick={() => { onAgent(a.id); onAgentUrl(a.ph || ""); }}
          >
            <span className="pk-ic"><Ic size={18} /></span>
            <span>
              <span className="pk-t">{a.name}</span>
              <span className="pk-d">{a.desc}</span>
            </span>
            {a.id === agent && <Check size={16} className="pk-check" />}
          </button>
        );
      })}
      {agent !== "direct" && (
        <Field label="Ajan Endpoint">
          <input className="input mono" value={agentUrl} onChange={(e) => onAgentUrl(e.target.value)} placeholder={active.ph} />
        </Field>
      )}
    </Accordion>
  );
}
