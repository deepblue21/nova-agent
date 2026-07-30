// Ayarlar penceresi. Bölümler ayrı dosyalarda; burada yalnız kabuk ve
// hangi bölümün ne zaman görüneceği kuralları var. Gateway oturumu
// gerektiren bölümler oturum yokken hiç render edilmez (boş kutu gösterilmez).
import React from "react";
import { X } from "lucide-react";
import { Icons } from "../lib/icons.mjs";
import { AppearanceSection, PersonaSection } from "./sections/AppearanceSection.jsx";
import { UsageSection } from "./sections/UsageSection.jsx";
import { KnowledgeSection, MemorySection, EvalSection } from "./sections/DataSections.jsx";
import { WorkspacesSection, ScheduledSection, AgentRunsSection, McpSection } from "./sections/TeamSections.jsx";
import { ProvidersSection, VoiceSection, AgentLayerSection } from "./sections/ConnectionSections.jsx";

export function SettingsModal({ onClose, signedIn, ...p }) {
  return (
    <div className="overlay" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()} role="dialog" aria-label="Ayarlar">
        <div className="modal-head">
          <div>
            <h2><Icons.settings size={19} color="var(--accent)" /> Ayarlar</h2>
            <div className="m-sub">Görünüm, persona, sağlayıcılar, bilgi tabanı ve takım ayarları.</div>
          </div>
          <button className="icon-btn" onClick={onClose} aria-label="Kapat"><X size={18} /></button>
        </div>

        <div className="m-section">
          <div className="card accent" style={{ padding: 14, borderRadius: 13 }}>
            <div className="hint" style={{ color: "color-mix(in srgb, var(--accent) 35%, #fff)" }}>
              <b>Önerilen kurulum:</b> Gateway üzerinden bağlan ve <b>Keycloak ile Giriş</b> yap —
              anahtar yapıştırmaya gerek kalmaz, oturum otomatik tazelenir, sohbet geçmişin sunucuya da
              yazılır. Ayarlar bu tarayıcıda kalıcıdır (IndexedDB).
            </div>
          </div>
        </div>

        <div className="m-section">
          <AppearanceSection
            accent={p.accent} onAccent={p.onAccent}
            scheme={p.scheme} onScheme={p.onScheme} resolvedScheme={p.resolvedScheme}
          />

          <PersonaSection
            personaId={p.personaId} onPersona={p.onPersona}
            customPersona={p.customPersona} onCustomPersona={p.onCustomPersona}
          />

          <ProvidersSection
            providers={p.providers} onProv={p.onProv} provReady={p.provReady}
            auth={p.auth} onLogin={p.onLogin} onLogout={p.onLogout} health={p.health}
          />

          {signedIn && <UsageSection usage={p.usage} />}

          {signedIn && (
            <KnowledgeSection
              docs={p.docs} wss={p.wss} kbWs={p.kbWs} onKbWs={p.onKbWs}
              docTitle={p.docTitle} onDocTitle={p.onDocTitle}
              docText={p.docText} onDocText={p.onDocText}
              docFile={p.docFile} docError={p.docError} docBusy={p.docBusy}
              onPickFile={p.onPickDocFile} onUpload={p.onUploadDoc} onDelete={p.onDeleteDoc}
              fileRef={p.docFileRef}
            />
          )}

          {signedIn && (
            <MemorySection
              mems={p.mems} wss={p.wss} memWs={p.memWs} onMemWs={p.onMemWs}
              memText={p.memText} onMemText={p.onMemText} memBusy={p.memBusy}
              onAdd={p.onAddMem} onDelete={p.onDeleteMem}
            />
          )}

          {signedIn && (
            <EvalSection
              prompt={p.evalPrompt} onPrompt={p.onEvalPrompt}
              modelsText={p.evalModelsText} onModelsText={p.onEvalModelsText}
              running={p.evalRunning} results={p.evalResults} onRun={p.onRunEval}
            />
          )}

          {signedIn && (
            <WorkspacesSection
              wss={p.wss} wsName={p.wsName} onWsName={p.onWsName} onCreate={p.onCreateWs}
              wsOpen={p.wsOpen} wsMembers={p.wsMembers} onToggleMembers={p.onToggleMembers}
              wsInvite={p.wsInvite} onWsInvite={p.onWsInvite} onInvite={p.onInviteMember}
              onChangeRole={p.onChangeRole} onRemoveMember={p.onRemoveMember}
            />
          )}

          {signedIn && (
            <ScheduledSection
              tasks={p.schedTasks} wss={p.wss} form={p.schedForm} onForm={p.onSchedForm}
              busy={p.schedBusy} onCreate={p.onCreateSched} onToggle={p.onToggleSched} onDelete={p.onDeleteSched}
            />
          )}

          {signedIn && <AgentRunsSection runs={p.agentRuns} onDelete={p.onDeleteAgentRun} />}
          {signedIn && <McpSection mcp={p.mcpInfo} busy={p.mcpBusy} onRefresh={p.onLoadMcp} />}

          <VoiceSection voiceCfg={p.voiceCfg} onVoiceCfg={p.onVoiceCfg} />

          <AgentLayerSection
            agent={p.agent} onAgent={p.onAgent}
            agentUrl={p.agentUrl} onAgentUrl={p.onAgentUrl}
          />
        </div>
      </div>
    </div>
  );
}
