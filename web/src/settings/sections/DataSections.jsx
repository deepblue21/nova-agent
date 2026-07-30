// Bilgi tabanı (RAG), kişisel hafıza ve model kıyas (eval) bölümleri.
// Hepsi gateway oturumu gerektirir; oturum yoksa bölüm hiç render edilmez.
import React from "react";
import { Plus, Trash2 } from "lucide-react";
import { Accordion } from "../../ui/primitives.jsx";
import { Icons } from "../../lib/icons.mjs";
import { fmtNum } from "../../lib/format.mjs";

const wsOptions = (wss) => wss.filter((w) => w.role === "admin" || w.role === "editor");

export function KnowledgeSection({
  docs, wss, kbWs, onKbWs, docTitle, onDocTitle, docText, onDocText,
  docFile, docError, docBusy, onPickFile, onUpload, onDelete, fileRef,
}) {
  return (
    <Accordion icon={Icons.chat} title="Bilgi Tabanı" desc={`Belgelerle sohbet · ${docs.length} belge`}>
      <div className="hint" style={{ marginBottom: 9 }}>
        Belge yükle; <b>Ajan</b> modunda model bu belgelerde arama yapıp kaynak göstererek cevaplar
        (doc_search). Hedef bir <b>çalışma alanı</b> seçersen belge ekibinle paylaşılır.
      </div>

      <div style={{ display: "flex", flexDirection: "column", gap: 7 }}>
        <input
          className="input"
          value={docTitle}
          onChange={(e) => onDocTitle(e.target.value)}
          placeholder="Başlık (opsiyonel)"
        />
        <textarea
          className="textarea"
          rows={3}
          value={docText}
          onChange={(e) => onDocText(e.target.value)}
          placeholder="Metni yapıştır ya da .txt/.md/.pdf/.docx dosyası seç…"
        />
        {docFile && <div className="hint">Seçili dosya: <b>{docFile.name}</b> — metin gateway tarafında çıkarılacak.</div>}
        {docError && <div className="hint err">{docError}</div>}

        <div className="row">
          <select className="select grow" value={kbWs} onChange={(e) => onKbWs(e.target.value)}>
            <option value="">Kişisel (sadece ben)</option>
            {wsOptions(wss).map((w) => <option key={w.id} value={w.id}>{w.name} (paylaşımlı)</option>)}
          </select>
          <input
            ref={fileRef}
            type="file"
            accept=".txt,.md,.markdown,.csv,.json,.log,.pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            style={{ display: "none" }}
            onChange={(e) => { onPickFile(e.target.files[0]); e.target.value = ""; }}
          />
          <button className="btn sm" onClick={() => fileRef.current && fileRef.current.click()}>
            <Plus size={14} /> Dosya
          </button>
          <button
            className="btn sm primary"
            onClick={onUpload}
            disabled={docBusy || (!docFile && docText.trim().length < 20)}
          >
            {docBusy ? "Yükleniyor…" : "Belgeyi Ekle"}
          </button>
        </div>
      </div>

      {docs.length > 0 && (
        <div className="list" style={{ marginTop: 11 }}>
          {docs.map((d) => {
            const ws = d.workspace_id ? wss.find((w) => w.id === d.workspace_id) : null;
            return (
              <div key={d.id} className="list-row">
                <Icons.chat size={13} />
                <span className="lr-name">{d.title}</span>
                {d.workspace_id && <span className="lr-meta accent">{ws ? ws.name : "paylaşımlı"}</span>}
                <span className="lr-meta">{d.chunks} parça</span>
                <button className="icon-act danger" onClick={() => onDelete(d.id)} aria-label="Sil">
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

export function MemorySection({ mems, wss, memWs, onMemWs, memText, onMemText, memBusy, onAdd, onDelete }) {
  return (
    <Accordion icon={Icons.brand} title="Hafıza" desc={`Otomatik hatırlama · ${mems.length} not`}>
      <div className="hint" style={{ marginBottom: 9 }}>
        Hakkında kalıcı notlar ekle (tercih, isim, bağlam); NOVA her sohbette bunları otomatik hatırlar.
        Çalışma alanı seçersen not ekiple paylaşılır.
      </div>

      <textarea
        className="textarea"
        rows={2}
        value={memText}
        onChange={(e) => onMemText(e.target.value)}
        placeholder="Örn: Beni Salih diye çağır. Kotlin/Compose ile çalışıyorum. Kısa ve net cevap severim."
      />
      <div className="row" style={{ marginTop: 7 }}>
        <select className="select grow" value={memWs} onChange={(e) => onMemWs(e.target.value)}>
          <option value="">Kişisel (sadece ben)</option>
          {wsOptions(wss).map((w) => <option key={w.id} value={w.id}>{w.name} (paylaşımlı)</option>)}
        </select>
        <button className="btn sm primary" onClick={onAdd} disabled={memBusy || memText.trim().length < 2}>
          {memBusy ? "Ekleniyor…" : "Hafızaya Ekle"}
        </button>
      </div>

      {mems.length > 0 && (
        <div className="list" style={{ marginTop: 11 }}>
          {mems.map((m) => {
            const ws = m.workspace_id ? wss.find((w) => w.id === m.workspace_id) : null;
            return (
              <div key={m.id} className="list-row">
                <Icons.brand size={13} />
                <span className="lr-name">{m.content}</span>
                {m.workspace_id && <span className="lr-meta accent">{ws ? ws.name : "paylaşımlı"}</span>}
                <button className="icon-act danger" onClick={() => onDelete(m.id)} aria-label="Sil">
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

export function EvalSection({ prompt, onPrompt, modelsText, onModelsText, running, results, onRun }) {
  return (
    <Accordion icon={Icons.models} title="Model Kıyas (Eval)" desc="Aynı promptu birden çok modele gönder">
      <div className="hint" style={{ marginBottom: 9 }}>
        Cevapları gecikme · token · maliyet ile yan yana karşılaştır. Modelleri virgülle ayır
        (örn. <b>ollama/gemma4:e2b, gemini/gemini-2.5-flash</b>).
      </div>

      <textarea
        className="textarea"
        rows={2}
        value={prompt}
        onChange={(e) => onPrompt(e.target.value)}
        placeholder="Kıyaslanacak prompt…"
      />
      <input
        className="input"
        style={{ marginTop: 7 }}
        value={modelsText}
        onChange={(e) => onModelsText(e.target.value)}
        placeholder="model1, model2, …"
      />
      <div className="row end" style={{ marginTop: 7 }}>
        <button className="btn sm primary" onClick={onRun} disabled={running || prompt.trim().length < 2}>
          {running ? "Çalışıyor…" : "Kıyasla"}
        </button>
      </div>

      {results && (
        <div className="list" style={{ marginTop: 10 }}>
          {results.map((r, i) => (
            <div key={i} className="kv-row" style={{ flexDirection: "column", alignItems: "stretch", gap: 6 }}>
              <div style={{ display: "flex", justifyContent: "space-between", gap: 10 }}>
                <span className="k">{r.model}</span>
                <span className="v">
                  {r.ok
                    ? `${fmtNum(r.ms)}ms · ${fmtNum(Number(r.tokens_in) + Number(r.tokens_out))} tok` +
                      `${Number(r.cost_micros) > 0 ? " · $" + (Number(r.cost_micros) / 1e6).toFixed(4) : ""}`
                    : "hata"}
                </span>
              </div>
              <div
                style={{
                  fontFamily: "'Sora',sans-serif",
                  color: r.ok ? "var(--text)" : "var(--danger)",
                  fontSize: 12.5, lineHeight: 1.5, whiteSpace: "pre-wrap",
                  maxHeight: 160, overflowY: "auto",
                }}
              >
                {r.ok ? r.content : r.error}
              </div>
            </div>
          ))}
        </div>
      )}
    </Accordion>
  );
}
