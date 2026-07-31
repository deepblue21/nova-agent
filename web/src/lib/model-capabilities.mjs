const KNOWN_SOURCES = new Set(["probe", "provider", "gateway", "agent", "family"]);
const VERIFIED_SOURCES = new Set(["probe", "provider", "gateway", "agent"]);

const LABELS = {
  thinking: "düşünme",
  tools: "araç çağırma",
};

export function normalizeCapabilityFields(model = {}) {
  const toolsSource = model.toolsSource || "unknown";
  const thinkingSource = model.thinkingSource || "unknown";
  return {
    tools: model.tools === true,
    toolsSource,
    toolsKnown: KNOWN_SOURCES.has(toolsSource),
    toolsVerified: VERIFIED_SOURCES.has(toolsSource),
    thinking: model.thinking === true,
    thinkingSource,
    thinkingKnown: KNOWN_SOURCES.has(thinkingSource),
    thinkingVerified: VERIFIED_SOURCES.has(thinkingSource),
    thinkingMode: model.thinkingMode || (model.thinking === true ? "toggle" : "none"),
  };
}

export function capabilityState(item = {}, capability) {
  const source = item[`${capability}Source`] || "unknown";
  const known = KNOWN_SOURCES.has(source);
  const supported = known && item[capability] === true;
  if (supported) return { supported: true, known: true, reason: "" };

  const name = item.name || "Bu model";
  if (!known) {
    return {
      supported: false,
      known: false,
      reason: `${name} için ${LABELS[capability] || capability} yeteneği doğrulanamadı.`,
    };
  }
  if (capability === "tools") {
    return {
      supported: false,
      known: true,
      reason: `${name} araç çağırmayı desteklemiyor; ajan, takım ve web araması kullanılamaz.`,
    };
  }
  return {
    supported: false,
    known: true,
    reason: `${name} düşünme özelliğini desteklemiyor.`,
  };
}

export function canDisableThinking(item = {}) {
  return item.thinkingMode !== "levels";
}

export function resolveThinkRequest(item, { reasoning = false, effort = "balanced" } = {}) {
  if (!capabilityState(item, "thinking").supported) return false;
  if (item.thinkingMode === "levels") {
    if (!reasoning) return "low";
    return ({ fast: "low", balanced: "medium", deep: "high", max: "high" })[effort] || "medium";
  }
  return Boolean(reasoning);
}

export function resolveExecutionCapabilities(item, {
  reasoning = false,
  effort = "balanced",
  agentMode = false,
  teamMode = false,
  liveTool = false,
} = {}) {
  const think = resolveThinkRequest(item, { reasoning, effort });
  const toolsActive = capabilityState(item, "tools").supported;
  return {
    think,
    reasoningActive: think !== false,
    agent: toolsActive && (Boolean(agentMode) || Boolean(liveTool)),
    team: toolsActive && Boolean(teamMode),
    toolsActive,
  };
}
