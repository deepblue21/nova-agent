// serve-apk.mjs - APK'yi CORS basligiyla yerel olarak yayinlar (Drive yuklemesi icin gecici).
// Kullanim:  node scripts/serve-apk.mjs
// Sonra tarayici tarafi fetch('http://127.0.0.1:9099/apk') ile dosyayi ceker.
import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const apk = path.join(root, "NOVA-Horus-debug.apk");
const PORT = 9099;

if (!fs.existsSync(apk)) {
  console.error("APK bulunamadi:", apk);
  process.exit(1);
}
const size = fs.statSync(apk).size;

const server = http.createServer((req, res) => {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "*");
  // Chrome Private Network Access: guvenli origin -> localhost izni
  res.setHeader("Access-Control-Allow-Private-Network", "true");
  if (req.method === "OPTIONS") { res.writeHead(204); res.end(); return; }
  if (req.url === "/apk") {
    res.writeHead(200, {
      "Content-Type": "application/vnd.android.package-archive",
      "Content-Length": size,
    });
    fs.createReadStream(apk).pipe(res);
    console.log("APK servis edildi ->", req.socket.remoteAddress);
  } else {
    res.writeHead(200, { "Content-Type": "text/plain" });
    res.end(`ready: ${(size / 1048576).toFixed(1)} MB @ /apk`);
  }
});
server.listen(PORT, "127.0.0.1", () => {
  console.log(`APK yayinda: http://127.0.0.1:${PORT}/apk  (${(size / 1048576).toFixed(1)} MB)`);
  console.log("Yukleme bitince bu pencerede Ctrl+C ile durdurabilirsin.");
});
