// ============================================================
//  DEPRECATED LOCATION
//  The gateway now lives in ./gateway/gateway.mjs (hardened:
//  CORS allowlist, bearer-token auth, rate limiting, .env support).
//
//  Run it from there:
//    cd gateway && npm install && cp .env.example .env && npm start
//
//  This file only forwards to the new one so old commands keep working.
//  It intentionally contains no server logic of its own.
// ============================================================
import "./gateway/gateway.mjs";
