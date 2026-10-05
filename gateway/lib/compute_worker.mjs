import { parentPort, workerData } from 'node:worker_threads';
import { inflateRawSync } from 'node:zlib';

// Validate real inflated sizes before the DOCX parser; ZIP header sizes alone
// are untrusted. ZIP64, encryption and multi-disk archives are unsupported.
function checkDocx(buffer) {
  let end = -1;
  for (let i = buffer.length - 22; i >= Math.max(0, buffer.length - 65557); i--) {
    if (buffer.readUInt32LE(i) === 0x06054b50 && i + 22 + buffer.readUInt16LE(i + 20) === buffer.length) { end = i; break; }
  }
  if (end < 0 || buffer.readUInt16LE(end + 4) || buffer.readUInt16LE(end + 6)) throw new Error('Invalid ZIP');
  const count = buffer.readUInt16LE(end + 10);
  let pos = buffer.readUInt32LE(end + 16), total = 0;
  if (!count || count > 1000 || buffer.readUInt16LE(end + 8) !== count) throw new Error('ZIP entry limit');
  for (let i = 0; i < count; i++) {
    if (pos + 46 > end || buffer.readUInt32LE(pos) !== 0x02014b50) throw new Error('Invalid ZIP directory');
    const flags = buffer.readUInt16LE(pos + 8), method = buffer.readUInt16LE(pos + 10);
    const compressed = buffer.readUInt32LE(pos + 20), size = buffer.readUInt32LE(pos + 24), local = buffer.readUInt32LE(pos + 42);
    if ((flags & 1) || ![0, 8].includes(method) || size > 32 * 1024 * 1024 - total || local + 30 > pos) throw new Error('ZIP size or format limit');
    if (buffer.readUInt32LE(local) !== 0x04034b50 || buffer.readUInt16LE(local + 8) !== method) throw new Error('Invalid ZIP entry');
    const start = local + 30 + buffer.readUInt16LE(local + 26) + buffer.readUInt16LE(local + 28);
    if (start + compressed > pos) throw new Error('Invalid ZIP entry size');
    const raw = buffer.subarray(start, start + compressed);
    const bytes = method === 0 ? raw : inflateRawSync(raw, {maxOutputLength: Math.max(1, 32 * 1024 * 1024 - total)});
    if (bytes.length !== size) throw new Error('ZIP size mismatch');
    total += bytes.length;
    pos += 46 + buffer.readUInt16LE(pos + 28) + buffer.readUInt16LE(pos + 30) + buffer.readUInt16LE(pos + 32);
  }
  if (pos !== end) throw new Error('Invalid ZIP directory end');
}

try {
  let value;
  if (workerData.kind === 'code') {
    const {runJavaScriptSandbox} = await import('./code_sandbox_worker.mjs');
    value = await runJavaScriptSandbox(workerData.payload);
  } else {
    const {kind, bytes, maxTextBytes} = workerData.payload;
    const buffer = Buffer.from(bytes);
    let text;
    if (kind === 'pdf') {
      const {default: pdfParse} = await import('pdf-parse/lib/pdf-parse.js');
      text = (await pdfParse(buffer)).text || '';
    } else {
      checkDocx(buffer);
      const {default: mammoth} = await import('mammoth');
      text = (await mammoth.extractRawText({buffer})).value || '';
    }
    if (Buffer.byteLength(text) > maxTextBytes) throw Object.assign(new Error('belge çok büyük'), {status: 413});
    value = text;
  }
  parentPort.postMessage({ok: true, value});
} catch (e) {
  parentPort.postMessage({ok: false, error: workerData.kind === 'document' ? 'Belge okunamadı veya işleme sınırını aştı.' : String(e.message).slice(0, 300), status: e.status || 422});
}
