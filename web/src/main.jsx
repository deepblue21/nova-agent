import React from "react";
import { createRoot } from "react-dom/client";
import App from "./nova-agent.jsx";

const el = document.getElementById("root");
createRoot(el).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);

// PWA: register the offline-shell service worker (production build only).
if (import.meta.env.PROD && "serviceWorker" in navigator) {
  window.addEventListener("load", () => {
    navigator.serviceWorker.register("/sw.js").catch(() => {});
  });
}
