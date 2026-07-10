import pdfParse from "pdf-parse/lib/pdf-parse.js";
import mammoth from "mammoth";

const PDF_MIME = "application/pdf";
const DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
const TEXT_MIMES = new Set([
  "text/plain",
  "text/markdown",
  "text/csv",
  "application/json",
  "application/x-ndjson",
]);
const TEXT_EXTS = new Set([".txt", ".md", ".markdown", ".csv", ".json", ".log"]);

function httpError(status, message) {
  const e = new Error(message);
  e.status = status;
  return e;
}

function cleanTitle(s = "") {
  return String(s || "Belge").replace(/\.[^.\\/]+$/, "").trim().slice(0, 200) || "Belge";
}

function extOf(name = "") {
  const m = /\.[^.\\/]+$/.exec(String(name).toLowerCase());
  return m ? m[0] : "";
}

function stripDataUrl(s = "") {
  const raw = String(s || "");
  const m = /^data:([^;,]+)?(?:;[^,]*)?;base64,(.*)$/i.exec(raw);
  return m ? { mime: m[1] || "", b64: m[2] || "" } : { mime: "", b64: raw };
}

function decodeFile(file, maxFileBytes) {
  if (!file || typeof file !== "object") throw httpError(400, "file gerekli");
  const name = String(file.name || "");
  const fromDataUrl = file.data_url || file.dataUrl || "";
  const data = fromDataUrl ? stripDataUrl(fromDataUrl) : { mime: "", b64: String(file.b64 || file.base64 || "") };
  const mime = String(file.mime || file.type || data.mime || "").toLowerCase();
  const b64 = String(data.b64 || "").replace(/\s+/g, "");
  if (!b64) throw httpError(400, "file.b64 gerekli");
  const buf = Buffer.from(b64, "base64");
  if (!buf.length) throw httpError(400, "dosya boş");
  if (maxFileBytes && buf.length > maxFileBytes)
    throw httpError(413, "dosya çok büyük (max " + Math.round(maxFileBytes / 1024) + " KB)");
  return { name, mime, ext: extOf(name), buffer: buf };
}

function isTextFile({ mime, ext }) {
  return TEXT_MIMES.has(mime) || mime.startsWith("text/") || TEXT_EXTS.has(ext);
}

async function extractFileText(file, maxFileBytes) {
  const f = decodeFile(file, maxFileBytes);
  if (isTextFile(f)) return { title: cleanTitle(f.name), text: f.buffer.toString("utf8"), kind: "text" };

  if (f.mime === PDF_MIME || f.ext === ".pdf") {
    try {
      const out = await pdfParse(f.buffer);
      return { title: cleanTitle(f.name), text: out.text || "", kind: "pdf" };
    } catch (e) {
      throw httpError(422, "PDF metni çıkarılamadı: " + (e.message || e));
    }
  }

  if (f.mime === DOCX_MIME || f.ext === ".docx") {
    try {
      const out = await mammoth.extractRawText({ buffer: f.buffer });
      return { title: cleanTitle(f.name), text: out.value || "", kind: "docx" };
    } catch (e) {
      throw httpError(422, "DOCX metni çıkarılamadı: " + (e.message || e));
    }
  }

  throw httpError(415, "desteklenmeyen belge türü: " + (f.mime || f.ext || "bilinmiyor"));
}

export async function normalizeKnowledgeInput(body = {}, { maxTextBytes = 1048576, maxFileBytes = 10485760 } = {}) {
  let title = body.title || "Belge";
  let text = "";
  let kind = "text";

  if (body.file) {
    const extracted = await extractFileText(body.file, maxFileBytes);
    title = body.title || extracted.title;
    text = extracted.text;
    kind = extracted.kind;
  } else if (typeof body.text === "string") {
    text = body.text;
  }

  text = String(text || "").trim();
  if (text.length < 20) throw httpError(400, "text (min 20 karakter) gerekli");
  if (Buffer.byteLength(text) > maxTextBytes)
    throw httpError(413, "belge çok büyük (max " + Math.round(maxTextBytes / 1024) + " KB)");

  return { title: cleanTitle(title), text, kind };
}

export const DOC_TYPES = { PDF_MIME, DOCX_MIME };
