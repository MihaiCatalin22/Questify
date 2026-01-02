import axios from "axios";
import { getAccessToken } from "./token";

const baseURL =
  (import.meta.env && (import.meta.env as any).VITE_API_BASE) || "/api";

const http = axios.create({
  baseURL: baseURL.replace(/\/$/, ""),
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
  window.dispatchEvent(new CustomEvent(FORCE_LOGOUT_EVENT, { detail: { reason } }));
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
