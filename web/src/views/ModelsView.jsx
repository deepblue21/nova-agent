// Modeller — Android `ModelsScreen`'in web karşılığı. Canlı gateway kataloğu
// gruplar hâlinde gösterilir; kullanılamayan modeller pasif kalır ve nedeni
// yazılır. Cihaz-üstü indirme/doğrulama telefona özgüdür: web'de taklit
// edilmez, ayrı bir kartta açıkça belirtilir.
import React from "react";
import { Check, ChevronRight, RotateCcw } from "lucide-react";
import { Card, Chip, SectionLabel, EmptyPanel } from "../ui/primitives.jsx";
import { Icons } from "../lib/icons.mjs";

function ModelCard({ item, selected, onPick }) {
  const off = item.available === false;
  const Ic = item.icon;
  return (
    <button
      type="button"
      className={"pick" + (selected ? " sel" : "") + (off ? " disabled" : "")}
      title={off ? item.reason : item.desc}
      aria-disabled={off}
      onClick={() => { if (!off) onPick(item.id); }}
    >
      <span className="pk-ic"><Ic size={17} /></span>
      <span>
        <span className="pk-t">
          {item.name}
          {item.tools && (
            <span
              className="tools-badge"
              style={{ marginLeft: 7 }}
              title={item.toolsVerified
                ? "Araç çağırmayı destekliyor (Ollama'ya soruldu). Seçilince ajan modu açılır."
                : "Aile tahminine göre araç destekliyor — Ollama'ya sorulamadı."}
            >
              {item.toolsVerified ? "araç destekli" : "araç destekli?"}
            </span>
          )}
          {item.thinking && (
            <span
              className="tools-badge thinking-badge"
              style={{ marginLeft: 7 }}
              title={item.thinkingVerified
                ? "Düşünme çıktısını destekliyor (Ollama'ya soruldu)."
                : "Aile bilgisine göre düşünme destekliyor — Ollama'ya sorulamadı."}
            >
              {item.thinkingVerified ? "düşünme" : "düşünme?"}
            </span>
          )}
        </span>
        <span className="pk-d">{off ? (item.reason || "kullanılamıyor") : item.desc}</span>
      </span>
      {selected && !off && <Check size={16} className="pk-check" />}
    </button>
  );
}

export function ModelsView({
  groups, modelId, onPick, live, err, onRefresh,
  health, defaultModel, onOpenChat, ollama,
}) {
  const total = groups.reduce((n, g) => n + g.items.length, 0);
  const usable = groups.reduce((n, g) => n + g.items.filter((i) => i.available !== false).length, 0);

  // Yerel modeller katalogdaki "ollama" sağlayıcısından sayılır; elle giriş yok.
  const ollamaCount = groups.reduce(
    (n, g) => n + g.items.filter((i) => i.srcProvider === "ollama").length,
    0,
  );
  const ollamaOk = !ollama || ollama.ok !== false;
  const ollamaError = ollama && ollama.error;

  return (
    <div className="panel-scroll view-enter">
      <div className="row" style={{ justifyContent: "space-between" }}>
        <SectionLabel>Katalog</SectionLabel>
        <button className="btn sm ghost" onClick={onRefresh}><RotateCcw size={13} /> Yenile</button>
      </div>

      <Card>
        <div className="card-head">
          <div className="ch-ic"><Icons.models size={18} /></div>
          <div className="ch-tx">
            <div className="ch-t">{live ? "Canlı liste · gateway" : "Yedek liste · gateway'e ulaşılamadı"}</div>
            <div className="ch-d">
              {live
                ? `${usable}/${total} model kullanılabilir durumda.`
                : "Gateway açılınca gerçek katalog (Ollama'da yüklü modeller + anahtarı olan bulut sağlayıcıları) gelir."}
            </div>
          </div>
          <span className={"status-dot" + (live ? " ok" : " err")} />
        </div>
        {err && <div className="hint err">{err}</div>}
        {health && (
          <div className="row wrap">
            <Chip tone="accent">varsayılan: {health.default || defaultModel || "?"}</Chip>
            <Chip>görsel: {health.vision || "?"}</Chip>
          </div>
        )}
      </Card>

      {groups.length === 0 ? (
        <EmptyPanel icon={Icons.models} title="Model bulunamadı" desc="Gateway kataloğu boş döndü." />
      ) : (
        groups.map((g) => (
          <div key={g.group}>
            <SectionLabel>{g.group}</SectionLabel>
            <div className="pick-grid">
              {g.items.map((it) => (
                <ModelCard key={it.id} item={it} selected={it.id === modelId} onPick={onPick} />
              ))}
            </div>
          </div>
        ))
      )}

      <div>
        <SectionLabel>Yerel modeller · Ollama</SectionLabel>
        <Card>
          <div className="card-head">
            <div className="ch-ic"><Icons.cpu size={18} /></div>
            <div className="ch-tx">
              <div className="ch-t">
                {ollamaCount > 0
                  ? `PC'deki Ollama'da ${ollamaCount} model yüklü`
                  : "Ollama listesi boş"}
              </div>
              <div className="ch-d">
                {ollamaOk
                  ? "Liste PC'deki Ollama'dan canlı okunuyor; yukarıdaki karttan seç. Elle model adı yazmana gerek yok."
                  : (ollamaError || "Gateway Ollama'ya ulaşamadı.")}
              </div>
            </div>
            <span className={"status-dot" + (ollamaOk && ollamaCount > 0 ? " ok" : " err")} />
          </div>

          {ollamaCount === 0 && (
            <div className="hint">
              PC'de <code className="md-ic">ollama pull qwen3:8b</code> gibi bir model indir, sonra
              <b> Yenile</b>'ye bas. Yeni indirilen modeller bu ekran her açıldığında da tazelenir.
            </div>
          )}

          <div className="row">
            <button className="btn sm" onClick={onRefresh}><RotateCcw size={13} /> Listeyi yenile</button>
          </div>
        </Card>
      </div>

      <div>
        <SectionLabel>Cihazdaki modeller</SectionLabel>
        <Card>
          <div className="card-head">
            <div className="ch-ic"><Icons.local size={18} /></div>
            <div className="ch-tx">
              <div className="ch-t">Telefona özgü</div>
              <div className="ch-d">
                İndirme, SHA-256 doğrulama ve cihaz-üstü çalıştırma yalnız Android istemcisinde vardır
                (LiteRT-LM). Tarayıcıda yerel motor yok; bu yüzden burada taklit edilmiyor.
              </div>
            </div>
          </div>
          <div className="row wrap">
            <Chip tone="warn">web'de kullanılamaz</Chip>
            <Chip>Android · Modeller sekmesi</Chip>
          </div>
        </Card>
      </div>

      <button className="btn primary block" onClick={onOpenChat}>
        Seçili modelle sohbete geç <ChevronRight size={14} />
      </button>
    </div>
  );
}
