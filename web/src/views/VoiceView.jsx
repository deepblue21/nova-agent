import React, { useRef } from "react";
import { Mic, Send, Square } from "lucide-react";
import { NovaVoiceMark } from "../ui/NovaVoiceMark.jsx";

const LABELS = { idle: "Hazır", listening: "Dinliyorum", thinking: "Düşünüyorum", speaking: "Konuşuyorum" };

/**
 * Ses görünümü.
 *
 * Merkezdeki görsel artık Pulse Aperture marka işaretidir — eski canvas orb'u
 * yedeğe alındı (hooks/legacy/useOrbCanvas.js). İşaret ses seviyesine ve
 * duruma tepki verir; durum sözlüğü Android `VoiceScreen` ile aynıdır.
 * Mikrofon yoksa yazıyla da sesli yanıt alınabilir.
 */
export function VoiceView({
  voiceState, voiceSub, sttSupported, voiceText, onVoiceText, onSubmitText,
  onMic, onStopSpeaking, voiceStateRef, reducedRef, extLevelRef,
}) {
  const waveRef = useRef(null);

  const listening = voiceState === "listening";
  const speaking = voiceState === "speaking";

  return (
    <div className="voice-view">
      <div className="orb-wrap">
        <NovaVoiceMark
          size={200}
          voiceStateRef={voiceStateRef}
          extLevelRef={extLevelRef}
          reducedRef={reducedRef}
          waveRef={waveRef}
        />
      </div>

      <div className="voice-status">
        <div className="vs-label">{LABELS[voiceState]}</div>
        <div className="vs-sub">{voiceSub}</div>
      </div>

      <div className="wavebar" ref={waveRef}>
        {Array.from({ length: 28 }).map((_, i) => <span key={i} style={{ height: 6 }} />)}
      </div>

      <div className="voice-controls">
        <button
          className={"mic-btn" + (listening ? " active" : "") + (speaking ? " speaking" : "")}
          onClick={speaking ? onStopSpeaking : onMic}
          title={speaking ? "Durdur" : listening ? "Dinlemeyi durdur" : "Konuşmak için dokun"}
        >
          {(speaking || listening) ? <Square size={24} /> : <Mic size={26} />}
        </button>
        {speaking && (
          <button className="btn pill" onClick={onStopSpeaking}><Square size={15} /> Durdur</button>
        )}
      </div>

      {(!sttSupported || listening) && (
        <div className="voice-fallback">
          <input
            className="input grow"
            value={voiceText}
            onChange={(e) => onVoiceText(e.target.value)}
            onKeyDown={(e) => { if (e.key === "Enter") onSubmitText(); }}
            placeholder="Mikrofon yoksa buraya yaz, ajan sesli yanıtlasın…"
          />
          <button className="send-btn" onClick={onSubmitText} disabled={!voiceText.trim()}>
            <Send size={18} />
          </button>
        </div>
      )}
    </div>
  );
}
