// Observe the real application socket without replacing its bind arguments.
import { Server } from "node:net";
const listen = Server.prototype.listen;
Server.prototype.listen = function (...args) {
  this.once("listening", () => process.send?.(this.address()));
  return listen.apply(this, args);
};
await import("../gateway.mjs");
