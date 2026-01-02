import { setAccessTokenGetter } from "./token";

type Tokens = {
  access_token: string;
  refresh_token?: string;
  expires_in: number;
  token_type: "Bearer" | string;
  scope?: string;
};
type Stored = Tokens & { obtained_at: number };

// Env overrides (optional). If not provided, we fall back to same-origin.
const envIssuer = (import.meta.env.VITE_OIDC_ISSUER as string | undefined) ?? "";
const clientId  = (import.meta.env.VITE_OIDC_CLIENT_ID as string) ?? "";
const envAppUrl = (import.meta.env.VITE_APP_URL as string | undefined) ?? "";

// Compute a stable origin for redirects (prefer VITE_APP_URL if provided)
function appOrigin(): string {
  try {
    if (envAppUrl) return new URL(envAppUrl).origin;
  } catch {
    // ignore bad envAppUrl
  }
  return window.location.origin;
}

// Redirect URI must be registered in Keycloak (e.g. https://yourhost/oidc/callback)
function redirectUri(): string {
  return new URL("/oidc/callback", appOrigin()).toString();
}

// Issuer defaults to same-origin Keycloak behind nginx (/auth)
function issuerBase(): string {
  if (envIssuer) return envIssuer.replace(/\/+$/, "");
  return new URL("/auth/realms/questify", appOrigin()).toString().replace(/\/+$/, "");
}

function endpoints() {
  const base = issuerBase();
  return {
    authorize: `${base}/protocol/openid-connect/auth`,
    token:     `${base}/protocol/openid-connect/token`,
    logout:    `${base}/protocol/openid-connect/logout`,
  };
}

function randomString(len = 64) {
  const b = new Uint8Array(len);
  crypto.getRandomValues(b);
  return Array.from(b).map(x => ("0" + x.toString(16)).slice(-2)).join("");
}

function base64url(ab: ArrayBuffer) {
  return btoa(String.fromCharCode(...new Uint8Array(ab)))
    .replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function sha256(s: string) {
  const enc = new TextEncoder().encode(s);
  const buf = await crypto.subtle.digest("SHA-256", enc);
  return base64url(buf);
}

function storeTokens(tok: Tokens | null) {
  if (!tok) {
    sessionStorage.removeItem("oidc.tokens");
    return;
  }
  const withTs: Stored = { ...tok, obtained_at: Date.now() / 1000 };
  sessionStorage.setItem("oidc.tokens", JSON.stringify(withTs));
}

function loadTokens(): Stored | null {
  const raw = sessionStorage.getItem("oidc.tokens");
  if (!raw) return null;
  try { return JSON.parse(raw) as Stored; } catch { return null; }
}

function expSeconds(t: Stored) {
  return t.obtained_at + (t.expires_in ?? 300);
}

async function exchangeCode(code: string, verifier: string): Promise<Tokens> {
  const { token } = endpoints();
  const body = new URLSearchParams({
    grant_type: "authorization_code",
    client_id: clientId,
    code,
    redirect_uri: redirectUri(),
    code_verifier: verifier,
  });

  const res = await fetch(token, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body,
  });

  if (!res.ok) throw new Error(`Token exchange failed: ${res.status}`);
  return await res.json();
}

async function refresh(refresh_token: string): Promise<Tokens> {
  const { token } = endpoints();
  const body = new URLSearchParams({
    grant_type: "refresh_token",
    client_id: clientId,
    refresh_token,
  });

  const res = await fetch(token, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body,
  });

  if (!res.ok) throw new Error(`Refresh failed: ${res.status}`);
  return await res.json();
}

function scheduleRefresh() {
  const current = loadTokens();
  if (!current?.refresh_token) return;

  const now = Date.now() / 1000;
  const target = Math.max(5, expSeconds(current) - 60 - now); // refresh 60s before expiry

  setTimeout(async () => {
    try {
      const latest = loadTokens();
      if (!latest?.refresh_token) return;

      const next = await refresh(latest.refresh_token);
      storeTokens(next);
      setAccessTokenGetter(() => (loadTokens()?.access_token ?? null));
      scheduleRefresh();
    } catch {
      // fallback: full login
      beginLogin();
    }
  }, target * 1000);
}

export function beginLogin() {
  if (!clientId) throw new Error("VITE_OIDC_CLIENT_ID is missing");

  const { authorize } = endpoints();
  const state = randomString(16);
  const verifier = randomString(64);

  sessionStorage.setItem("pkce.state", state);
  sessionStorage.setItem("pkce.verifier", verifier);

  sha256(verifier).then(challenge => {
    const url = new URL(authorize);
    url.searchParams.set("response_type", "code");
    url.searchParams.set("client_id", clientId);
    url.searchParams.set("redirect_uri", redirectUri());
    url.searchParams.set("scope", "openid profile email");
    url.searchParams.set("state", state);
    url.searchParams.set("code_challenge_method", "S256");
    url.searchParams.set("code_challenge", challenge);
    window.location.replace(url.toString());
  });
}

export function logout() {
  const { logout: logoutEndpoint } = endpoints();
  const url = new URL(logoutEndpoint);

  // Keycloak supports post_logout_redirect_uri (must be allowed in client settings)
  url.searchParams.set("post_logout_redirect_uri", appOrigin());
  url.searchParams.set("client_id", clientId);

  storeTokens(null);
  window.location.replace(url.toString());
}

export async function initOidc(): Promise<void> {
  const params = new URLSearchParams(window.location.search);

  // Handle authorization code callback
  if (params.has("code")) {
    const code = params.get("code")!;
    const retState = params.get("state");
    const expectState = sessionStorage.getItem("pkce.state");
    const verifier = sessionStorage.getItem("pkce.verifier");

    sessionStorage.removeItem("pkce.state");
    sessionStorage.removeItem("pkce.verifier");

    if (!verifier || !retState || !expectState || retState !== expectState) {
      throw new Error("OIDC state/verifier mismatch");
    }

    const tokens = await exchangeCode(code, verifier);
    storeTokens(tokens);

    // Clean URL (remove code/state params)
    const clean = new URL(window.location.href);
    clean.search = "";
    window.history.replaceState({}, "", clean.toString());
  }

  const t = loadTokens();
  if (!t?.access_token) {
    beginLogin();
    return;
  }

  setAccessTokenGetter(() => (loadTokens()?.access_token ?? null));
  scheduleRefresh();
}
