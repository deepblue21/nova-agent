// Conversations/settings persist; provider keys and login tokens stay in memory.

export const STATE_KEY = "nova:state:v1";
export const AUTH_KEY = "nova:auth:v1";

const _mem = {};

const _idb = (() => {
  let dbp;
  const open = () => {
    if (!dbp) {
      dbp = new Promise((res, rej) => {
        const rq = indexedDB.open("nova-store", 1);
        rq.onupgradeneeded = () => rq.result.createObjectStore("kv");
        rq.onsuccess = () => res(rq.result);
        rq.onerror = () => rej(rq.error);
      });
    }
    return dbp;
  };
  return {
    async get(k) {
      const db = await open();
      return new Promise((res, rej) => {
        const rq = db.transaction("kv").objectStore("kv").get(k);
        rq.onsuccess = () => res(rq.result != null ? rq.result : null);
        rq.onerror = () => rej(rq.error);
      });
    },
    async set(k, v) {
      const db = await open();
      return new Promise((res, rej) => {
        const tx = db.transaction("kv", "readwrite");
        tx.objectStore("kv").put(v, k);
        tx.oncomplete = () => res();
        tx.onerror = () => rej(tx.error);
      });
    },
  };
})();

const persistent = {
  async get(k) {
    try {
      if (typeof window !== "undefined" && window.storage) {
        const r = await window.storage.get(k);
        return r ? r.value : null;
      }
    } catch (e) {}
    try {
      if (typeof indexedDB !== "undefined") {
        const v = await _idb.get(k);
        if (v != null) return v;
      }
    } catch (e) {}
    try {
      const v = localStorage.getItem(k);
      if (v != null) return v;
    } catch (e) {}
    return k in _mem ? _mem[k] : null;
  },
  async set(k, v) {
    try {
      if (typeof window !== "undefined" && window.storage) {
        await window.storage.set(k, v);
        return;
      }
    } catch (e) {}
    try {
      if (typeof indexedDB !== "undefined") {
        await _idb.set(k, v);
        return;
      }
    } catch (e) {}
    try {
      localStorage.setItem(k, v);
      return;
    } catch (e) {}
    _mem[k] = v;
  },
};

export function redactStoredSecrets(k, value) {
  if (k === AUTH_KEY) return '';
  if (k !== STATE_KEY || !value) return value;
  try {
    const state = JSON.parse(value);
    for (const provider of Object.values(state.settings?.providers || {})) {
      if (provider && typeof provider === 'object') provider.apiKey = '';
    }
    return JSON.stringify(state);
  } catch { return ''; }
}

async function scrubLegacyCopies(k) {
  // All fallback stores may contain old copies; clean each one independently.
  const clean = async (get, set) => {
    const value = await get(k);
    if (value != null) await set(k, redactStoredSecrets(k, value));
  };
  await Promise.allSettled([
    clean(key => _idb.get(key), (key, value) => _idb.set(key, value)),
    clean(key => localStorage.getItem(key), (key, value) => localStorage.setItem(key, value)),
    clean(async key => (await window.storage.get(key))?.value, (key, value) => window.storage.set(key, value)),
  ]);
}

export const store = {
  async get(k) {
    if (k === STATE_KEY || k === AUTH_KEY) await scrubLegacyCopies(k);
    return k === AUTH_KEY ? (_mem[k] || null) : redactStoredSecrets(k, await persistent.get(k));
  },
  async set(k, value) {
    if (k === AUTH_KEY) { _mem[k] = value; await scrubLegacyCopies(k); return; }
    await persistent.set(k, redactStoredSecrets(k, value));
  },
};
