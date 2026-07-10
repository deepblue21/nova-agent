// MCP client pure-helper tests — npm --prefix gateway test (node --test)
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  parseServers, prefixedName, splitToolName, isMcpTool,
  toToolSpec, parseRpcBody, flattenContent, describeTools,
} from "../lib/mcp.mjs";

test("parseServers: JSON array form", () => {
  const out = parseServers('[{"name":"fs","url":"http://localhost:9001/mcp","token":"t"},{"name":"x","url":"bad"}]');
  assert.equal(out.length, 1);
  assert.deepEqual(out[0], { name: "fs", url: "http://localhost:9001/mcp", token: "t" });
});

test("parseServers: name=url comma list; ignores malformed + non-http", () => {
  const out = parseServers("fs=http://a/mcp, web=https://b/mcp, junk, z=ftp://no");
  assert.deepEqual(out.map(s => s.name), ["fs", "web"]);
  assert.equal(out[1].url, "https://b/mcp");
});

test("parseServers: empty / whitespace → []", () => {
  assert.deepEqual(parseServers(""), []);
  assert.deepEqual(parseServers("   "), []);
  assert.deepEqual(parseServers(null), []);
  assert.deepEqual(parseServers("[not json"), []);
});

test("prefixedName / splitToolName / isMcpTool round-trip", () => {
  const n = prefixedName("fs", "read_file");
  assert.equal(n, "mcp__fs__read_file");
  assert.ok(isMcpTool(n));
  assert.deepEqual(splitToolName(n), { server: "fs", tool: "read_file" });
  assert.equal(splitToolName("calculator"), null);
  assert.equal(isMcpTool("web_search"), false);
});

test("toToolSpec: maps name+desc+inputSchema; defaults schema", () => {
  const spec = toToolSpec("fs", { name: "read_file", description: "Read a file", inputSchema: { type: "object", properties: { path: { type: "string" } }, required: ["path"] } });
  assert.equal(spec.type, "function");
  assert.equal(spec.function.name, "mcp__fs__read_file");
  assert.equal(spec.function.description, "Read a file");
  assert.deepEqual(spec.function.parameters.required, ["path"]);
  const bare = toToolSpec("fs", { name: "ping" });
  assert.deepEqual(bare.function.parameters, { type: "object", properties: {} });
});

test("parseRpcBody: plain JSON and SSE stream", () => {
  assert.deepEqual(parseRpcBody("application/json", '{"jsonrpc":"2.0","id":1,"result":{"ok":true}}').result, { ok: true });
  const sse = "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"n\":2}}\n\n";
  assert.deepEqual(parseRpcBody("text/event-stream; charset=utf-8", sse).result, { n: 2 });
  assert.equal(parseRpcBody("application/json", "not json"), null);
});

test("parseRpcBody: SSE picks the last valid data line", () => {
  const sse = "data: keepalive\ndata: {\"result\":{\"v\":1}}\ndata: {\"result\":{\"v\":2}}\n";
  assert.deepEqual(parseRpcBody("text/event-stream", sse).result, { v: 2 });
});

test("describeTools: maps MCP specs to server/tool pairs, skips non-MCP", () => {
  const specs = [
    toToolSpec("fs", { name: "read_file", description: "Read a file" }),
    { type: "function", function: { name: "web_search", description: "builtin" } },
    toToolSpec("db", { name: "query" }),
  ];
  const out = describeTools(specs);
  assert.equal(out.length, 2);
  assert.deepEqual(out[0], { server: "fs", tool: "read_file", name: "mcp__fs__read_file", description: "Read a file" });
  assert.equal(out[1].server, "db");
  assert.equal(describeTools([]).length, 0);
});

test("flattenContent: joins text parts, reads resource text/uri", () => {
  assert.equal(flattenContent({ content: [{ type: "text", text: "a" }, { type: "text", text: "b" }] }), "a\nb");
  assert.equal(flattenContent({ content: [{ type: "resource", resource: { uri: "file://x" } }] }), "file://x");
  assert.equal(flattenContent({}), "");
  assert.equal(flattenContent(null), "");
});
