// Scheduled runner tests: agent flag must select direct chat vs tool-using agent.
import { test } from "node:test";
import assert from "node:assert/strict";
import { runScheduledTask } from "../lib/scheduled_runner.mjs";

test("scheduled runner: agent=false uses direct provider chat", async () => {
  let chatArgs = null;
  let agentCalls = 0;
  const out = await runScheduledTask(
    { user_id: "u1", prompt: "özetle", model: "ollama/qwen3:14b", agent: false },
    {
      defaultModel: "ollama/qwen3:14b",
      maxRetries: 0,
      providerClient: {
        chat: async (args) => {
          chatArgs = args;
          return "direkt cevap";
        },
      },
      runAgentImpl: async () => {
        agentCalls++;
        return { content: "ajan cevabı" };
      },
    },
  );
  assert.equal(out.status, "ok");
  assert.equal(out.result, "direkt cevap");
  assert.equal(agentCalls, 0);
  assert.equal(chatArgs.provider, "ollama");
  assert.equal(chatArgs.model, "qwen3:14b");
  assert.equal(chatArgs.stream, false);
  assert.equal(chatArgs.res, null);
  assert.equal(chatArgs.ctx.retries, 0);
  assert.ok(chatArgs.messages.some((m) => m.role === "user" && m.content === "özetle"));
});

test("scheduled runner: agent=true/default uses runAgent", async () => {
  let agentArgs = null;
  let chatCalls = 0;
  const out = await runScheduledTask(
    { user_id: "u1", prompt: "araştır", model: "ollama/qwen3:14b" },
    {
      ollamaBase: "http://ollama-test:11434",
      providerClient: { chat: async () => { chatCalls++; return "direct"; } },
      runAgentImpl: async (args) => {
        agentArgs = args;
        return { content: "ajan cevabı" };
      },
    },
  );
  assert.equal(out.status, "ok");
  assert.equal(out.result, "ajan cevabı");
  assert.equal(chatCalls, 0);
  assert.equal(agentArgs.ollamaBase, "http://ollama-test:11434");
  assert.equal(agentArgs.model, "qwen3:14b");
  assert.equal(agentArgs.userId, "u1");
});

test("scheduled runner: non-ollama models are rejected", async () => {
  const out = await runScheduledTask({ prompt: "x", model: "openai/gpt-4o-mini", agent: false });
  assert.equal(out.status, "error");
  assert.match(out.result, /ollama/);
});
