import { after, before, test } from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { createServer } from "vite";
import { CHAT_CSS } from "../src/styles/chat.mjs";

let vite;
let ChatView;
let cacheDir;

before(async () => {
  cacheDir = await mkdtemp(join(tmpdir(), "nova-chat-mark-"));
  vite = await createServer({
    root: fileURLToPath(new URL("..", import.meta.url)),
    cacheDir,
    configFile: false,
    logLevel: "silent",
    server: { middlewareMode: true },
  });
  ({ ChatView } = await vite.ssrLoadModule("/src/views/ChatView.jsx"));
});

after(async () => {
  await vite?.close();
  await rm(cacheDir, { force: true, recursive: true });
});

function renderMessage(message, busy) {
  return renderToStaticMarkup(React.createElement(ChatView, {
    messages: [message],
    busy,
    greeting: "Merhaba",
    userName: "",
    modelName: "Qwen",
    modelId: "qwen",
    input: "",
    onInput() {},
    onSend() {},
    onStop() {},
    onRegenerate() {},
    onArtifact() {},
    pending: [],
    onAddImages() {},
    onRemoveImage() {},
    imageRouteHint: "",
  }));
}

test("asistan işareti beklerken 40 px thinking hareketi kullanır", () => {
  const html = renderMessage({ role: "assistant", content: "", thinking: true, thoughts: "" }, true);
  assert.match(html, /class="nova-mark is-animated is-thinking"[^>]*width="40"[^>]*height="40"/);
});

test("asistan işareti yanıt tamamlanınca 40 px sakin marka hareketini sürdürür", () => {
  const html = renderMessage({ role: "assistant", content: "Merhaba", thinking: false }, false);
  assert.match(html, /class="nova-mark is-animated is-brand"[^>]*width="40"[^>]*height="40"/);
});

test("asistan avatar yuvası işaretle aynı 40 px ölçüsündedir", () => {
  assert.match(CHAT_CSS, /\.avatar\s*{\s*width:\s*40px;\s*height:\s*40px;/);
});
