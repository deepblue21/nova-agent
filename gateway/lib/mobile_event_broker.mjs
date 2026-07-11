import { EventEmitter } from "node:events";

export class MobileEventBroker {
  #events = new EventEmitter();

  constructor() {
    this.#events.setMaxListeners(1000);
  }

  publish(event) {
    this.#events.emit(String(event.task_id), event);
  }

  subscribe(taskId, listener) {
    const key = String(taskId);
    this.#events.on(key, listener);
    return () => this.#events.off(key, listener);
  }
}

export function formatSseEvent(event) {
  return `id: ${event.id}\nevent: ${event.type}\ndata: ${JSON.stringify(event)}\n\n`;
}

export const mobileEventBroker = new MobileEventBroker();
