import axios from "axios";
import { getAccessToken } from "./token";

function normalizeApiBase(raw?: string): string {
  const v = (raw ?? "").trim();
  if (!v) return "/api";

  if (v.startsWith("/")) return v.replace(/\/$/, "");

  const hasLocation =
    typeof window !== "undefined" &&
    typeof window.location !== "undefined" &&
    typeof window.location.origin === "string";

  try {
    const baseOrigin = hasLocation ? window.location.origin : "http://localhost";
    const pageProto = hasLocation ? window.location.protocol : "https:";
    const pageHost = hasLocation ? window.location.hostname : "";
    const pagePort = hasLocation ? window.location.port : ""; 

    const u = new URL(v, baseOrigin);

    if (hasLocation && u.hostname === pageHost) {
      if (u.protocol === "http:") u.protocol = pageProto;

      if (u.port === "8080") u.port = pagePort;

      if (u.protocol === "https:" && u.port === "443") u.port = "";
      if (u.protocol === "http:" && u.port === "80") u.port = "";
    }

    return u.toString().replace(/\/$/, "");
  } catch {
    return "/api";
  }
}

const baseURL = normalizeApiBase(
  (import.meta.env && (import.meta.env as any).VITE_API_BASE) || "/api"
);

const http = axios.create({
  baseURL,
  withCredentials: false,
});

http.interceptors.request.use((config) => {
  const t = getAccessToken();
  if (t) {
    config.headers = config.headers ?? {};
    (config.headers as any).Authorization = `Bearer ${t}`;
  }
  return config;
});

const FORCE_LOGOUT_EVENT = "questify:force-logout";
let forceLogoutEmitted = false;

function emitForceLogout(reason: "deleted" | "unauthorized") {
  if (forceLogoutEmitted) return;
  forceLogoutEmitted = true;
  window.dispatchEvent(
    new CustomEvent(FORCE_LOGOUT_EVENT, { detail: { reason } })
  );
}

http.interceptors.response.use(
  (res) => res,
  (err) => {
    const status = err?.response?.status as number | undefined;

    if (status === 410) emitForceLogout("deleted");
    if (status === 401) emitForceLogout("unauthorized");

    return Promise.reject(err);
  }
);

export default http;
