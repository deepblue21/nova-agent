// Gerçek UDP soketi üzerinden uçtan uca kablo testi.
//
// Kapsam DÜRÜSTÇE sınırlı: burada multicast grubuna katılma (addMembership)
// DOĞRULANMAZ — CI konteynerinde LAN arayüzü olmayabilir. Doğrulanan şey,
// announce-mdns.mjs'in kullandığı boru hattının tamamı:
//     decodeMessage → answersFor → encodeMessage
// gerçek bir sokete yazılıp okunduğunda telefonun beklediği baytları veriyor mu.
import { test } from "node:test";
import assert from "node:assert/strict";
import dgram from "node:dgram";
import { once } from "node:events";
import {
  TYPE,
  encodeMessage,
  decodeMessage,
  decodeName,
  decodeTxt,
  serviceRecords,
  answersFor,
} from "./lib/mdns.mjs";

const records = serviceRecords({
  instance: "SALIH-PC",
  serviceType: "_horus._tcp",
  hostname: "salih-pc",
  ip: "192.168.1.20",
  port: 8088,
  txt: { v: "1", path: "/v1", tls: "0", name: "SALIH-PC" },
});

/** announce-mdns.mjs'in message işleyicisiyle aynı mantık. */
function handle(msg) {
  const decoded = decodeMessage(msg);
  if (!decoded || (decoded.flags & 0x8000) !== 0) return null;
  const answers = answersFor(decoded.questions, records);
  if (!answers.length) return null;
  return encodeMessage({ answers });
}

async function roundTrip(queryBuf) {
  const responder = dgram.createSocket({ type: "udp4", reuseAddr: true });
  const client = dgram.createSocket({ type: "udp4", reuseAddr: true });
  try {
    responder.bind(0, "127.0.0.1");
    await once(responder, "listening");
    responder.on("message", (msg, rinfo) => {
      const reply = handle(msg);
      if (reply) responder.send(reply, rinfo.port, rinfo.address);
    });

    client.bind(0, "127.0.0.1");
    await once(client, "listening");
    const replyPromise = Promise.race([
      once(client, "message").then(([m]) => m),
      new Promise((_, rej) => setTimeout(() => rej(new Error("yanıt gelmedi (2 sn)")), 2000)),
    ]);
    client.send(queryBuf, responder.address().port, "127.0.0.1");
    return await replyPromise;
  } finally {
    responder.close();
    client.close();
  }
}

test("gerçek soket: PTR sorgusu tam servis kümesini döner", async () => {
  const query = encodeMessage({ flags: 0, questions: [{ name: "_horus._tcp.local", type: TYPE.PTR }] });
  const reply = decodeMessage(await roundTrip(query));

  assert.ok(reply, "yanıt çözülebilmeli");
  assert.equal((reply.flags & 0x8000) !== 0, true, "QR biti set olmalı (bu bir yanıt)");
  assert.equal(reply.answers.length, 4);

  const byType = Object.fromEntries(reply.answers.map((r) => [r.type, r]));

  assert.equal(
    decodeName(byType[TYPE.PTR].rdata, 0).name,
    "SALIH-PC._horus._tcp.local",
    "PTR örnek adına işaret etmeli",
  );

  const srv = byType[TYPE.SRV].rdata;
  assert.equal(srv.readUInt16BE(4), 8088, "SRV gateway portunu taşımalı");
  assert.equal(decodeName(srv, 6).name, "salih-pc.local", "SRV hedefi host adı olmalı");

  assert.deepEqual(decodeTxt(byType[TYPE.TXT].rdata), {
    v: "1", path: "/v1", tls: "0", name: "SALIH-PC",
  }, "TXT telefonun Base URL kurması için gereken her şeyi taşımalı");

  assert.deepEqual([...byType[TYPE.A].rdata], [192, 168, 1, 20], "A kaydı LAN IP'si olmalı");
});

test("gerçek soket: ilgisiz sorguya hiç paket gönderilmez", async () => {
  const query = encodeMessage({ flags: 0, questions: [{ name: "_airplay._tcp.local", type: TYPE.PTR }] });
  await assert.rejects(() => roundTrip(query), /yanıt gelmedi/, "başkasının servisine cevap vermemeli");
});

test("gerçek soket: yanıt paketleri yok sayılır (yayın fırtınası olmaz)", async () => {
  // Bir başka responder'ın yanıtı: QR biti set. Buna cevap verilirse iki
  // responder sonsuz döngüye girer.
  const otherResponse = encodeMessage({ answers: [records.ptr] });
  await assert.rejects(() => roundTrip(otherResponse), /yanıt gelmedi/);
});

test("gerçek soket: çöp paket işleyiciyi düşürmez", async () => {
  const garbage = Buffer.from([0xff, 0x00, 0x13, 0x37, 0x99]);
  await assert.rejects(() => roundTrip(garbage), /yanıt gelmedi/, "çöp sessizce atılmalı");

  // Aynı işleyici hemen ardından geçerli sorguyu hâlâ yanıtlayabilmeli.
  const query = encodeMessage({ flags: 0, questions: [{ name: "salih-pc.local", type: TYPE.A }] });
  const reply = decodeMessage(await roundTrip(query));
  assert.equal(reply.answers.length, 1);
  assert.equal(reply.answers[0].type, TYPE.A);
});
