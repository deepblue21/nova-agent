import React from "react";
import { Accordion, Meter } from "../../ui/primitives.jsx";
import { Icons } from "../../lib/icons.mjs";
import { fmtNum } from "../../lib/format.mjs";

/** Kullanım paneli — /v1/usage. Oturum yoksa hiç gösterilmez. */
export function UsageSection({ usage }) {
  if (!usage || !usage.month) return null;
  const m = usage.month;
  const q = usage.quota;

  return (
    <Accordion icon={Icons.check} title="Kullanım — Bu Ay" desc={`${fmtNum(m.requests)} istek`}>
      <div className="stat-grid">
        <div className="stat-box"><div className="sv">{fmtNum(m.tokens_in)}</div><div className="sl">giren token</div></div>
        <div className="stat-box"><div className="sv">{fmtNum(m.tokens_out)}</div><div className="sl">çıkan token</div></div>
        <div className="stat-box"><div className="sv">{fmtNum(m.requests)}</div><div className="sl">istek</div></div>
        <div className="stat-box"><div className="sv">${(m.cost_micros / 1e6).toFixed(4)}</div><div className="sl">maliyet</div></div>
      </div>

      {q && Number(q.limit_micros) > 0 && (
        <div style={{ marginTop: 10 }}>
          <Meter
            value={q.used_micros}
            max={q.limit_micros}
            note={`Kota: $${(Number(q.used_micros) / 1e6).toFixed(2)} / $${(Number(q.limit_micros) / 1e6).toFixed(2)} · yenileme ${new Date(q.resets_at).toLocaleDateString("tr-TR")}`}
          />
        </div>
      )}

      {m.by_model && m.by_model.length > 0 && (
        <div className="list" style={{ marginTop: 10 }}>
          {m.by_model.map((row) => (
            <div key={row.model} className="kv-row">
              <span className="k">{row.model}</span>
              <span className="v">
                {fmtNum(Number(row.tokens_in) + Number(row.tokens_out))} tok · {fmtNum(row.requests)} istek
                {Number(row.cost_micros) > 0 ? " · $" + (Number(row.cost_micros) / 1e6).toFixed(4) : ""}
              </span>
            </div>
          ))}
        </div>
      )}
    </Accordion>
  );
}
