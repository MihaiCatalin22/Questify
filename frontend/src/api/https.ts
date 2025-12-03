// src/lib/https.ts
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

export default http;
