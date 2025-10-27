import axios, { AxiosHeaders } from "axios";
import { jwtDecode } from "jwt-decode";

export const STORAGE_KEY = "questify.auth";

export type JwtClaims = {
  sub?: string;
  exp?: number;
  iat?: number;
  roles?: string[];
  uid?: number | string;
  [k: string]: unknown;
};

export type JwtAuth = {
  jwt: string;
  user?:
    | {
        id?: string | number;
        username?: string;
        email?: string;
        displayName?: string;
        roles?: string[];
      }
    | null;
  expiresAt?: string | number | null;
};

const API_BASE_URL = "/api";

const ax = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: false,
  timeout: 20000,
});

const Token = {
  get(): string | null {
    const t = sessionStorage.getItem("accessToken");
    if (t) return t;

    const raw = sessionStorage.getItem(STORAGE_KEY) ?? localStorage.getItem(STORAGE_KEY);
    if (raw) {
      try {
        const obj = JSON.parse(raw) as JwtAuth;
        if (obj?.jwt) return obj.jwt;
      } catch {}
    }

    const u = sessionStorage.getItem("user");
    if (u) {
      try {
        const obj = JSON.parse(u) as { jwt?: string };
        if (obj?.jwt) return obj.jwt;
      } catch {}
    }
    return null;
  },

  set(jwt: string) {
    sessionStorage.setItem("accessToken", jwt);
    try {
      const claims = jwtDecode<JwtClaims>(jwt);
      sessionStorage.setItem("claims", JSON.stringify(claims));
    } catch {
      sessionStorage.removeItem("claims");
    }
  },

  clear() {
    sessionStorage.removeItem("accessToken");
    sessionStorage.removeItem("claims");
  },
};

export function setAuthHeader(jwt?: string) {
  if (!(ax.defaults.headers as any).common || !((ax.defaults.headers as any).common instanceof AxiosHeaders)) {
    (ax.defaults.headers as any).common = new AxiosHeaders((ax.defaults.headers as any).common);
  }
  const h: AxiosHeaders = (ax.defaults.headers as any).common;
  if (jwt) h.set("Authorization", `Bearer ${jwt}`);
  else if (h.has("Authorization")) h.delete("Authorization");
}

function persistAuth(res: JwtAuth): void {
  const payload = JSON.stringify(res);
  sessionStorage.setItem(STORAGE_KEY, payload);
  localStorage.setItem(STORAGE_KEY, payload);

  sessionStorage.setItem("user", JSON.stringify(res));
  Token.set(res.jwt);

  setAuthHeader(res.jwt);
}

function parseAuth(raw: string | null): JwtAuth | null {
  if (!raw) return null;
  try {
    const obj = JSON.parse(raw) as JwtAuth;
    return obj?.jwt ? obj : null;
  } catch {
    return null;
  }
}

function loadAuth(): JwtAuth | null {
  const ss = parseAuth(sessionStorage.getItem(STORAGE_KEY));
  if (ss?.jwt) {
    setAuthHeader(ss.jwt);
    sessionStorage.setItem("user", JSON.stringify(ss));
    Token.set(ss.jwt);
    return ss;
  }
  const ls = parseAuth(localStorage.getItem(STORAGE_KEY));
  if (ls?.jwt) {
    setAuthHeader(ls.jwt);
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(ls));
    sessionStorage.setItem("user", JSON.stringify(ls));
    Token.set(ls.jwt);
    return ls;
  }

  const u = parseAuth(sessionStorage.getItem("user"));
  if (u?.jwt) {
    setAuthHeader(u.jwt);
    Token.set(u.jwt);
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(u));
    return u;
  }
  return null;
}

function clearAuth(): void {
  sessionStorage.removeItem(STORAGE_KEY);
  localStorage.removeItem(STORAGE_KEY);
  sessionStorage.removeItem("user");
  Token.clear();
  setAuthHeader(undefined);
}

export const AuthStorage = { STORAGE_KEY, persistAuth, loadAuth, clearAuth, setAuthHeader };

(() => {
  const jwt = Token.get();
  if (jwt) {
    setAuthHeader(jwt);
    try {
      const claims = jwtDecode<JwtClaims>(jwt);
      sessionStorage.setItem("claims", JSON.stringify(claims));
    } catch {
      sessionStorage.removeItem("claims");
    }
    return;
  }
  const curr = loadAuth();
  if (curr?.jwt) {
    return;
  }
})();

if (!(ax as any).__questifyInterceptorsInstalled) {
  ax.interceptors.request.use((cfg) => {
    if (!(cfg.headers instanceof AxiosHeaders)) {
      cfg.headers = new AxiosHeaders(cfg.headers);
    }
    const h = cfg.headers as AxiosHeaders;
    if (!h.has("Accept")) h.set("Accept", "application/json");
    if (!h.has("Authorization")) {
      const jwt = Token.get();
      if (jwt) h.set("Authorization", `Bearer ${jwt}`);
    }
    return cfg;
  });

  ax.interceptors.response.use(
    (r) => r,
    (err) => {
      if (err?.response?.status === 401) {
        clearAuth();
      }
      return Promise.reject(err);
    }
  );

  (ax as any).__questifyInterceptorsInstalled = true;
}

export { ax as http };
export default ax;
