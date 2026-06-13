// S3-compatible object storage for media (works with AWS S3 or MinIO).
// Lets clients upload images/audio once and reference them, instead of
// shipping 25 MB base64 data URLs through every chat request.
import { S3Client, PutObjectCommand, GetObjectCommand, CreateBucketCommand } from "@aws-sdk/client-s3";
import { getSignedUrl } from "@aws-sdk/s3-request-presigner";
import { randomUUID } from "node:crypto";

export const BUCKET = process.env.S3_BUCKET || "nova-media";

export const s3 = new S3Client({
  region: process.env.S3_REGION || "us-east-1",
  endpoint: process.env.S3_ENDPOINT || undefined,          // MinIO: http://minio:9000
  forcePathStyle: process.env.S3_FORCE_PATH_STYLE === "1", // MinIO needs path-style
  credentials: process.env.S3_ACCESS_KEY ? {
    accessKeyId: process.env.S3_ACCESS_KEY,
    secretAccessKey: process.env.S3_SECRET_KEY || "",
  } : undefined,
});

// Pure: deterministic, collision-resistant object key scoped to the user.
export function mediaKey(userId, mime) {
  const ext = (String(mime).split("/")[1] || "bin").replace(/[^a-z0-9]/gi, "").slice(0, 8) || "bin";
  return `u/${userId}/${randomUUID()}.${ext}`;
}

// Create the bucket once, lazily, on first upload (idempotent — MinIO/S3
// return BucketAlready* when it exists). Without this a fresh stack 500s on
// the first /v1/media call until someone creates the bucket by hand.
let bucketReady;
function ensureBucket() {
  if (!bucketReady) {
    bucketReady = s3.send(new CreateBucketCommand({ Bucket: BUCKET }))
      .catch((e) => {
        if (/BucketAlready/i.test(e.name || "")) return;     // exists → fine
        bucketReady = undefined;                              // retry next call
        throw e;
      });
  }
  return bucketReady;
}

export async function putMedia(userId, buffer, mime) {
  await ensureBucket();
  const key = mediaKey(userId, mime);
  await s3.send(new PutObjectCommand({ Bucket: BUCKET, Key: key, Body: buffer, ContentType: mime }));
  return { key, bucket: BUCKET };
}

export function signedGetUrl(key, ttlSeconds = 3600) {
  return getSignedUrl(s3, new GetObjectCommand({ Bucket: BUCKET, Key: key }), { expiresIn: ttlSeconds });
}
