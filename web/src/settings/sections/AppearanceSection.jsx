// Görünüm + Persona. Aksan temaları design/nova-tokens.json'dan gelir ve
// Android "Görünüm" ayarındaki listeyle birebir aynıdır (aynı id, aynı ad,
// aynı renkler) — tema seçimi iki platformda aynı sonucu verir.
import React from "react";
import { Check } from "lucide-react";
import { ACCENTS } from "../../design/tokens.generated.mjs";
import { PERSONAS } from "../../lib/constants.mjs";
import { normalizeThemeId, themePreviewStyle } from "../../lib/theme.mjs";
import { Accordion, Field } from "../../ui/primitives.jsx";
import { NovaMark } from "../../ui/NovaMark.jsx";
import { Icons } from "../../lib/icons.mjs";

/**
 * Yüzey şeması seçenekleri. "Kağıt" saf beyaz değil sıcak kırık beyazdır:
 * marka işaretinin sıcak çekirdeği saf beyazda görünmez oluyor.
 */
const SCHEMES = [
  { id: "dark", name: "Gece", desc: "koyu zemin · varsayılan" },
  { id: "light", name: "Kağıt", desc: "sıcak kırık beyaz" },
  { id: "auto", name: "Sistem", desc: "işletim sistemini izle" },
];

export function AppearanceSection({ accent, onAccent, scheme = "dark", onScheme, resolvedScheme }) {
  const selectedThemeId = normalizeThemeId(accent);

  return (
    <Accordion
      icon={Icons.brand}
      title="Görünüm"
      desc="Yüzey şeması + aksan teması · mobil uygulamayla ortak"
      defaultOpen
    >
      <div className="section-label" style={{ marginTop: 2 }}>Yüzey</div>
      <div className="scheme-pick-row">
        {SCHEMES.map((s) => (
          <button
            key={s.id}
            type="button"
            className={"scheme-pick" + (scheme === s.id ? " sel" : "")}
            aria-pressed={scheme === s.id}
            onClick={() => onScheme && onScheme(s.id)}
          >
            <span className={"scheme-swatch is-" + s.id} aria-hidden="true" />
            <span className="scheme-pick-copy">
              <span className="scheme-pick-name">{s.name}</span>
              <span className="scheme-pick-meta">
                {s.id === "auto" && resolvedScheme
                  ? `${s.desc} · şu an ${resolvedScheme === "light" ? "kağıt" : "gece"}`
                  : s.desc}
              </span>
            </span>
            {scheme === s.id && <Check size={14} className="theme-pick-check" />}
          </button>
        ))}
      </div>

      <div className="section-label" style={{ marginTop: 16 }}>Aksan</div>
      <div className="hint" style={{ marginBottom: 10 }}>
        Temalar tek kaynaktan (<b>design/nova-tokens.json</b>) üretilir; Android uygulamasındaki
        Görünüm ayarı da aynı listeyi kullanır. On bir temanın hepsi iki yüzey şemasında da çalışır.
      </div>
      <div className="theme-pick-grid">
        {ACCENTS.map((a) => (
          <button
            key={a.id}
            type="button"
            className={
              "theme-pick"
              + (selectedThemeId === a.id ? " sel is-active-preview" : "")
            }
            aria-label={`${a.name} temasını seç`}
            aria-pressed={selectedThemeId === a.id}
            data-theme-id={a.id}
            onClick={() => onAccent(a.id)}
          >
            <span className="theme-pick-mark" style={themePreviewStyle(a.id)} aria-hidden="true">
              <NovaMark
                size={32}
                halo={false}
                animated={selectedThemeId === a.id}
                motion="preview"
              />
            </span>
            <span className="theme-pick-copy">
              <span className="theme-pick-name">{a.name}</span>
              <span className="theme-pick-meta t-mono">{a.primary}</span>
            </span>
            {selectedThemeId === a.id && <Check size={15} className="theme-pick-check" />}
          </button>
        ))}
      </div>
    </Accordion>
  );
}

export function PersonaSection({ personaId, onPersona, customPersona, onCustomPersona }) {
  const active = PERSONAS.find((p) => p.id === personaId) || PERSONAS[0];
  return (
    <Accordion icon={Icons.think} title="Persona / Sistem Prompt" desc={active.name}>
      <div className="pick-grid">
        {PERSONAS.map((p) => {
          const Ic = p.icon;
          return (
            <button
              key={p.id}
              type="button"
              className={"pick" + (p.id === active.id ? " sel" : "")}
              onClick={() => onPersona(p.id)}
            >
              <span className="pk-ic"><Ic size={17} /></span>
              <span>
                <span className="pk-t">{p.name}</span>
                <span className="pk-d">{p.desc}</span>
              </span>
              {p.id === active.id && <Check size={16} className="pk-check" />}
            </button>
          );
        })}
      </div>
      {active.id === "custom" && (
        <div style={{ marginTop: 10 }}>
          <Field label="Özel sistem yönergesi">
            <textarea
              className="textarea"
              rows={4}
              value={customPersona}
              onChange={(e) => onCustomPersona(e.target.value)}
              placeholder="NOVA nasıl davransın? Rol, ton, öncelikler ve kaçınması gereken şeyleri yaz."
            />
          </Field>
        </div>
      )}
    </Accordion>
  );
}
