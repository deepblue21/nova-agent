// NOVA Agent — kök bileşen.
//
// Bilgi mimarisi mobil uygulamayla ortak: Kontrol / İşler / Sohbet / Modeller
// (+ Sohbet üst çubuğundan açılan Ses). Renk, ikon, hareket ve durum sözlüğü
// design/nova-tokens.json ile lib/tasks.mjs üzerinden iki platformda ortaktır.
//
// Bu dosya durum ve iş akışını tutar; çizim işini views/ · layout/ · ui/
// altındaki bileşenler yapar.
import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";

import { NOVA_CSS } from "../styles/index.mjs";
import { ACCENTS, DEFAULT_ACCENT } from "../design/tokens.generated.mjs";
import { normalizeThemeId } from "../lib/theme.mjs";
import { Icons } from "../lib/icons.mjs";

import { store, STATE_KEY, AUTH_KEY } from "../lib/store.mjs";
import { newId, trim, delay, blobToB64, b64ToArrayBuffer } from "../lib/format.mjs";
import { streamChat, errHint, needsLiveTool } from "../lib/stream.mjs";
import { extractWebsite } from "../lib/site.mjs";
import { prepareArtifact } from "../lib/artifact.mjs";
import {
  exportChat as exportChatFile, shareChat as shareChatLink,
  convFromSharePayload, readSharePayloadFromHash, clearShareHash,
} from "../lib/share.mjs";
import {
  OIDC, pkcePair, authorizeUrl, exchangeCode, refresh as refreshTokens, sessionFromTokens,
} from "../lib/oidc.mjs";
import * as api from "../lib/gateway.mjs";
import {
  FALLBACK_MODELS, liveGroupsFrom, EFFORTS, PERSONAS, AGENTS,
} from "../lib/constants.mjs";

import { useReducedMotion } from "../hooks/useReducedMotion.js";
import { useMobileTasks } from "../hooks/useMobileTasks.js";

import { SideRail, BottomNav, ConversationsDrawer } from "../layout/SideRail.jsx";
import { TopBar } from "../layout/TopBar.jsx";
import { Dock } from "../layout/Dock.jsx";
import { ArtifactPanel } from "../layout/ArtifactPanel.jsx";
import { ChatView } from "../views/ChatView.jsx";
import { VoiceView } from "../views/VoiceView.jsx";
import { ControlView } from "../views/ControlView.jsx";
import { TasksView } from "../views/TasksView.jsx";
import { ModelsView } from "../views/ModelsView.jsx";
import { SettingsModal } from "../settings/SettingsModal.jsx";

const CHAT_VIEWS = new Set(["sohbet", "ses"]);

/** Vite dev/preview portları — burada gateway ayrı origin'dedir. */
const DEV_PORTS = new Set(["5173", "4173"]);

/**
 * Varsayılan gateway adresi.
 *
 * Caddy `/v1/*`, `/health`, `/stt`, `/tts` yollarını gateway'e aynı origin
 * üzerinden proxy'liyor. Bu yüzden üretim sunumunda (port 80/443) göreli
 * `/v1` kullanılır: CORS yok, Tailscale/LAN üzerinden de adres elle
 * değiştirilmeden çalışır. Yalnız vite dev sunucusunda mutlak adres gerekir.
 */
function defaultGatewayBase() {
  if (typeof window === "undefined") return "http://localhost:8088/v1";
  return DEV_PORTS.has(window.location.port) ? "http://localhost:8088/v1" : "/v1";
}

export default function NovaAgent() {
  /* ------------------------------ görünüm -------------------------------- */
  const [view, setView] = useState("ses");
  const [accent, setAccent] = useState(DEFAULT_ACCENT);
  // Yüzey şeması: "dark" (varsayılan) | "light" (Kağıt) | "auto" (sistem).
  const [scheme, setScheme] = useState("dark");
  const [systemLight, setSystemLight] = useState(false);
  const [openDD, setOpenDD] = useState(null);
  const [showSettings, setShowSettings] = useState(false);
  const [showDrawer, setShowDrawer] = useState(false);

  /* ------------------------------ model ---------------------------------- */
  // Boş = kullanıcı henüz seçmedi. Gateway'e bağlanınca canlı listenin
  // varsayılanı atanır; "Dinamik Yönlendirme" kendiliğinden seçili gelmez.
  const [modelId, setModelId] = useState("");
  const [liveCatalog, setLiveCatalog] = useState(null);
  const [liveModelsErr, setLiveModelsErr] = useState("");
  const [effort, setEffort] = useState("balanced");
  const [reasoning, setReasoning] = useState(true);
  const [personaId, setPersonaId] = useState("nova");
  const [customPersona, setCustomPersona] = useState("");
  const [agentMode, setAgentMode] = useState(false);   // araç çağırma
  const [teamMode, setTeamMode] = useState(false);     // paralel alt-ajanlar
  const [target, setTarget] = useState("GATEWAY_ONLY");

  /* ---------------------------- sağlayıcılar ----------------------------- */
  const [providers, setProviders] = useState({
    gateway: { kind: "openai", baseUrl: defaultGatewayBase(), apiKey: "" },
    ollama: { kind: "ollama", baseUrl: "http://localhost:11434", apiKey: "" },
    anthropic: { kind: "anthropic", baseUrl: "https://api.anthropic.com", apiKey: "" },
    gemini: { kind: "gemini", baseUrl: "https://generativelanguage.googleapis.com", apiKey: "" },
    openai: { kind: "openai", baseUrl: "https://api.openai.com/v1", apiKey: "" },
  });
  const setProv = (id, patch) => setProviders((p) => ({ ...p, [id]: { ...p[id], ...patch } }));
  const [agent, setAgent] = useState("direct");
  const [agentUrl, setAgentUrl] = useState("");

  /* ------------------------------- oturum -------------------------------- */
  const [auth, setAuth] = useState(null);
  const [usageInfo, setUsageInfo] = useState(null);
  const [gatewayInfo, setGatewayInfo] = useState(null);
  const [healthErr, setHealthErr] = useState("");

  /* ---------------------------- gateway verisi --------------------------- */
  const [docs, setDocs] = useState([]);
  const [kbWs, setKbWs] = useState("");
  const [docText, setDocText] = useState("");
  const [docTitle, setDocTitle] = useState("");
  const [docFile, setDocFile] = useState(null);
  const [docError, setDocError] = useState("");
  const [docBusy, setDocBusy] = useState(false);
  const docFileRef = useRef(null);

  const [mems, setMems] = useState([]);
  const [memText, setMemText] = useState("");
  const [memWs, setMemWs] = useState("");
  const [memBusy, setMemBusy] = useState(false);

  const [evalPrompt, setEvalPrompt] = useState("");
  const [evalModelsText, setEvalModelsText] = useState("ollama/gemma4:e2b, ollama/qwen3.6:35b");
  const [evalRunning, setEvalRunning] = useState(false);
  const [evalResults, setEvalResults] = useState(null);

  const [wss, setWss] = useState([]);
  const [wsName, setWsName] = useState("");
  const [wsOpen, setWsOpen] = useState(null);
  const [wsMembers, setWsMembers] = useState([]);
  const [wsInvite, setWsInvite] = useState({ email: "", role: "viewer" });

  const [schedTasks, setSchedTasks] = useState([]);
  const [schedForm, setSchedForm] = useState({ title: "", prompt: "", schedule: "daily:09:00", ws: "" });
  const [schedBusy, setSchedBusy] = useState(false);

  const [agentRuns, setAgentRuns] = useState([]);
  const [mcpInfo, setMcpInfo] = useState(null);
  const [mcpBusy, setMcpBusy] = useState(false);

  /* ------------------------------ sohbetler ------------------------------ */
  const [convs, setConvs] = useState([]);
  const [activeId, setActiveId] = useState(null);
  const [hydrated, setHydrated] = useState(false);
  const [convSearch, setConvSearch] = useState("");
  const [shareNote, setShareNote] = useState("");
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);
  const [pending, setPending] = useState([]);
  const [artifact, setArtifact] = useState(null);

  /* -------------------------------- ses ---------------------------------- */
  const [voiceState, setVoiceState] = useState("idle");
  const [voiceSub, setVoiceSub] = useState("Konuşmak için mikrofona dokun");
  const [sttSupported, setSttSupported] = useState(false);
  const [voiceText, setVoiceText] = useState("");
  const [voiceCfg, setVoiceCfg] = useState({
    real: false, queued: false, sttUrl: "/stt", ttsUrl: "/tts",
    jobUrl: "/v1/voice/jobs", voice: "alloy",
  });
  const setVc = (patch) => setVoiceCfg((v) => ({ ...v, ...patch }));

  const voiceStateRef = useRef("idle");
  const { ref: reducedRef } = useReducedMotion();
  const extLevelRef = useRef(-1);
  const recogRef = useRef(null);
  const abortRef = useRef(null);
  const audioCtxRef = useRef(null);
  const mediaRecRef = useRef(null);
  const mediaStreamRef = useRef(null);
  const meterRafRef = useRef(null);
  const ttsAudioRef = useRef(null);

  const accentRef = useRef(ACCENTS.find((a) => a.id === DEFAULT_ACCENT) || ACCENTS[0]);
  useEffect(() => {
    accentRef.current = ACCENTS.find((a) => a.id === accent) || ACCENTS[0];
  }, [accent]);

  useEffect(() => { voiceStateRef.current = voiceState; }, [voiceState]);

  // "auto" seçiliyken işletim sisteminin açık/koyu tercihini izle.
  useEffect(() => {
    const mq = window.matchMedia("(prefers-color-scheme: light)");
    const apply = () => setSystemLight(mq.matches);
    apply();
    if (mq.addEventListener) mq.addEventListener("change", apply);
    else mq.addListener(apply);
    return () => {
      if (mq.removeEventListener) mq.removeEventListener("change", apply);
      else mq.removeListener(apply);
    };
  }, []);
  useEffect(() => {
    const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
    setSttSupported(!!SR);
  }, []);

  /* --------------------------- türetilmiş durum -------------------------- */

  // "auto" işletim sistemi tercihine uyar; diğerleri doğrudan uygulanır.
  const resolvedScheme = scheme === "auto" ? (systemLight ? "light" : "dark") : scheme;

  const gw = providers.gateway;
  const signedIn = !!gw.apiKey;

  const active = convs.find((c) => c.id === activeId) || null;
  const messages = active ? active.messages : [];

  const setMessages = useCallback((updater) => {
    setConvs((prev) => prev.map((c) => {
      if (c.id !== activeId) return c;
      const nm = typeof updater === "function" ? updater(c.messages) : updater;
      let title = c.title;
      if (title === "Yeni sohbet" || !title) {
        const u = nm.find((m) => m.role === "user");
        if (u) title = u.content.slice(0, 42);
      }
      return { ...c, messages: nm, title, updatedAt: Date.now() };
    }));
  }, [activeId]);

  const MODELS = useMemo(
    () => (liveCatalog ? liveGroupsFrom(liveCatalog) : FALLBACK_MODELS),
    [liveCatalog],
  );
  const MODEL_FLAT = useMemo(() => MODELS.flatMap((g) => g.items), [MODELS]);

  const curItem = (MODEL_FLAT.find((m) => m.id === modelId)
      || MODEL_FLAT.find((m) => m.available !== false)
      || MODEL_FLAT[0]
      || { id: "auto", name: "Model seçilmedi", desc: "", icon: Icons.brand, provider: "gateway", model: "auto" });

  const curProv = providers[curItem.provider];
  const curApiModel = curItem.model;
  const curEffort = EFFORTS.find((e) => e.id === effort) || EFFORTS[1];
  const activePersona = PERSONAS.find((p) => p.id === personaId) || PERSONAS[0];

  const provReady = useCallback((id) => {
    const p = providers[id];
    if (id === "ollama" || id === "gateway") return !!p.baseUrl;
    if (id === "anthropic") return true;
    return !!p.apiKey;
  }, [providers]);
  const ready = provReady(curItem.provider);

  const visionRoute = (gatewayInfo && gatewayInfo.vision) || "VISION_MODEL";
  const imageRouteHint = pending.length > 0 && curItem.provider === "gateway" && curApiModel === "auto"
    ? "Görsel auto route: " + visionRoute
    : "";

  /* ---------------------------- mobil görevler --------------------------- */

  const tasks = useMobileTasks(gw, { enabled: signedIn });
  const runningTaskCount = tasks.tasks.filter(
    (t) => !["completed", "failed", "cancelled"].includes(String(t.status || "").toLowerCase()),
  ).length;

  /* ------------------------------- yükle --------------------------------- */

  useEffect(() => {
    (async () => {
      const imported = convFromSharePayload(readSharePayloadFromHash());
      if (imported) { clearShareHash(); setView("sohbet"); }
      const raw = await store.get(STATE_KEY);
      if (raw) {
        try {
          const s = JSON.parse(raw);
          const g = s.settings;
          if (g) {
            // Elle model adı girme kaldırıldı; eski "custom" kaydı temizlenir,
            // canlı listenin varsayılanı uygulanır.
            if (g.modelId && g.modelId !== "custom") setModelId(g.modelId);
            if (g.effort) setEffort(g.effort);
            if (typeof g.reasoning === "boolean") setReasoning(g.reasoning);
            if (g.personaId && PERSONAS.some((p) => p.id === g.personaId)) setPersonaId(g.personaId);
            if (typeof g.customPersona === "string") setCustomPersona(g.customPersona);
            if (typeof g.agentMode === "boolean") setAgentMode(g.agentMode);
            if (typeof g.teamMode === "boolean") setTeamMode(g.teamMode);
            if (g.agent) setAgent(g.agent);
            if (g.agentUrl) setAgentUrl(g.agentUrl);
            if (g.accent) setAccent(normalizeThemeId(g.accent));
            if (g.target) setTarget(g.target);
            if (g.view && ["kontrol", "isler", "sohbet", "modeller", "ses"].includes(g.view) && !imported) {
              setView(g.view);
            }
            if (g.voiceCfg) {
              setVoiceCfg((v) => {
                const vc = { ...v, ...g.voiceCfg };
                if (/localhost:8088\/stt/.test(vc.sttUrl || "")) vc.sttUrl = "/stt";   // eski uçtan göç
                if (/localhost:8088\/tts/.test(vc.ttsUrl || "")) vc.ttsUrl = "/tts";
                if (!vc.jobUrl) vc.jobUrl = "/v1/voice/jobs";
                if (vc.queued == null) vc.queued = false;
                return vc;
              });
            }
            if (g.providers) {
              setProviders((p) => {
                const merged = { ...p };
                for (const k of Object.keys(p)) merged[k] = { ...p[k], ...(g.providers[k] || {}) };
                return merged;
              });
            }
          }
          if (Array.isArray(s.convs) && s.convs.length) {
            const next = imported ? [imported, ...s.convs] : s.convs;
            setConvs(next);
            setActiveId(imported
              ? imported.id
              : (s.activeId && s.convs.some((c) => c.id === s.activeId) ? s.activeId : s.convs[0].id));
            setHydrated(true);
            return;
          }
        } catch (e) {}
      }
      const id = imported ? imported.id : newId();
      setConvs([imported || { id, title: "Yeni sohbet", messages: [], updatedAt: Date.now() }]);
      setActiveId(id);
      setHydrated(true);
    })();
  }, []);

  /* ------------------------------- kaydet -------------------------------- */

  useEffect(() => {
    if (!hydrated) return undefined;
    const t = setTimeout(() => {
      store.set(STATE_KEY, JSON.stringify({
        v: 2,
        settings: {
          modelId, effort, reasoning, personaId, customPersona,
          agentMode, teamMode, agent, agentUrl, voiceCfg, providers,
          accent: normalizeThemeId(accent), target, view,
        },
        convs, activeId,
      }));
    }, 400);
    return () => clearTimeout(t);
  }, [
    hydrated, convs, activeId, modelId, effort, reasoning, personaId,
    customPersona, agentMode, teamMode, agent, agentUrl, voiceCfg, providers, accent, scheme, target, view,
  ]);

  /* ------------------------------- OIDC ---------------------------------- */

  const applyTokens = useCallback((t) => {
    const a = sessionFromTokens(t);
    setAuth(a);
    store.set(AUTH_KEY, JSON.stringify(a));
    setProv("gateway", { apiKey: t.access_token });   // gateway çağrıları JWT kullanır
  }, []);

  const loginOidc = useCallback(async () => {
    const { verifier, challenge } = await pkcePair();
    const state = newId();
    try { sessionStorage.setItem("nova:pkce", JSON.stringify({ verifier, state })); } catch (e) {}
    location.assign(authorizeUrl({ challenge, state }));
  }, []);

  const logoutOidc = useCallback(() => {
    setAuth(null);
    store.set(AUTH_KEY, "");
    setProv("gateway", { apiKey: "" });
  }, []);

  useEffect(() => {
    (async () => {
      const qs = new URLSearchParams(location.search);
      if (qs.get("code")) {
        try {
          const pk = JSON.parse(sessionStorage.getItem("nova:pkce") || "{}");
          if (pk.state && qs.get("state") === pk.state) {
            applyTokens(await exchangeCode(qs.get("code"), pk.verifier));
          }
        } catch (e) {}
        try { window.history.replaceState({}, "", location.pathname); } catch (e) {}
        return;
      }
      try {
        const raw = await store.get(AUTH_KEY);
        if (!raw) return;
        const a = JSON.parse(raw);
        if (!a || !a.refresh_token) return;
        if (a.expires_at > Date.now() + 60000) {
          setAuth(a);
          setProv("gateway", { apiKey: a.access_token });
        } else {
          applyTokens(await refreshTokens(a.refresh_token));
        }
      } catch (e) {}
    })();
  }, [applyTokens]);

  useEffect(() => {
    if (!auth || !auth.refresh_token) return undefined;
    const t = setInterval(async () => {
      if (auth.expires_at - Date.now() > 120000) return;
      try { applyTokens(await refreshTokens(auth.refresh_token)); } catch (e) { logoutOidc(); }
    }, 30000);
    return () => clearInterval(t);
  }, [auth, applyTokens, logoutOidc]);

  /* --------------------------- canlı model listesi ----------------------- */

  const loadLiveModels = useCallback(async (refreshList) => {
    if (!trim(gw.baseUrl)) { setLiveCatalog(null); return; }
    try {
      const d = await api.fetchModels(gw, refreshList);
      setLiveCatalog(d);
      setLiveModelsErr(d.ollama && d.ollama.ok === false ? (d.ollama.error || "Ollama listesi alınamadı") : "");
    } catch (e) {
      setLiveCatalog(null);
      setLiveModelsErr("Model listesi alınamadı: " + ((e && e.message) || e));
    }
  }, [gw.baseUrl, gw.apiKey]);   // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => { loadLiveModels(false); }, [loadLiveModels]);

  // Kullanıcı seçim yapmadıysa canlı listenin varsayılanını uygula; geçersiz
  // kayıtlı seçimi temizle. Dinamik yönlendirmeye sessizce düşülmez.
  useEffect(() => {
    if (modelId) {
      const stillValid = MODEL_FLAT.some((m) => m.id === modelId && m.available !== false);
      if (stillValid) return;
      if (liveCatalog) setModelId("");
      return;
    }
    const wanted = liveCatalog && liveCatalog.defaultModel ? "live:" + liveCatalog.defaultModel : null;
    const pick = (wanted && MODEL_FLAT.find((m) => m.id === wanted))
      || MODEL_FLAT.find((m) => m.available !== false && m.model !== "auto");
    if (pick) setModelId(pick.id);
  }, [liveCatalog, MODEL_FLAT, modelId]);

  /* -------------------------- gateway sağlığı ---------------------------- */

  const loadHealth = useCallback(async () => {
    const h = await api.fetchHealth(gw);
    setGatewayInfo(h);
    setHealthErr(h ? "" : "Gateway /health yanıt vermedi");
  }, [gw.baseUrl]);   // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => { loadHealth(); }, [loadHealth]);
  useEffect(() => { if (view === "kontrol") loadHealth(); }, [view, loadHealth]);

  /* ------------------- ayarlar açılınca gateway verileri ----------------- */

  const loadDocs = useCallback(async () => { if (signedIn) setDocs(await api.listDocs(gw)); }, [signedIn, gw.apiKey, gw.baseUrl]);   // eslint-disable-line react-hooks/exhaustive-deps
  const loadMems = useCallback(async () => { if (signedIn) setMems(await api.listMems(gw)); }, [signedIn, gw.apiKey, gw.baseUrl]);   // eslint-disable-line react-hooks/exhaustive-deps
  const loadWss = useCallback(async () => { if (signedIn) setWss(await api.listWorkspaces(gw)); }, [signedIn, gw.apiKey, gw.baseUrl]);   // eslint-disable-line react-hooks/exhaustive-deps
  const loadSched = useCallback(async () => { if (signedIn) setSchedTasks(await api.listScheduled(gw)); }, [signedIn, gw.apiKey, gw.baseUrl]);   // eslint-disable-line react-hooks/exhaustive-deps
  const loadRuns = useCallback(async () => { if (signedIn) setAgentRuns(await api.listAgentRuns(gw)); }, [signedIn, gw.apiKey, gw.baseUrl]);   // eslint-disable-line react-hooks/exhaustive-deps
  const loadMcp = useCallback(async () => {
    if (!signedIn) return;
    setMcpBusy(true);
    try { setMcpInfo(await api.fetchMcpTools(gw)); } finally { setMcpBusy(false); }
  }, [signedIn, gw.apiKey, gw.baseUrl]);   // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!showSettings) return;
    loadDocs(); loadMems(); loadWss(); loadSched(); loadRuns(); loadMcp(); loadHealth();
    if (signedIn) api.fetchUsage(gw).then(setUsageInfo);
    else setUsageInfo(null);
  }, [showSettings, signedIn]);   // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => { if (view === "kontrol" || view === "isler") loadWss(); }, [view, loadWss]);

  /* ---------------------------- bilgi tabanı ----------------------------- */

  function readDocFile(file) {
    if (!file) return;
    setDocError("");
    const name = file.name || "Belge";
    const ext = (name.match(/\.[^.]+$/) || [""])[0].toLowerCase();
    const serverExtract = ext === ".pdf" || ext === ".docx";
    if (serverExtract && file.size > 10 * 1024 * 1024) {
      setDocError("Dosya çok büyük (max 10 MB)");
      return;
    }
    const fr = new FileReader();
    fr.onerror = () => setDocError("Dosya okunamadı");
    fr.onload = () => {
      if (!docTitle) setDocTitle(name.replace(/\.[^.]+$/, ""));
      if (serverExtract) {
        const raw = String(fr.result || "");
        const b64 = raw.includes(",") ? raw.split(",").pop() : raw;
        setDocFile({
          name,
          mime: file.type || (ext === ".pdf"
            ? "application/pdf"
            : "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
          b64,
        });
        setDocText("");
      } else {
        setDocFile(null);
        setDocText(String(fr.result || ""));
      }
    };
    if (serverExtract) fr.readAsDataURL(file);
    else fr.readAsText(file);
  }

  async function uploadDoc() {
    const text = docText.trim();
    if (!signedIn || docBusy || (!docFile && text.length < 20)) return;
    setDocError("");
    setDocBusy(true);
    try {
      const ws = kbWs ? { workspace_id: kbWs } : {};
      const body = docFile
        ? { title: docTitle.trim() || docFile.name.replace(/\.[^.]+$/, ""), file: docFile, ...ws }
        : { title: docTitle.trim() || "Belge", text, ...ws };
      const r = await api.createDoc(gw, body);
      if (r.ok) { setDocText(""); setDocTitle(""); setDocFile(null); await loadDocs(); }
      else setDocError(r.error || "Belge yüklenemedi");
    } finally {
      setDocBusy(false);
    }
  }

  async function removeDoc(id) { await api.deleteDoc(gw, id); await loadDocs(); }

  async function addMem() {
    const content = memText.trim();
    if (!signedIn || memBusy || content.length < 2) return;
    setMemBusy(true);
    try {
      const r = await api.createMem(gw, { content, ...(memWs ? { workspace_id: memWs } : {}) });
      if (r.ok) { setMemText(""); await loadMems(); }
    } finally {
      setMemBusy(false);
    }
  }
  async function removeMem(id) { await api.deleteMem(gw, id); await loadMems(); }

  async function runEval() {
    const prompt = evalPrompt.trim();
    const models = evalModelsText.split(",").map((s) => s.trim()).filter(Boolean);
    if (!signedIn || evalRunning || prompt.length < 2 || !models.length) return;
    setEvalRunning(true);
    setEvalResults(null);
    try {
      const r = await api.runEval(gw, { prompt, models });
      if (r.ok) setEvalResults(r.data.results || []);
      else setEvalResults([{ model: "—", ok: false, error: r.error || "Kıyas başarısız" }]);
    } finally {
      setEvalRunning(false);
    }
  }

  /* ---------------------------- workspaces ------------------------------- */

  async function createWs() {
    const name = wsName.trim();
    if (!signedIn || !name) return;
    const r = await api.createWorkspace(gw, name);
    if (r.ok) { setWsName(""); await loadWss(); }
  }
  const refreshMembers = async (id) => setWsMembers(await api.listMembers(gw, id));
  async function toggleMembers(id) {
    if (wsOpen === id) { setWsOpen(null); setWsMembers([]); return; }
    setWsOpen(id);
    await refreshMembers(id);
  }
  async function inviteMember(id) {
    const email = wsInvite.email.trim();
    if (!signedIn || !email) return;
    const r = await api.inviteMember(gw, id, { email, role: wsInvite.role });
    if (r.ok) { setWsInvite({ email: "", role: "viewer" }); await refreshMembers(id); }
    else alert(r.error || "Davet başarısız");
  }
  async function changeRole(id, userId, role) {
    const r = await api.changeMemberRole(gw, id, userId, role);
    if (r.ok) await refreshMembers(id);
    else alert(r.error || "Rol değişmedi");
  }
  async function removeMember(id, userId) {
    const r = await api.removeMember(gw, id, userId);
    if (r.ok) await refreshMembers(id);
    else alert(r.error || "Çıkarılamadı");
  }

  /* ------------------------ zamanlanmış görevler ------------------------- */

  async function createSched() {
    const title = schedForm.title.trim();
    const prompt = schedForm.prompt.trim();
    if (!signedIn || schedBusy || !title || prompt.length < 3) return;
    setSchedBusy(true);
    try {
      const r = await api.createScheduled(gw, {
        title, prompt, schedule: schedForm.schedule, agent: true,
        ...(schedForm.ws ? { workspace_id: schedForm.ws } : {}),
      });
      if (r.ok) { setSchedForm({ title: "", prompt: "", schedule: schedForm.schedule, ws: schedForm.ws }); await loadSched(); }
    } finally {
      setSchedBusy(false);
    }
  }
  async function toggleSched(t) { await api.patchScheduled(gw, t.id, { enabled: !t.enabled }); await loadSched(); }
  async function deleteSched(id) { await api.deleteScheduled(gw, id); await loadSched(); }
  async function removeAgentRun(id) { await api.deleteAgentRun(gw, id); await loadRuns(); }

  /* --------------------- sunucu sohbet senkronizasyonu ------------------- */

  useEffect(() => {
    if (!hydrated || !signedIn) return;
    (async () => {
      const items = await api.listConversations(gw);
      if (!items.length) return;
      setConvs((prev) => {
        const have = new Set(prev.map((c) => c.serverId).filter(Boolean));
        const add = items.filter((s) => !have.has(s.id)).map((s) => ({
          id: newId(), serverId: s.id, title: s.title || "Sunucu sohbeti", messages: [], remote: true,
          updatedAt: Date.parse(s.updated_at || s.created_at || "") || Date.now(),
        }));
        return add.length ? [...prev, ...add] : prev;
      });
    })();
  }, [hydrated, signedIn]);   // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    const a = convs.find((c) => c.id === activeId);
    if (!a || !a.remote || !a.serverId || (a.messages && a.messages.length) || !signedIn) return;
    (async () => {
      const d = await api.getConversation(gw, a.serverId);
      if (!d) return;
      const msgs = (d.messages || []).map((m) => ({ role: m.role, content: m.content, route: m.route || undefined }));
      setConvs((prev) => prev.map((c) => (c.id === a.id ? { ...c, messages: msgs, remote: false } : c)));
    })();
  }, [activeId, signedIn]);   // eslint-disable-line react-hooks/exhaustive-deps

  function newConv() {
    const id = newId();
    setConvs((prev) => [{ id, title: "Yeni sohbet", messages: [], updatedAt: Date.now() }, ...prev]);
    setActiveId(id);
    setShowDrawer(false);
    setView("sohbet");
  }

  function deleteConv(id) {
    setConvs((prev) => {
      const next = prev.filter((c) => c.id !== id);
      if (id === activeId) {
        if (next.length) setActiveId(next[0].id);
        else {
          const nid = newId();
          next.unshift({ id: nid, title: "Yeni sohbet", messages: [], updatedAt: Date.now() });
          setActiveId(nid);
        }
      }
      return next;
    });
  }

  /* ------------------------------ sistem prompt -------------------------- */

  function buildSystem() {
    const agentLine = agent === "direct"
      ? ""
      : "Bir " + (AGENTS.find((a) => a.id === agent) || {}).name + " ajanı üzerinden çalışıyorsun; araç çağrıları ve çok adımlı görevler mümkün.";
    const personaPrompt = activePersona.id === "custom" ? customPersona.trim() : activePersona.sys;
    const personaLine = personaPrompt ? ("Seçili persona: " + activePersona.name + ". " + personaPrompt) : "";
    return [
      "Sen NOVA adlı kişisel yapay zeka asistanısın. Türkçe konuşursun.",
      "Teknik, net ve doğrudan ol. Gereksiz dolgu cümleler kurma.",
      curItem.provider === "gateway"
        ? "Dinamik yönlendirme aktif: göreve en uygun modeli seç."
        : ("Etkin model: " + curItem.name + "."),
      personaLine, agentLine, curEffort.sys,
      reasoning ? "Yanıttan önce kısa bir muhakeme yapabilirsin ama nihai yanıtı açık ver." : "",
    ].filter(Boolean).join(" ");
  }

  const updateLast = (patch) => setMessages((prev) => {
    const c = [...prev];
    c[c.length - 1] = { ...c[c.length - 1], ...patch };
    return c;
  });

  async function ensureServerConv() {
    if (curItem.provider !== "gateway" || !signedIn) return null;
    const a = convs.find((c) => c.id === activeId);
    if (!a) return null;
    if (a.serverId) return a.serverId;
    const title = (a.title && a.title !== "Yeni sohbet")
      ? a.title
      : (((a.messages[0] || {}).content) || "Yeni sohbet").slice(0, 60);
    const d = await api.createConversation(gw, title);
    if (!d || !d.id) return null;
    setConvs((prev) => prev.map((c) => (c.id === activeId ? { ...c, serverId: d.id } : c)));
    return d.id;
  }

  /** Gateway araçları gerekiyorsa yerel modeli gateway üzerinden çağır. */
  function routeFor(lastText) {
    const liveTool = needsLiveTool(lastText);
    const forceGatewayTools = curItem.provider === "ollama" && !!gw.baseUrl && (agentMode || teamMode || liveTool);
    const sendProv = forceGatewayTools ? gw : curProv;
    const sendModel = forceGatewayTools ? ("ollama/" + curApiModel) : curApiModel;
    const viaGateway = curItem.provider === "gateway" || forceGatewayTools;
    // Araç çağıramayan bir modele ajan modu göndermek boşuna bir tur: Ollama
    // 400 döner. Katalog yeteneği ÖLÇTÜYSE (probe/provider) burada eliyoruz.
    // Yalnızca tahmin varsa denemeye devam ederiz; gateway zaten desteklemeyen
    // modelde düz sohbete düşüp nedenini yazıyor.
    const toolsBlocked = curItem.toolsVerified && !curItem.tools;
    const wantAgent = (agentMode || (forceGatewayTools && liveTool)) && !toolsBlocked;
    const sendExtra = viaGateway
      ? {
        effort, think: reasoning,
        ...(wantAgent ? { agent: true } : {}),
        ...(teamMode && !toolsBlocked ? { team: true } : {}),
      }
      : {};
    return { sendProv, sendModel, sendExtra, viaGateway };
  }

  async function complete(historyMsgs) {
    setMessages((prev) => [...prev, { role: "assistant", content: "", thinking: reasoning, thoughts: "", at: Date.now() }]);
    setBusy(true);
    const ctrl = new AbortController();
    abortRef.current = ctrl;

    let full = "";
    let route = null;
    let thoughts = "";
    let toolSteps = [];
    const t0 = performance.now();
    let firstAt = 0;

    const lastText = (historyMsgs[historyMsgs.length - 1] || {}).content || "";
    const { sendProv, sendModel, sendExtra, viaGateway } = routeFor(lastText);
    const convId = await ensureServerConv();
    if (convId && viaGateway) sendExtra.conversation_id = convId;

    try {
      await streamChat({
        prov: sendProv, model: sendModel, system: buildSystem(),
        history: historyMsgs.map((m) => ({ role: m.role, content: m.content, images: m.images })),
        think: reasoning, signal: ctrl.signal, extra: sendExtra,
        onRoute: (r) => { route = r; },
        onThought: (t) => { thoughts += t; updateLast({ thoughts }); },
        onTool: (s) => {
          if (s.done) {
            const next = [...toolSteps];
            let idx = -1;
            for (let i = next.length - 1; i >= 0; i--) {
              if (next[i].name === s.name && (!s.q || next[i].q === s.q)) { idx = i; break; }
            }
            if (idx >= 0) next[idx] = { ...next[idx], ...s, q: next[idx].q || s.q };
            else next.push(s);
            toolSteps = next;
          } else {
            toolSteps = [...toolSteps, s];
          }
          updateLast({ tools: toolSteps });
        },
        onToken: (t) => {
          if (!firstAt) firstAt = performance.now() - t0;
          full += t;
          updateLast({ content: full, route });
        },
      });

      const ms = Math.round(performance.now() - t0);
      const tok = Math.max(1, Math.round((full.length + thoughts.length) / 4));   // ~4 krktr/token
      updateLast({
        content: full.trim() || "(boş yanıt)",
        route,
        stats: { ms, ttft: Math.round(firstAt), tok, model: route || sendModel },
      });

      const site = extractWebsite(full);
      if (site) openArtifact({ type: "html", code: site, lang: "html" });   // tam sayfa → canlı önizleme
    } catch (e) {
      const h = errHint(e, sendProv);
      updateLast({ content: full + (h ? (full ? "\n\n" : "") + h : ""), route });
    } finally {
      setBusy(false);
      abortRef.current = null;
    }
  }

  async function sendChat(textArg) {
    const text = (textArg != null && typeof textArg === "string" ? textArg : input).trim();
    const imgs = pending;
    if ((!text && !imgs.length) || busy) return;
    setInput("");
    setPending([]);
    const userMsg = { role: "user", content: text };
    if (imgs.length) userMsg.images = imgs;

    // Görselleri MinIO'ya da arşivle; model çağrısı data URL ile sürer.
    if (imgs.length && signedIn) {
      imgs.forEach((u) => {
        const du = typeof u === "string" ? u : ((u && u.url) || "");
        if (du.startsWith("data:")) api.archiveMedia(gw, du);
      });
    }

    const hist = [...messages, userMsg];
    setMessages(hist);
    if (view !== "sohbet") setView("sohbet");
    await complete(hist);
  }

  async function regenerate() {
    if (busy) return;
    const lastUser = [...messages].reverse().find((m) => m.role === "user");
    if (!lastUser) return;
    const base = [...messages];
    while (base.length && base[base.length - 1].role === "assistant") base.pop();
    setMessages(base);
    await complete(base);
  }

  function stopChat() { if (abortRef.current) abortRef.current.abort(); }

  function addImages(files) {
    const list = Array.from(files || []).filter((f) => f.type.startsWith("image/")).slice(0, 4);
    list.forEach((f) => {
      const fr = new FileReader();
      fr.onload = () => setPending((p) => [...p, String(fr.result)]);
      fr.readAsDataURL(f);
    });
  }

  async function openArtifact(next) {
    setArtifact(await prepareArtifact(next));
  }

  async function doShare() {
    const note = await shareChatLink(convs.find((c) => c.id === activeId));
    setShareNote(note);
    setTimeout(() => setShareNote(""), 1800);
  }

  /* --------------------------------- ses --------------------------------- */

  function ensureCtx() {
    if (!audioCtxRef.current) {
      const AC = window.AudioContext || window.webkitAudioContext;
      audioCtxRef.current = new AC();
    }
    if (audioCtxRef.current.state === "suspended") audioCtxRef.current.resume();
    return audioCtxRef.current;
  }

  function runMeter(analyser) {
    const buf = new Uint8Array(analyser.fftSize);
    const loop = () => {
      analyser.getByteTimeDomainData(buf);
      let sum = 0;
      for (let i = 0; i < buf.length; i++) { const v = (buf[i] - 128) / 128; sum += v * v; }
      extLevelRef.current = Math.min(1, Math.sqrt(sum / buf.length) * 2.6);
      meterRafRef.current = requestAnimationFrame(loop);
    };
    loop();
  }
  function stopMeter() {
    if (meterRafRef.current) cancelAnimationFrame(meterRafRef.current);
    meterRafRef.current = null;
    extLevelRef.current = -1;
  }

  const voiceAuthHeaders = () => (signedIn ? { Authorization: "Bearer " + gw.apiKey } : {});
  const voiceJobBase = () => (voiceCfg.jobUrl || "/v1/voice/jobs").replace(/\/+$/, "");

  async function runVoiceJob(type, payload, label) {
    const base = voiceJobBase();
    const headers = voiceAuthHeaders();
    const start = await fetch(base, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...headers },
      body: JSON.stringify({ type, ...payload }),
    });
    if (!start.ok) throw new Error("voice job " + start.status);
    const id = (await start.json()).id;
    const started = Date.now();
    while (Date.now() - started < 120000) {
      const r = await fetch(base + "/" + encodeURIComponent(id), { headers });
      if (!r.ok) throw new Error("voice job status " + r.status);
      const job = await r.json();
      const state = job.state === "waiting" ? "kuyrukta" : job.state === "active" ? "çalışıyor" : job.state;
      setVoiceSub((label || "Ses işi") + " · " + state);
      if (job.state === "completed") return job.result || {};
      if (job.state === "failed") throw new Error(job.error || "voice job failed");
      await delay(800);
    }
    throw new Error("voice job timeout");
  }

  function speakBrowser(text) {
    const synth = window.speechSynthesis;
    if (!synth) {
      setTimeout(() => { setVoiceState("idle"); setVoiceSub("Konuşmak için mikrofona dokun"); }, 2200);
      return;
    }
    try {
      const u = new SpeechSynthesisUtterance(text);
      u.lang = "tr-TR";
      u.rate = 1.02;
      const trv = synth.getVoices().find((v) => v.lang && v.lang.toLowerCase().startsWith("tr"));
      if (trv) u.voice = trv;
      u.onend = () => { setVoiceState("idle"); setVoiceSub("Konuşmak için mikrofona dokun"); };
      synth.cancel();
      synth.speak(u);
      setTimeout(() => {
        if (voiceStateRef.current === "speaking") {
          setVoiceState("idle");
          setVoiceSub("Konuşmak için mikrofona dokun");
        }
      }, Math.min(20000, 2500 + text.length * 55));
    } catch (e) {
      setTimeout(() => setVoiceState("idle"), 2200);
    }
  }

  async function speakReal(text) {
    try {
      let ab;
      if (voiceCfg.queued) {
        const result = await runVoiceJob("tts", { input: text, voice: voiceCfg.voice }, "TTS kuyruğu");
        if (!result.audio) throw new Error("tts job returned no audio");
        ab = b64ToArrayBuffer(result.audio);
      } else {
        const r = await fetch(voiceCfg.ttsUrl, {
          method: "POST",
          headers: { "Content-Type": "application/json", ...voiceAuthHeaders() },
          body: JSON.stringify({ input: text, voice: voiceCfg.voice }),
        });
        if (!r.ok) throw new Error("tts " + r.status);
        ab = await r.arrayBuffer();
      }
      const ctx = ensureCtx();
      const audioBuf = await ctx.decodeAudioData(ab);
      const src = ctx.createBufferSource();
      src.buffer = audioBuf;
      const analyser = ctx.createAnalyser();
      analyser.fftSize = 512;
      src.connect(analyser);
      analyser.connect(ctx.destination);
      ttsAudioRef.current = src;
      src.onended = () => {
        stopMeter();
        ttsAudioRef.current = null;
        setVoiceState("idle");
        setVoiceSub("Konuşmak için mikrofona dokun");
      };
      runMeter(analyser);
      src.start();
    } catch (e) {
      stopMeter();
      speakBrowser(text);   // gateway/TTS yoksa tarayıcı TTS'e düş
    }
  }

  function speak(text) {
    setVoiceState("speaking");
    setVoiceSub(text.slice(0, 180));
    if (voiceCfg.real) speakReal(text);
    else speakBrowser(text);
  }

  /** Barge-in / "Durdur": sesi anında kes. */
  function stopSpeaking() {
    try { ttsAudioRef.current && ttsAudioRef.current.stop(); } catch (e) {}
    ttsAudioRef.current = null;
    try { window.speechSynthesis && window.speechSynthesis.cancel(); } catch (e) {}
    stopMeter();
    setVoiceState("idle");
    setVoiceSub("Konuşmak için mikrofona dokun");
  }

  async function runVoice(text) {
    setVoiceState("thinking");
    setVoiceSub("Düşünüyor…");
    const hist = [...messages, { role: "user", content: text }];
    setMessages(hist);

    let full = "";
    const { sendProv, sendModel, sendExtra, viaGateway } = routeFor(text);
    const vConvId = await ensureServerConv();
    if (vConvId && viaGateway) sendExtra.conversation_id = vConvId;

    try {
      await streamChat({
        prov: sendProv, model: sendModel, system: buildSystem(),
        history: hist.map((m) => ({ role: m.role, content: m.content, images: m.images })),
        think: reasoning, extra: sendExtra,
        onToken: (t) => { full += t; },
      });
    } catch (e) {
      full = errHint(e, sendProv) || "Yanıt alınamadı.";
    }
    const reply = full.trim() || "Yanıt alınamadı.";
    setMessages((prev) => [...prev, { role: "assistant", content: reply }]);
    speak(reply);
  }

  async function startListeningReal() {
    if (voiceState === "listening") {
      try { mediaRecRef.current && mediaRecRef.current.stop(); } catch (e) {}
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      mediaStreamRef.current = stream;
      const ctx = ensureCtx();
      const srcNode = ctx.createMediaStreamSource(stream);
      const analyser = ctx.createAnalyser();
      analyser.fftSize = 512;
      srcNode.connect(analyser);
      runMeter(analyser);   // mikrofon genliği → orb

      const mime = MediaRecorder.isTypeSupported("audio/webm") ? "audio/webm" : "";
      const rec = new MediaRecorder(stream, mime ? { mimeType: mime } : undefined);
      mediaRecRef.current = rec;
      const chunks = [];
      rec.ondataavailable = (e) => { if (e.data.size) chunks.push(e.data); };
      rec.onstop = async () => {
        stopMeter();
        stream.getTracks().forEach((t) => t.stop());
        mediaStreamRef.current = null;
        const blob = new Blob(chunks, { type: mime || "audio/webm" });
        setVoiceState("thinking");
        setVoiceSub("Çözümleniyor…");
        try {
          const b64 = await blobToB64(blob);
          let d;
          if (voiceCfg.queued) {
            d = await runVoiceJob("stt", { audio: b64, mime: blob.type, language: "tr" }, "STT kuyruğu");
          } else {
            const r = await fetch(voiceCfg.sttUrl, {
              method: "POST",
              headers: { "Content-Type": "application/json", ...voiceAuthHeaders() },
              body: JSON.stringify({ audio: b64, mime: blob.type, language: "tr" }),
            });
            d = await r.json();
          }
          const text = (d.text || "").trim();
          if (text) runVoice(text);
          else { setVoiceState("idle"); setVoiceSub("Ses çözülemedi — tekrar dene"); }
        } catch (e) {
          setVoiceState("idle");
          setVoiceSub("Whisper'a ulaşılamadı — gateway açık mı?");
        }
      };
      rec.start();
      setVoiceState("listening");
      setVoiceSub("Dinliyorum… (bitirmek için tekrar dokun)");
    } catch (e) {
      setVoiceState("idle");
      setVoiceSub("Mikrofon izni yok — bu ortamda kapalı olabilir");
    }
  }

  function startListening() {
    if (voiceCfg.real) return startListeningReal();
    if (voiceState === "listening") {
      try { recogRef.current && recogRef.current.stop(); } catch (e) {}
      setVoiceState("idle");
      setVoiceSub("Konuşmak için mikrofona dokun");
      return undefined;
    }
    const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SR) {
      setVoiceState("listening");
      setVoiceSub("Mikrofon bu ortamda kapalı — aşağıdan yazabilirsin");
      return undefined;
    }
    try {
      const rec = new SR();
      rec.lang = "tr-TR";
      rec.interimResults = true;
      rec.continuous = false;
      recogRef.current = rec;
      setVoiceState("listening");
      setVoiceSub("Dinliyorum…");
      let final = "";
      rec.onresult = (ev) => {
        let interim = "";
        for (let i = ev.resultIndex; i < ev.results.length; i++) {
          const r = ev.results[i];
          if (r.isFinal) final += r[0].transcript;
          else interim += r[0].transcript;
        }
        setVoiceSub((final + interim) || "Dinliyorum…");
      };
      rec.onerror = () => {
        setVoiceState("listening");
        setVoiceSub("Mikrofon erişimi yok — aşağıdan yazabilirsin");
      };
      rec.onend = () => {
        if (final.trim()) runVoice(final.trim());
        else if (voiceStateRef.current === "listening") {
          setVoiceState("idle");
          setVoiceSub("Konuşmak için mikrofona dokun");
        }
      };
      rec.start();
    } catch (e) {
      setVoiceState("listening");
      setVoiceSub("Mikrofon başlatılamadı — aşağıdan yazabilirsin");
    }
    return undefined;
  }

  function submitVoiceText() {
    const t = voiceText.trim();
    if (!t) return;
    setVoiceText("");
    runVoice(t);
  }

  /* ------------------------------- görünüm ------------------------------- */

  const hour = new Date().getHours();
  const greet = hour < 6 ? "İyi geceler" : hour < 12 ? "Günaydın" : hour < 18 ? "İyi günler" : "İyi akşamlar";
  const who = auth && auth.email ? ", " + auth.email.split("@")[0] : "";

  const filteredConvs = convs.filter(
    (c) => !convSearch.trim() || (c.title || "").toLowerCase().includes(convSearch.trim().toLowerCase()),
  );

  const statusTone = gatewayInfo ? "ok" : (healthErr ? "err" : "warn");
  const subtitle = `${curItem.name} · ${activePersona.short} · ${curEffort.name}${reasoning ? " · düşünme" : ""}`;

  const openConv = (id) => { setActiveId(id); setView("sohbet"); setShowDrawer(false); };
  const navView = (v) => { setView(v); setOpenDD(null); };

  return (
    <div
      className="nova-root has-rail"
      data-accent={accent}
      data-scheme={resolvedScheme}
      onClick={() => setOpenDD(null)}
    >
      <style>{NOVA_CSS}</style>
      <div className="bg-aurora"><div className="blob b1" /><div className="blob b2" /><div className="blob b3" /></div>
      <div className="bg-grid" />
      <div className="bg-grain" />

      <SideRail
        view={view} onView={navView} taskBadge={runningTaskCount}
        convs={filteredConvs} activeId={activeId} onOpenConv={openConv}
        onNewConv={newConv} onDeleteConv={deleteConv}
        search={convSearch} onSearch={setConvSearch}
        hasMessages={messages.length > 0}
        onExport={(fmt) => exportChatFile(convs.find((c) => c.id === activeId), fmt)}
        onShare={doShare} shareNote={shareNote}
        onSettings={() => setShowSettings(true)}
        authEmail={auth && auth.email}
      />

      <TopBar
        subtitle={subtitle}
        statusTone={statusTone}
        modelName={curItem.name}
        modelId={curApiModel}
        connected={ready}
        openDD={openDD} setOpenDD={setOpenDD}
        models={MODELS} selectedModelId={modelId} onPickModel={setModelId}
        live={!!liveCatalog} modelsErr={liveModelsErr} onRefreshModels={() => loadLiveModels(true)}
        view={view}
        onToggleVoice={() => setView(view === "ses" ? "sohbet" : "ses")}
        onOpenDrawer={() => setShowDrawer(true)}
        onSettings={() => setShowSettings(true)}
      />

      {showDrawer && (
        <ConversationsDrawer
          view={view} onView={navView} taskBadge={runningTaskCount}
          convs={convs} activeId={activeId} onOpenConv={openConv}
          onNewConv={newConv} onDeleteConv={deleteConv}
          onClose={() => setShowDrawer(false)}
        />
      )}

      <main className="stage">
        {view === "ses" && (
          <VoiceView
            voiceState={voiceState} voiceSub={voiceSub} sttSupported={sttSupported}
            voiceText={voiceText} onVoiceText={setVoiceText} onSubmitText={submitVoiceText}
            onMic={startListening} onStopSpeaking={stopSpeaking}
            voiceStateRef={voiceStateRef} reducedRef={reducedRef}
            extLevelRef={extLevelRef} accentRef={accentRef}
          />
        )}

        {view === "sohbet" && (
          <ChatView
            messages={messages} busy={busy}
            greeting={greet} userName={who}
            modelName={curItem.name} modelId={curApiModel}
            input={input} onInput={setInput}
            onSend={sendChat} onStop={stopChat} onRegenerate={regenerate}
            onArtifact={openArtifact}
            pending={pending} onAddImages={addImages}
            onRemoveImage={(i) => setPending((p) => p.filter((_, j) => j !== i))}
            imageRouteHint={imageRouteHint}
          />
        )}

        {view === "kontrol" && (
          <ControlView
            health={gatewayInfo} healthErr={healthErr} baseUrl={gw.baseUrl}
            signedIn={signedIn} email={auth && auth.email}
            target={target} onTarget={setTarget}
            modelName={curItem.name} modelId={curApiModel}
            effortName={curEffort.name} personaName={activePersona.name} reasoning={reasoning}
            agentMode={agentMode} teamMode={teamMode}
            chatBusy={busy} activeTask={tasks.active} taskCount={tasks.tasks.length}
            onRetry={() => { loadHealth(); loadLiveModels(true); }}
            onOpenSettings={() => setShowSettings(true)}
            onOpenChat={() => setView("sohbet")}
            onOpenTasks={() => setView("isler")}
            onOpenModels={() => setView("modeller")}
          />
        )}

        {view === "isler" && (
          <TasksView
            connected={signedIn}
            tasks={tasks.tasks} active={tasks.active} activeId={tasks.activeId} setActiveId={tasks.setActiveId}
            events={tasks.events} confirmation={tasks.confirmation}
            error={tasks.error} busy={tasks.busy} loading={tasks.loading}
            onCreate={tasks.create} onCommand={tasks.command} onDecide={tasks.decide}
            onReload={tasks.reload} onClearError={tasks.clearError}
            onOpenSettings={() => setShowSettings(true)}
          />
        )}

        {view === "modeller" && (
          <ModelsView
            groups={MODELS} modelId={modelId} onPick={setModelId}
            live={!!liveCatalog} err={liveModelsErr} onRefresh={() => loadLiveModels(true)}
            ollama={liveCatalog && liveCatalog.ollama}
            health={gatewayInfo} defaultModel={liveCatalog && liveCatalog.defaultModel}
            onOpenChat={() => setView("sohbet")}
          />
        )}
      </main>

      <ArtifactPanel artifact={artifact} onClose={() => setArtifact(null)} />

      {CHAT_VIEWS.has(view) && (
        <Dock
          view={view} onView={navView}
          curItem={curItem} models={MODELS} modelId={modelId} onPickModel={setModelId}
          live={!!liveCatalog} modelsErr={liveModelsErr} onRefreshModels={() => loadLiveModels(true)}
          openDD={openDD} setOpenDD={setOpenDD}
          effort={effort} onEffort={setEffort}
          reasoning={reasoning} onReasoning={setReasoning}
          agentMode={agentMode} onAgentMode={setAgentMode}
          teamMode={teamMode} onTeamMode={setTeamMode}
        />
      )}

      <BottomNav view={view} onView={navView} taskBadge={runningTaskCount} />

      {showSettings && (
        <SettingsModal
          onClose={() => setShowSettings(false)}
          signedIn={signedIn}
          accent={accent} onAccent={(id) => setAccent(normalizeThemeId(id))}
          personaId={personaId} onPersona={setPersonaId}
          customPersona={customPersona} onCustomPersona={setCustomPersona}
          providers={providers} onProv={setProv} provReady={provReady}
          auth={auth} onLogin={loginOidc} onLogout={logoutOidc} health={gatewayInfo}
          usage={usageInfo}
          docs={docs} wss={wss} kbWs={kbWs} onKbWs={setKbWs}
          docTitle={docTitle} onDocTitle={setDocTitle}
          docText={docText} onDocText={(v) => { setDocText(v); setDocFile(null); setDocError(""); }}
          docFile={docFile} docError={docError} docBusy={docBusy}
          onPickDocFile={readDocFile} onUploadDoc={uploadDoc} onDeleteDoc={removeDoc}
          docFileRef={docFileRef}
          mems={mems} memWs={memWs} onMemWs={setMemWs}
          memText={memText} onMemText={setMemText} memBusy={memBusy}
          onAddMem={addMem} onDeleteMem={removeMem}
          evalPrompt={evalPrompt} onEvalPrompt={setEvalPrompt}
          evalModelsText={evalModelsText} onEvalModelsText={setEvalModelsText}
          evalRunning={evalRunning} evalResults={evalResults} onRunEval={runEval}
          wsName={wsName} onWsName={setWsName} onCreateWs={createWs}
          wsOpen={wsOpen} wsMembers={wsMembers} onToggleMembers={toggleMembers}
          wsInvite={wsInvite} onWsInvite={setWsInvite} onInviteMember={inviteMember}
          onChangeRole={changeRole} onRemoveMember={removeMember}
          schedTasks={schedTasks} schedForm={schedForm} onSchedForm={setSchedForm}
          schedBusy={schedBusy} onCreateSched={createSched}
          onToggleSched={toggleSched} onDeleteSched={deleteSched}
          agentRuns={agentRuns} onDeleteAgentRun={removeAgentRun}
          mcpInfo={mcpInfo} mcpBusy={mcpBusy} onLoadMcp={loadMcp}
          voiceCfg={voiceCfg} onVoiceCfg={setVc}
          agent={agent} onAgent={setAgent} agentUrl={agentUrl} onAgentUrl={setAgentUrl}
        />
      )}
    </div>
  );
}
