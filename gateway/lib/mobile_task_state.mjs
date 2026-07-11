export const TASK_STATUSES = Object.freeze([
  "queued", "routing", "observing", "planning", "executing", "verifying",
  "waiting_for_confirmation", "waiting_for_device", "waiting_for_compute",
  "paused", "completed", "failed", "cancelled",
]);

export const TERMINAL_STATUSES = new Set(["completed", "failed", "cancelled"]);

export function isTerminal(status) {
  return TERMINAL_STATUSES.has(status);
}

export function applyTaskCommand(status, command) {
  if (!TASK_STATUSES.includes(status)) throw new Error("unknown task status");
  if (isTerminal(status)) throw new Error("terminal task cannot be changed");
  if (command === "cancel") return "cancelled";
  if (command === "pause") {
    if (status === "paused") return status;
    return "paused";
  }
  if (command === "resume") {
    if (status !== "paused") throw new Error("cannot resume a task that is not paused");
    return "queued";
  }
  if (command === "steer") return status;
  throw new Error("unknown command");
}
