// Keycloak (OIDC) + PKCE. Oturum açıldığında gateway çağrıları JWT kullanır;
// tarayıcıya kalıcı sağlayıcı anahtarı yapıştırmak gerekmez.

export const OIDC = { issuer: "http://localhost:8081/realms/nova", clientId: "nova-web" };

const b64url = (buf) =>
  btoa(String.fromCharCode(...new Uint8Array(buf)))
    .replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

export async function pkcePair() {
  const verifier = b64url(crypto.getRandomValues(new Uint8Array(32)));
  const challenge = b64url(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier)));
  return { verifier, challenge };
}

export function jwtClaim(tok, k) {
  try {
    return JSON.parse(atob(tok.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")))[k] || "";
  } catch (e) {
    return "";
  }
}

export async function tokenRequest(params) {
  const r = await fetch(OIDC.issuer + "/protocol/openid-connect/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: params,
  });
  if (!r.ok) throw new Error("token " + r.status);
  return r.json();
}

/** Token yanıtını uygulama oturumuna çevirir (süre payı: 30 sn). */
export function sessionFromTokens(t) {
  return {
    access_token: t.access_token,
    refresh_token: t.refresh_token,
    expires_at: Date.now() + Math.max(60, (t.expires_in || 300) - 30) * 1000,
    email: jwtClaim(t.access_token, "email"),
  };
}

export function authorizeUrl({ challenge, state }) {
  const u = new URL(OIDC.issuer + "/protocol/openid-connect/auth");
  u.searchParams.set("client_id", OIDC.clientId);
  u.searchParams.set("response_type", "code");
  u.searchParams.set("redirect_uri", location.origin + "/");
  u.searchParams.set("scope", "openid profile email");
  u.searchParams.set("state", state);
  u.searchParams.set("code_challenge", challenge);
  u.searchParams.set("code_challenge_method", "S256");
  return u.toString();
}

export const exchangeCode = (code, verifier) =>
  tokenRequest(new URLSearchParams({
    grant_type: "authorization_code",
    client_id: OIDC.clientId,
    code,
    redirect_uri: location.origin + "/",
    code_verifier: verifier,
  }));

export const refresh = (refreshToken) =>
  tokenRequest(new URLSearchParams({
    grant_type: "refresh_token",
    client_id: OIDC.clientId,
    refresh_token: refreshToken,
  }));
