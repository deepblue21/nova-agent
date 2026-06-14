// fetch_url tool guard tests — npm --prefix gateway test (node --test)
import { test } from "node:test";
import assert from "node:assert/strict";
import { isUnsafeFetchHost, htmlToText } from "../lib/tools.mjs";

test("isUnsafeFetchHost: blocks localhost, private IPs, internal TLDs", () => {
  for (const h of [
    "localhost", "LOCALHOST", "ip6-localhost", "127.0.0.1", "10.1.2.3",
    "192.168.0.5", "172.16.0.1", "169.254.169.254", "0.0.0.0",
    "foo.local", "svc.internal", "metadata.google.internal", "",
  ]) {
    assert.equal(isUnsafeFetchHost(h), true, "should block: " + h);
  }
});

test("isUnsafeFetchHost: allows public hostnames", () => {
  for (const h of ["example.com", "www.wikipedia.org", "api.github.com", "8.8.8.8"]) {
    assert.equal(isUnsafeFetchHost(h), false, "should allow: " + h);
  }
});

test("htmlToText: strips scripts/styles/tags, decodes entities, truncates", () => {
  const html = "<html><head><style>.x{}</style></head><body><h1>Başlık</h1><script>alert(1)</script><p>Merhaba &amp; dünya</p></body></html>";
  const t = htmlToText(html);
  assert.match(t, /Başlık/);
  assert.match(t, /Merhaba & dünya/);
  assert.doesNotMatch(t, /alert\(1\)/);
  assert.doesNotMatch(t, /<[^>]+>/);
  const long = htmlToText("x".repeat(7000), 100);
  assert.ok(long.length <= 130 && /kısaltıldı/.test(long));
});
