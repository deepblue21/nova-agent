// Mobil görev kontrol düzlemi. Android `MobileTaskViewModel` ile aynı akış:
// liste → seç → SSE olay akışı (yeniden bağlanmada Last-Event-ID ile devam) →
// duraklat/sürdür/iptal → risk onayı.
import { useCallback, useEffect, useRef, useState } from "react";
import {
  listMobileTasks, getMobileTask, createMobileTask, commandMobileTask,
  resolveMobileConfirmation, streamMobileTaskEvents,
} from "../lib/gateway.mjs";
import { isTerminal, pendingConfirmation, normalizeStatus } from "../lib/tasks.mjs";

const EMPTY = { tasks: [], events: [], confirmation: null };

export function useMobileTasks(prov, { enabled = true } = {}) {
  const [tasks, setTasks] = useState(EMPTY.tasks);
  const [activeId, setActiveId] = useState(null);
  const [events, setEvents] = useState(EMPTY.events);
  const [confirmation, setConfirmation] = useState(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(false);

  const closeRef = useRef(null);
  const lastEventIdRef = useRef(0);
  const key = (prov && prov.apiKey) || "";
  const base = (prov && prov.baseUrl) || "";

  const active = tasks.find((t) => t.id === activeId) || null;

  const reload = useCallback(async () => {
    if (!enabled || !key) return;
    setLoading(true);
    try {
      const data = await listMobileTasks(prov);
      setTasks(data);
      setActiveId((cur) => (cur && data.some((t) => t.id === cur) ? cur : (data[0] && data[0].id) || null));
      setError("");
    } catch (e) {
      setError("Görev listesi alınamadı");
    } finally {
      setLoading(false);
    }
  }, [enabled, key, base]);   // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => { reload(); }, [reload]);

  const patchTask = useCallback((task) => {
    if (!task || !task.id) return;
    setTasks((prev) => {
      const i = prev.findIndex((t) => t.id === task.id);
      if (i < 0) return [task, ...prev];
      const next = [...prev];
      next[i] = { ...next[i], ...task };
      return next;
    });
  }, []);

  /* ------------------------------ olay akışı ----------------------------- */

  useEffect(() => {
    if (closeRef.current) { closeRef.current(); closeRef.current = null; }
    setEvents([]);
    setConfirmation(null);
    lastEventIdRef.current = 0;
    if (!enabled || !key || !activeId) return undefined;

    let cancelled = false;
    let retry;

    const connect = () => {
      closeRef.current = streamMobileTaskEvents(prov, activeId, {
        lastEventId: lastEventIdRef.current || undefined,
        onEvent: (ev) => {
          if (cancelled) return;
          if (ev && ev.id) lastEventIdRef.current = ev.id;
          setEvents((prev) => (prev.some((p) => p.id === ev.id) ? prev : [...prev, ev]));
          const conf = pendingConfirmation(ev);
          if (conf) setConfirmation(conf);
          if (ev && (ev.type === "confirmation.approved" || ev.type === "confirmation.rejected")) {
            setConfirmation(null);
          }
          const status = normalizeStatus(ev && (ev.status || (ev.data && ev.data.status)));
          if (status) patchTask({ id: activeId, status });
        },
        onError: (msg) => {
          if (cancelled) return;
          setError(msg);
          // Terminal olmayan görevlerde kısa gecikmeyle yeniden bağlan.
          const cur = normalizeStatus((tasks.find((t) => t.id === activeId) || {}).status);
          if (!isTerminal(cur)) retry = setTimeout(connect, 3000);
        },
      });
    };
    connect();

    return () => {
      cancelled = true;
      if (retry) clearTimeout(retry);
      if (closeRef.current) { closeRef.current(); closeRef.current = null; }
    };
  }, [enabled, key, base, activeId]);   // eslint-disable-line react-hooks/exhaustive-deps

  /* -------------------------------- eylemler ----------------------------- */

  const create = useCallback(async (prompt) => {
    const text = String(prompt || "").trim();
    if (!key || !text || busy) return { ok: false, error: "Görev metni gerekli" };
    setBusy(true);
    setError("");
    try {
      const r = await createMobileTask(prov, text);
      if (!r.ok) { setError(r.error); return r; }
      patchTask(r.data);
      setActiveId(r.data.id);
      return r;
    } finally {
      setBusy(false);
    }
  }, [key, base, busy, patchTask]);   // eslint-disable-line react-hooks/exhaustive-deps

  const command = useCallback(async (cmd) => {
    if (!key || !activeId || busy) return { ok: false };
    setBusy(true);
    try {
      const r = await commandMobileTask(prov, activeId, cmd);
      if (r.ok) patchTask(r.data);
      else setError(r.status === 409 ? "Görev bu komutu şu an kabul etmiyor" : r.error);
      return r;
    } finally {
      setBusy(false);
    }
  }, [key, base, activeId, busy, patchTask]);   // eslint-disable-line react-hooks/exhaustive-deps

  const decide = useCallback(async (decision) => {
    if (!key || !activeId || !confirmation || busy) return { ok: false };
    setBusy(true);
    try {
      const r = await resolveMobileConfirmation(prov, activeId, confirmation.id, decision);
      if (r.ok) { patchTask(r.data); setConfirmation(null); }
      else setError(r.error);
      return r;
    } finally {
      setBusy(false);
    }
  }, [key, base, activeId, confirmation, busy, patchTask]);   // eslint-disable-line react-hooks/exhaustive-deps

  const refreshActive = useCallback(async () => {
    if (!key || !activeId) return;
    const t = await getMobileTask(prov, activeId);
    if (t) patchTask(t);
  }, [key, base, activeId, patchTask]);   // eslint-disable-line react-hooks/exhaustive-deps

  return {
    tasks, active, activeId, setActiveId,
    events, confirmation, error, busy, loading,
    reload, create, command, decide, refreshActive,
    clearError: () => setError(""),
  };
}
