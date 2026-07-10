// fetch_url tool guard tests — npm --prefix gateway test (node --test)
import { test } from "node:test";
import assert from "node:assert/strict";
import { isUnsafeFetchHost, htmlToText, runTool } from "../lib/tools.mjs";

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

function mockOpenMeteo({ geocode, forecast, forecastStatus = 200 } = {}) {
  const prev = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (url) => {
    const u = String(url);
    calls.push(u);
    if (u.includes("geocoding-api.open-meteo.com")) {
      const data = geocode ?? { results: [] };
      return { ok: true, status: 200, async json() { return data; }, async text() { return JSON.stringify(data); } };
    }
    if (u.includes("api.open-meteo.com")) {
      const data = forecast ?? { daily: { time: [] } };
      return {
        ok: forecastStatus >= 200 && forecastStatus < 300,
        status: forecastStatus,
        async json() { return data; },
        async text() { return JSON.stringify(data); },
      };
    }
    return { ok: false, status: 404, async json() { return {}; }, async text() { return "not mocked: " + u; } };
  };
  return { calls, restore() { globalThis.fetch = prev; } };
}

test("weather_forecast: empty geocode returns a useful no-location result", async () => {
  const fx = mockOpenMeteo({ geocode: { results: [] } });
  try {
    const r = await runTool("weather_forecast", { location: "Atlantis" });
    assert.equal(r.ok, true);
    assert.match(r.text, /konum bulunamadı/);
    assert.deepEqual(r.sources, []);
    assert.equal(fx.calls.length, 1);
  } finally { fx.restore(); }
});

test("weather_forecast: forecast upstream errors are surfaced as tool errors", async () => {
  const fx = mockOpenMeteo({
    geocode: { results: [{ name: "Manisa", country: "Türkiye", latitude: 38.61, longitude: 27.43 }] },
    forecastStatus: 503,
  });
  try {
    const r = await runTool("weather_forecast", { location: "Manisa, Türkiye" });
    assert.equal(r.ok, false);
    assert.match(r.text, /Araç hatası \(weather_forecast\): forecast 503/);
  } finally { fx.restore(); }
});

test("weather_forecast: missing numeric fields do not produce NaN output", async () => {
  const fx = mockOpenMeteo({
    geocode: { results: [{ name: "Manisa", admin1: "Manisa", country: "Türkiye", latitude: 38.61, longitude: 27.43 }] },
    forecast: {
      daily: {
        time: ["2026-06-16", "2026-06-17"],
        weather_code: [0, 3],
        precipitation_probability_max: [10, 55],
      },
    },
  });
  try {
    const r = await runTool("weather_forecast", { location: "Manisa, Türkiye", date: "2099-01-01" });
    assert.equal(r.ok, true);
    assert.match(r.text, /Manisa/);
    assert.match(r.text, /2026-06-17/);
    assert.match(r.text, /Sıcaklık: bilinmiyor – bilinmiyor/);
    assert.doesNotMatch(r.text, /NaN/);
    assert.equal(r.sources[0].url, "https://open-meteo.com/");
  } finally { fx.restore(); }
});
