import React, { useEffect, useRef } from "react";
import { Copy, GitBranch, Plus, RotateCcw, Send, Square, Activity, X } from "lucide-react";
import { NovaMark, NovaBadge, NovaThinkingMark } from "../ui/NovaMark.jsx";
import { Markdown } from "../ui/Markdown.jsx";
import { ThinkTrace, ToolTrace } from "../ui/Traces.jsx";
import { SUGGESTIONS } from "../lib/constants.mjs";
import { fmtNum, fmtMs, copyText } from "../lib/format.mjs";

function MessageStats({ m }) {
  if (!m.stats && !m.route) return null;
  const s = m.stats;
  return (
    <div className="msg-meta">
      <span className="chip accent"><GitBranch size={11} /> {(s && s.model) || m.route}</span>
      {s && (
        <>
          <span className="chip"><Activity size={11} /> {fmtMs(s.ms)}</span>
          {s.ttft > 0 && <span className="chip" title="İlk token süresi">⚡ {fmtMs(s.ttft)}</span>}
          <span className="chip">~{fmtNum(s.tok)} tok</span>
          {s.ms > 0 && <span className="chip">{Math.round(s.tok / (s.ms / 1000))} tok/sn</span>}
        </>
      )}
      {m.at && (
        <span className="chip muted time">
          {new Date(m.at).toLocaleTimeString("tr-TR", { hour: "2-digit", minute: "2-digit" })}
        </span>
      )}
    </div>
  );
}

function Message({ m, isLast, busy, onArtifact, onRegenerate }) {
  const isAI = m.role === "assistant";
  const pending = isAI && !m.content && busy && isLast;

  return (
    <div>
      {isAI && m.tools && m.tools.length > 0 && (
        <div className="trace-slot" style={{ marginBottom: 8 }}><ToolTrace tools={m.tools} /></div>
      )}
      {isAI && m.thinking && (
        <div className="trace-slot" style={{ marginBottom: 8 }}>
          <ThinkTrace text={m.thoughts} live={busy && isLast} />
        </div>
      )}

      <div className={"msg " + (m.role === "user" ? "user" : "")}>
        <div className={"avatar " + (m.role === "user" ? "me" : "ai nova-avatar")}>
          {m.role === "user" ? "S" : <NovaMark size={34} animated={pending} />}
        </div>
        <div className={"bubble " + (m.role === "user" ? "me" : "ai")}>
          {m.images && m.images.length > 0 && (
            <div className="msg-imgs">
              {m.images.map((u, ii) => <img key={ii} className="msg-img" src={u} alt="" />)}
            </div>
          )}
          {m.content
            ? (isAI ? <Markdown text={m.content} onArtifact={onArtifact} /> : m.content)
            : (!m.images
                ? (pending
                    ? (
                      <div className="wait">
                        <div className="wait-status">
                          <NovaThinkingMark size={28} /> {m.thoughts ? "Düşünüyor…" : "NOVA yanıt hazırlıyor…"}
                        </div>
                        <div className="wait-line l1" /><div className="wait-line l2" /><div className="wait-line l3" />
                      </div>
                    )
                    : <span className="typing"><span /><span /><span /></span>)
                : null)}
        </div>
      </div>

      {isAI && !(busy && isLast) && <MessageStats m={m} />}

      {isAI && m.content && !(busy && isLast) && (
        <div className="msg-actions">
          <button className="msg-act" onClick={() => copyText(m.content)}><Copy size={13} /> Kopyala</button>
          {isLast && <button className="msg-act" onClick={onRegenerate}><RotateCcw size={13} /> Yeniden</button>}
        </div>
      )}
    </div>
  );
}

export function ChatView({
  messages, busy, greeting, userName, modelName, modelId,
  input, onInput, onSend, onStop, onRegenerate, onArtifact,
  pending, onAddImages, onRemoveImage, imageRouteHint,
}) {
  const scrollRef = useRef(null);
  const fileRef = useRef(null);
  const taRef = useRef(null);

  useEffect(() => {
    if (scrollRef.current) scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
  }, [messages, busy]);

  const grow = (el) => {
    if (!el) return;
    el.style.height = "auto";
    el.style.height = Math.min(el.scrollHeight, 120) + "px";
  };

  return (
    <div className="chat-view">
      {messages.length === 0 ? (
        <div className="hero">
          <NovaBadge size={72} radius={24} halo title="NOVA" />
          <div>
            <div className="hero-title">{greeting}{userName}, ben <em>NOVA</em></div>
            <div className="hero-sub">
              Kişisel ajanın. Şu an <b>{modelName}</b>{modelId ? " · " + modelId : ""} ile hazırım.
              Bir kartla başla ya da alttan yaz.
            </div>
          </div>
          <div className="sugg-grid">
            {SUGGESTIONS.map((s, i) => {
              const Ic = s.icon;
              return (
                <button key={i} className="sugg-card" onClick={() => onSend(s.t)}>
                  <div className="sc-ic"><Ic size={17} /></div>
                  <div>
                    <div className="sc-cat">{s.cat}</div>
                    <div className="sc-t">{s.t}</div>
                    <div className="sc-d">{s.d}</div>
                  </div>
                </button>
              );
            })}
          </div>
        </div>
      ) : (
        <div className="chat-scroll" ref={scrollRef}>
          {messages.map((m, idx) => (
            <Message
              key={idx}
              m={m}
              isLast={idx === messages.length - 1}
              busy={busy}
              onArtifact={onArtifact}
              onRegenerate={onRegenerate}
            />
          ))}
        </div>
      )}

      <div className="composer">
        {pending.length > 0 && (
          <div className="attach-strip">
            {imageRouteHint && <div className="hint" style={{ width: "100%" }}>{imageRouteHint}</div>}
            {pending.map((u, i) => (
              <div className="attach-thumb" key={i}>
                <img src={u} alt="" />
                <button onClick={() => onRemoveImage(i)} aria-label="Görseli kaldır"><X size={11} /></button>
              </div>
            ))}
          </div>
        )}
        <div className="composer-inner">
          <input
            ref={fileRef} type="file" accept="image/*" multiple style={{ display: "none" }}
            onChange={(e) => { onAddImages(e.target.files); e.target.value = ""; }}
          />
          <button className="attach-btn" onClick={() => fileRef.current && fileRef.current.click()} title="Görsel ekle">
            <Plus size={18} />
          </button>
          <textarea
            ref={taRef}
            rows={1}
            value={input}
            data-gramm="false" data-gramm_editor="false" data-enable-grammarly="false"
            onChange={(e) => { onInput(e.target.value); grow(e.target); }}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                onSend();
                if (taRef.current) taRef.current.style.height = "auto";
              }
            }}
            placeholder={"NOVA'ya yaz… (" + (modelId || "model seç") + ")"}
          />
          {busy
            ? <button className="send-btn stop" onClick={onStop} title="Durdur"><Square size={17} /></button>
            : (
              <button
                className="send-btn"
                onClick={() => onSend()}
                disabled={!input.trim() && !pending.length}
                title="Gönder"
              >
                <Send size={18} />
              </button>
            )}
        </div>
      </div>
    </div>
  );
}
