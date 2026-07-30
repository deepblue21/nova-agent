// Kalıcı depo. Sıra: claude.ai artefakt köprüsü (window.storage) → IndexedDB
// (kendi origin'inde; büyük sohbet geçmişi + data-URL görseller için kota derdi
// yok) → localStorage → bellek. Bu sıra olmadan her sayfa yenilemesinde API key
// ve sohbetler sıfırlanıyordu.

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

export const store = {
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
