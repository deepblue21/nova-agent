// Media upload: accept a base64 data URL (or {mime,b64}), store in object
// storage, return a reference + short-lived signed URL. Requires req.principal.
import { Router } from "express";
import { putMedia, signedGetUrl } from "../lib/storage.mjs";
import { mediaInputConfig, normalizeMediaInput } from "../lib/media_input.mjs";

export const media = Router();

media.post("/v1/media", async (req, res) => {
  try {
    const media = normalizeMediaInput(req.body || {}, mediaInputConfig(process.env));
    const { key, bucket } = await putMedia(req.principal.userId, media.buffer, media.mime);
    res.status(201).json({ key, bucket, url: await signedGetUrl(key), bytes: media.buffer.length });
  } catch (e) {
    if (e.status) return res.status(e.status).json({ error: e.message || "media rejected" });
    req.log?.error?.({ err: e.message }, "media upload failed");
    res.status(500).json({ error: "upload failed" });
  }
});
