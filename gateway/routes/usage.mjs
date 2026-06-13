// Self-service usage + quota: the UI's settings panel shows this. Requires
// req.principal (mount after principal()). Month window = current calendar month.
import { Router } from "express";
import { q } from "../lib/db.mjs";

export const usage = Router();

usage.get("/v1/usage", async (req, res) => {
  try {
    const uid = req.principal.userId;
    const { rows: byModel } = await q(
      `SELECT model,
              SUM(tokens_in)::bigint  AS tokens_in,
              SUM(tokens_out)::bigint AS tokens_out,
              SUM(cost_micros)::bigint AS cost_micros,
              COUNT(*)::int AS requests
         FROM usage_events
        WHERE user_id = $1 AND created_at >= date_trunc('month', now())
        GROUP BY model ORDER BY SUM(cost_micros) DESC, SUM(tokens_out) DESC`, [uid]);
    const totals = byModel.reduce((a, r) => ({
      tokens_in:  a.tokens_in  + Number(r.tokens_in),
      tokens_out: a.tokens_out + Number(r.tokens_out),
      cost_micros: a.cost_micros + Number(r.cost_micros),
      requests:   a.requests + Number(r.requests),
    }), { tokens_in: 0, tokens_out: 0, cost_micros: 0, requests: 0 });
    const { rows: qr } = await q(
      "SELECT limit_micros, used_micros, resets_at, period FROM quotas WHERE subject_id = $1", [uid]);
    res.json({ month: { ...totals, by_model: byModel }, quota: qr[0] || null });
  } catch (e) {
    req.log?.error?.({ err: e.message }, "usage endpoint failed");
    res.status(500).json({ error: "usage unavailable" });
  }
});
