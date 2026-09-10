// Çalışma alanları (RBAC), zamanlanmış ajan görevleri, ajan çalışma geçmişi ve
// MCP araç envanteri. Hepsi gateway oturumu gerektirir.
import React from "react";
import { RotateCcw, Settings, Trash2, Users, X } from "lucide-react";
import { Accordion } from "../../ui/primitives.jsx";
import { Icons } from "../../lib/icons.mjs";
import { SCHEDULE_OPTIONS } from "../../lib/constants.mjs";

const editable = (wss) => wss.filter((w) => w.role === "admin" || w.role === "editor");

export function WorkspacesSection({
  wss, wsName, onWsName, onCreate, wsOpen, wsMembers, onToggleMembers,
  wsInvite, onWsInvite, onInvite, onChangeRole, onRemoveMember,
}) {
  return (
    <Accordion icon={Users} title="Çalışma Alanları" desc={`Takım · RBAC · ${wss.length} alan`}>
      <div className="hint" style={{ marginBottom: 9 }}>
        Çalışma alanı oluştur ve üyeleri rolüyle yönet: <b>admin</b> (tam yetki) ·
        <b> editör</b> (paylaşılan içerik yaz) · <b>izleyici</b> (sadece görüntüle).
      </div>

      <div className="row">
        <input
          className="input grow"
          value={wsName}
          onChange={(e) => onWsName(e.target.value)}
          placeholder="Yeni çalışma alanı adı"
        />
        <button className="btn sm primary" onClick={onCreate} disabled={wsName.trim().length < 1}>Oluştur</button>
      </div>

      {wss.length > 0 && (
        <div className="list" style={{ marginTop: 11 }}>
          {wss.map((w) => (
            <div key={w.id} style={{ border: "1px solid var(--line)", borderRadius: 10, padding: "8px 10px", background: "rgba(255,255,255,0.02)" }}>
              <div className="list-row" style={{ border: "none", padding: 0, background: "none" }}>
                <Users size={13} />
                <span className="lr-name">{w.name}</span>
                <span className="lr-meta">{w.role}</span>
                <button className="icon-act" onClick={() => onToggleMembers(w.id)} title="Üyeler">
                  {wsOpen === w.id ? <X size={13} /> : <Settings size={13} />}
                </button>
              </div>

              {wsOpen === w.id && (
                <div style={{ marginTop: 8, display: "flex", flexDirection: "column", gap: 6 }}>
                  {wsMembers.map((m) => (
                    <div key={m.user_id} style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 12 }}>
                      <span style={{ flex: 1, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", color: "var(--text)" }}>
                        {m.email}
                      </span>
                      {w.role === "admin" ? (
                        <select
                          className="select"
                          style={{ flex: "0 0 auto", padding: "4px 24px 4px 8px", width: "auto" }}
                          value={m.role}
                          onChange={(e) => onChangeRole(w.id, m.user_id, e.target.value)}
                        >
                          <option value="admin">admin</option>
                          <option value="editor">editör</option>
                          <option value="viewer">izleyici</option>
                        </select>
                      ) : <span className="lr-meta">{m.role}</span>}
                      {w.role === "admin" && (
                        <button className="icon-act danger" onClick={() => onRemoveMember(w.id, m.user_id)}>
                          <Trash2 size={12} />
                        </button>
                      )}
                    </div>
                  ))}

                  {w.role === "admin" && (
                    <div className="row" style={{ marginTop: 4 }}>
                      <input
                        className="input grow"
                        value={wsInvite.email}
                        onChange={(e) => onWsInvite({ ...wsInvite, email: e.target.value })}
                        placeholder="E-posta ile davet et"
                      />
                      <select
                        className="select"
                        style={{ flex: "0 0 auto", padding: "4px 24px 4px 8px", width: "auto" }}
                        value={wsInvite.role}
                        onChange={(e) => onWsInvite({ ...wsInvite, role: e.target.value })}
                      >
                        <option value="admin">admin</option>
                        <option value="editor">editör</option>
                        <option value="viewer">izleyici</option>
                      </select>
                      <button className="btn sm primary" onClick={() => onInvite(w.id)} disabled={!wsInvite.email.trim()}>
                        Davet
                      </button>
                    </div>
                  )}
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </Accordion>
  );
}

export function ScheduledSection({ tasks, wss, form, onForm, busy, onCreate, onToggle, onDelete }) {
  return (
    <Accordion icon={Icons.team} title="Zamanlanmış Görevler" desc={`Otomatik ajan · ${tasks.length} görev`}>
      <div className="hint" style={{ marginBottom: 9 }}>
        Tekrarlayan ajan görevleri tanımla (ör. her sabah haber özeti). Sunucu zamanı gelince ajanı
        çalıştırır; son sonuç altta görünür. Gateway'de <b>SCHEDULER_ENABLED=1</b> gerekir.
      </div>

      <input
        className="input"
        value={form.title}
        onChange={(e) => onForm({ ...form, title: e.target.value })}
        placeholder="Görev başlığı (ör. Günlük teknoloji özeti)"
      />
      <textarea
        className="textarea"
        style={{ marginTop: 7 }}
        rows={2}
        value={form.prompt}
        onChange={(e) => onForm({ ...form, prompt: e.target.value })}
        placeholder="Ajana verilecek görev (ör. 'Bugünün teknoloji haberlerini web'de ara, 5 maddede özetle')"
      />
      <div className="row wrap" style={{ marginTop: 7 }}>
        <select className="select grow" value={form.schedule} onChange={(e) => onForm({ ...form, schedule: e.target.value })}>
          {SCHEDULE_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
        </select>
        <select className="select grow" value={form.ws} onChange={(e) => onForm({ ...form, ws: e.target.value })}>
          <option value="">Kişisel</option>
          {editable(wss).map((w) => <option key={w.id} value={w.id}>{w.name} (paylaşımlı)</option>)}
        </select>
        <button
          className="btn sm primary"
          onClick={onCreate}
          disabled={busy || !form.title.trim() || form.prompt.trim().length < 3}
        >
          {busy ? "Ekleniyor…" : "Görev Ekle"}
        </button>
      </div>

      {tasks.length > 0 && (
        <div className="list" style={{ marginTop: 11 }}>
          {tasks.map((t) => {
            const ws = t.workspace_id ? wss.find((w) => w.id === t.workspace_id) : null;
            return (
              <div key={t.id} className="list-row" style={{ opacity: t.enabled ? 1 : 0.5 }}>
                <Icons.team size={13} />
                <span className="lr-name" title={t.last_result || t.prompt}>{t.title}</span>
                {t.workspace_id && <span className="lr-meta accent">{ws ? ws.name : "paylaşımlı"}</span>}
                <span className="lr-meta">{t.schedule}{t.last_status ? " · " + t.last_status : ""}</span>
                <button className="icon-act" title={t.enabled ? "Duraklat" : "Etkinleştir"} onClick={() => onToggle(t)}>
                  {t.enabled ? <Icons.pause size={13} /> : <Icons.resume size={13} />}
                </button>
                <button className="icon-act danger" title="Sil" onClick={() => onDelete(t.id)}>
                  <Trash2 size={13} />
                </button>
              </div>
            );
          })}
        </div>
      )}
    </Accordion>
  );
}

export function AgentRunsSection({ runs, onDelete }) {
  if (!runs.length) return null;
  return (
    <Accordion icon={Icons.agent} title="Ajan Çalışma Geçmişi" desc={`${runs.length} koşum`}>
      <div className="hint" style={{ marginBottom: 9 }}>
        Geçmiş <b>Ajan</b>, <b>Takım</b> ve <b>PC devri</b> koşumları — kullanılan araçlar ve sonuç özeti.
        <code className="md-ic">openclaw</code> satırları telefondan devredilen işlerdir.
      </div>
      <div className="list">
        {runs.map((run) => (
          <div key={run.id} className="list-row" title={run.result || ""}>
            {run.mode === "team" ? <Icons.team size={13} /> : <Icons.agent size={13} />}
            <span className="lr-name">{run.prompt || "(boş)"}</span>
            {run.tools && <span className="lr-meta accent">{run.tools}</span>}
            <span className="lr-meta">{run.mode}</span>
            <button className="icon-act danger" onClick={() => onDelete(run.id)}><Trash2 size={13} /></button>
          </div>
        ))}
      </div>
    </Accordion>
  );
}

export function McpSection({ mcp, busy, onRefresh }) {
  if (!mcp || !(mcp.servers || []).length) return null;
  const tools = mcp.tools || [];
  return (
    <Accordion icon={Icons.team} title="MCP Araçları" desc={`${mcp.servers.length} sunucu · ${tools.length} araç`}>
      <div className="hint" style={{ marginBottom: 9 }}>
        <code className="md-ic">MCP_SERVERS</code> ile yapılandırılan sunucular ve ajana açılan araçlar.
        Sunucular gateway env'inde tanımlanır.
      </div>
      <div className="row" style={{ marginBottom: 8 }}>
        <span className="lr-meta grow">Sunucular: {mcp.servers.join(", ")}</span>
        <button className="btn sm" onClick={onRefresh} disabled={busy}>
          <RotateCcw size={13} /> {busy ? "…" : "Yenile"}
        </button>
      </div>
      {tools.length > 0 && (
        <div className="list">
          {tools.map((t) => (
            <div key={t.name} className="list-row" title={t.description}>
              <Icons.team size={13} />
              <span className="lr-name">{t.tool}</span>
              <span className="lr-meta accent">{t.server}</span>
            </div>
          ))}
        </div>
      )}
    </Accordion>
  );
}
