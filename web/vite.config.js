import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Vite dev/build config for the NOVA Agent UI.
// The dev server runs on 5173 — keep this origin in the Gateway's
// ALLOW_ORIGINS list so browser → gateway calls are not blocked by CORS.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
  },
  preview: {
    port: 4173,
    strictPort: true,
  },
  build: {
    outDir: "dist",
    sourcemap: false,
  },
});
