import { applyTaskCommand, isTerminal, TASK_STATUSES } from "../../lib/mobile_task_state.mjs";

function clone(value) {
  return structuredClone(value);
}

function id(sequence) {
  return `00000000-0000-4000-8000-${String(sequence).padStart(12, "0")}`;
}

export function createMemoryMobileTaskStore() {
  const tasks = new Map();
  const confirmations = new Map();
  const events = [];
  let taskSequence = 0;
  let confirmationSequence = 0;
  let eventSequence = 0;

  function ownedTask(userId, taskId) {
    const task = tasks.get(taskId);
    return task?.user_id === userId ? task : null;
  }

  function appendEvent(taskId, type, payload) {
    const event = {
      id: String(++eventSequence),
      task_id: taskId,
      type,
      payload: clone(payload),
      created_at: new Date().toISOString(),
    };
    events.push(event);
    return clone(event);
  }

  return {
    async createTask(userId, { prompt, deviceId = null }) {
      const normalizedPrompt = String(prompt ?? "").trim();
      if (!normalizedPrompt) throw new Error("prompt is required");
      const now = new Date().toISOString();
      const task = {
        id: id(++taskSequence),
        user_id: userId,
        prompt: normalizedPrompt,
        device_id: deviceId == null ? null : String(deviceId),
        status: "queued",
        created_at: now,
        updated_at: now,
      };
      tasks.set(task.id, task);
      return { task: clone(task), event: appendEvent(task.id, "task.created", { status: task.status }) };
    },

    async listTasks(userId, requestedLimit = 50) {
      const limit = Math.min(100, Math.max(1, Number(requestedLimit) || 50));
      return [...tasks.values()]
        .filter(task => task.user_id === userId)
        .sort((left, right) => right.created_at.localeCompare(left.created_at))
        .slice(0, limit)
        .map(clone);
    },

    async getTask(userId, taskId) {
      const task = ownedTask(userId, taskId);
      return task ? clone(task) : null;
    },

    async listEvents(userId, taskId, afterId = 0, requestedLimit = 500) {
      if (!ownedTask(userId, taskId)) return [];
      const limit = Math.min(500, Math.max(1, Number(requestedLimit) || 500));
      return events
        .filter(event => event.task_id === taskId && Number(event.id) > Number(afterId))
        .sort((left, right) => Number(left.id) - Number(right.id))
        .slice(0, limit)
        .map(clone);
    },

    async applyCommand(userId, taskId, command, note = "") {
      const task = ownedTask(userId, taskId);
      if (!task) return null;
      const previousStatus = task.status;
      task.status = applyTaskCommand(task.status, command);
      task.updated_at = new Date().toISOString();
      const event = appendEvent(taskId, `task.${command}`, {
        command,
        note: String(note ?? ""),
        previous_status: previousStatus,
        status: task.status,
      });
      return { task: clone(task), event };
    },

    async requestConfirmation(userId, taskId, input) {
      const task = ownedTask(userId, taskId);
      if (!task) return null;
      if (isTerminal(task.status)) throw new Error("terminal task cannot be changed");
      if (input?.riskLevel !== "R2" && input?.riskLevel !== "R3") throw new Error("risk level must be R2 or R3");
      if (input?.action === undefined) throw new Error("confirmation action is required");
      if ([...confirmations.values()].some(confirmation => confirmation.task_id === taskId && confirmation.status === "pending")) {
        throw new Error("task already has a pending confirmation");
      }
      const resumeStatus = String(input.resumeStatus || "executing");
      if (!TASK_STATUSES.includes(resumeStatus) || isTerminal(resumeStatus)) {
        throw new Error("resume status must be a non-terminal task status");
      }
      const confirmation = {
        id: id(++confirmationSequence),
        task_id: taskId,
        risk_level: input.riskLevel,
        action: clone(input.action),
        resume_status: resumeStatus,
        status: "pending",
        decided_at: null,
        created_at: new Date().toISOString(),
      };
      confirmations.set(confirmation.id, confirmation);
      task.status = "waiting_for_confirmation";
      task.updated_at = new Date().toISOString();
      const event = appendEvent(taskId, "confirmation.requested", {
        confirmation_id: confirmation.id,
        risk_level: confirmation.risk_level,
        status: task.status,
      });
      return { task: clone(task), event, confirmation: clone(confirmation) };
    },

    async resolveConfirmation(userId, taskId, confirmationId, decision) {
      if (decision !== "approve" && decision !== "reject") throw new Error("confirmation decision must be approve or reject");
      const task = ownedTask(userId, taskId);
      if (!task) return null;
      const confirmation = confirmations.get(confirmationId);
      if (!confirmation || confirmation.task_id !== taskId || confirmation.status !== "pending") return null;
      const waitingForConfirmation = task.status === "waiting_for_confirmation";
      if (decision === "approve" && !waitingForConfirmation) {
        throw new Error("task must be waiting_for_confirmation to resolve confirmation");
      }

      confirmation.status = decision === "approve" ? "approved" : "rejected";
      confirmation.decided_at = new Date().toISOString();
      if (waitingForConfirmation) {
        task.status = decision === "approve" ? confirmation.resume_status : "paused";
        task.updated_at = new Date().toISOString();
      }
      const event = appendEvent(taskId, `confirmation.${confirmation.status}`, { status: task.status });
      return { task: clone(task), event, confirmation: clone(confirmation) };
    },
  };
}
