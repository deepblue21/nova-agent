import test from "node:test";
import assert from "node:assert/strict";

const policy = await import("../src/lib/model-capabilities.mjs").catch(() => ({}));

test("capabilityState: ölçülmüş destek, ölçülmüş ret ve bilinmeyen modeli ayırır", () => {
  assert.equal(typeof policy.capabilityState, "function", "yetenek politikası dışa aktarılmalı");

  assert.deepEqual(
    policy.capabilityState({ name: "Qwen", thinking: true, thinkingSource: "probe" }, "thinking"),
    { supported: true, known: true, reason: "" },
  );
  assert.deepEqual(
    policy.capabilityState({ name: "Hermes", thinking: false, thinkingSource: "probe" }, "thinking"),
    {
      supported: false,
      known: true,
      reason: "Hermes düşünme özelliğini desteklemiyor.",
    },
  );
  assert.deepEqual(
    policy.capabilityState({ name: "Özel model", thinking: false, thinkingSource: "unknown" }, "thinking"),
    {
      supported: false,
      known: false,
      reason: "Özel model için düşünme yeteneği doğrulanamadı.",
    },
  );
});

test("capabilityState: araç açıklaması web aramasının araç gerektirdiğini söyler", () => {
  assert.deepEqual(
    policy.capabilityState({ name: "Gemma", tools: false, toolsSource: "probe" }, "tools"),
    {
      supported: false,
      known: true,
      reason: "Gemma araç çağırmayı desteklemiyor; ajan, takım ve web araması kullanılamaz.",
    },
  );
});

test("resolveThinkRequest: GPT-OSS çabayı yerel seviyelere eşler", () => {
  const item = {
    name: "gpt-oss:20b",
    thinking: true,
    thinkingSource: "probe",
    thinkingMode: "levels",
  };
  assert.equal(policy.resolveThinkRequest(item, { reasoning: true, effort: "fast" }), "low");
  assert.equal(policy.resolveThinkRequest(item, { reasoning: true, effort: "balanced" }), "medium");
  assert.equal(policy.resolveThinkRequest(item, { reasoning: true, effort: "deep" }), "high");
  assert.equal(policy.resolveThinkRequest(item, { reasoning: true, effort: "max" }), "high");
  assert.equal(
    policy.resolveThinkRequest(item, { reasoning: false, effort: "max" }),
    "low",
    "GPT-OSS düşünme izini kapatamadığı için en düşük gerçek seviye kullanılmalı",
  );
});

test("resolveThinkRequest: aç-kapat modeli ve desteklenmeyen modeli dürüstçe eşler", () => {
  const toggle = {
    name: "qwen3:8b",
    thinking: true,
    thinkingSource: "probe",
    thinkingMode: "toggle",
  };
  assert.equal(policy.resolveThinkRequest(toggle, { reasoning: true, effort: "max" }), true);
  assert.equal(policy.resolveThinkRequest(toggle, { reasoning: false, effort: "max" }), false);
  assert.equal(policy.resolveThinkRequest(
    { name: "hermes3:8b", thinking: false, thinkingSource: "probe", thinkingMode: "none" },
    { reasoning: true, effort: "max" },
  ), false);
});

test("canDisableThinking: seviye zorunlu modelde kapatma anahtarını kilitler", () => {
  assert.equal(policy.canDisableThinking({ thinkingMode: "levels" }), false);
  assert.equal(policy.canDisableThinking({ thinkingMode: "toggle" }), true);
});

test("normalizeCapabilityFields: katalog değerlerini bilinen ve doğrulanmış olarak ayırır", () => {
  assert.deepEqual(policy.normalizeCapabilityFields({
    tools: true,
    toolsSource: "family",
    thinking: true,
    thinkingSource: "probe",
    thinkingMode: "toggle",
  }), {
    tools: true,
    toolsSource: "family",
    toolsKnown: true,
    toolsVerified: false,
    thinking: true,
    thinkingSource: "probe",
    thinkingKnown: true,
    thinkingVerified: true,
    thinkingMode: "toggle",
  });
});

test("resolveExecutionCapabilities: kayıtlı tercihleri desteklenmeyen modele göndermeyi engeller", () => {
  const unsupported = {
    name: "Hermes 3",
    thinking: false,
    thinkingSource: "probe",
    thinkingMode: "none",
    tools: false,
    toolsSource: "probe",
  };
  assert.deepEqual(policy.resolveExecutionCapabilities(unsupported, {
    reasoning: true,
    effort: "max",
    agentMode: true,
    teamMode: true,
    liveTool: true,
  }), {
    think: false,
    reasoningActive: false,
    agent: false,
    team: false,
    toolsActive: false,
  });

  const supported = {
    name: "GPT-OSS",
    thinking: true,
    thinkingSource: "probe",
    thinkingMode: "levels",
    tools: true,
    toolsSource: "probe",
  };
  assert.deepEqual(policy.resolveExecutionCapabilities(supported, {
    reasoning: true,
    effort: "balanced",
    agentMode: false,
    teamMode: true,
    liveTool: true,
  }), {
    think: "medium",
    reasoningActive: true,
    agent: true,
    team: true,
    toolsActive: true,
  });
});
