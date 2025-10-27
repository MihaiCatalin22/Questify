import http from "./https";
import type { SubmissionDTO, CreateSubmissionInput } from "../types/submission";

const ALLOWED = new Set<string>([
  "image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif",
  "video/mp4", "video/quicktime", "video/webm",
]);
const MAX_BYTES = 100 * 1024 * 1024; // 100 MB

function normalizeList<T>(payload: any): T[] {
  if (Array.isArray(payload)) return payload;
  if (!payload || typeof payload !== "object") return [];
  const c = [payload.content, payload.items, payload.data, payload.results, payload.list];
  const found = c.find(Array.isArray);
  return found ?? [];
}

function mapUploadError(status?: number, bodyText?: string, json?: any): never {
  const msg = json?.message || json?.error || bodyText || "Failed to create submission.";
  if (status === 413) throw new Error("File too large. Max allowed is 100MB.");
  if (status === 415) throw new Error("Unsupported media type. Only images (jpeg/png/webp/gif) or videos (mp4/mov/webm) are allowed.");
  if (status === 400) throw new Error(msg || "Invalid submission data.");
  throw new Error(msg);
}

function getJwt(): string | null {
  const t = sessionStorage.getItem("accessToken");
  if (t) return t;
  const raw = sessionStorage.getItem("questify.auth") ?? localStorage.getItem("questify.auth");
  if (raw) { try { const o = JSON.parse(raw); if (o?.jwt) return o.jwt as string; } catch {} }
  const u = sessionStorage.getItem("user");
  if (u) { try { const o = JSON.parse(u); if (o?.jwt) return o.jwt as string; } catch {} }
  return null;
}

export const SubmissionsApi = {
  async list(): Promise<SubmissionDTO[]> {
    const { data } = await http.get<SubmissionDTO[]>("/submissions");
    return normalizeList<SubmissionDTO>(data);
  },

  async get(id: string): Promise<SubmissionDTO> {
    const { data } = await http.get<SubmissionDTO>(`/submissions/${id}`);
    return data;
  },

  async listByQuest(questId: string): Promise<SubmissionDTO[]> {
    const { data } = await http.get<SubmissionDTO[]>(`/quests/${questId}/submissions`);
    return normalizeList<SubmissionDTO>(data);
  },

  proofUrl(id: string | number) {
    return `/api/submissions/${id}/proof`;
  },

  async review(
    id: string | number,
    input: { reviewStatus: "APPROVED" | "REJECTED"; reviewNote?: string }
  ): Promise<SubmissionDTO> {
    const { data } = await http.post<SubmissionDTO>(`/submissions/${id}/review`, input);
    return data;
  },

  async create(input: CreateSubmissionInput): Promise<SubmissionDTO> {
    if (input.file) {
      const f = input.file as File;
      if (!ALLOWED.has(f.type)) throw new Error("Only images (jpeg/png/webp/gif) or videos (mp4/mov/webm) are allowed.");
      if (f.size > MAX_BYTES) throw new Error("File exceeds 100MB limit.");

      const form = new FormData();
      form.append("questId", input.questId);
      if (input.comment) form.append("comment", input.comment);
      form.append("file", f, f.name);

      const headers: Record<string, string> = {};
      const jwt = getJwt();
      if (jwt) headers["Authorization"] = `Bearer ${jwt}`;

      const res = await fetch("/api/submissions", { method: "POST", body: form, headers });
      const ct = res.headers.get("content-type") || "";
      if (!res.ok) {
        if (ct.includes("application/json")) { mapUploadError(res.status, undefined, await res.json().catch(() => ({}))); }
        else { mapUploadError(res.status, await res.text().catch(() => "")); }
      }
      if (ct.includes("application/json")) return (await res.json()) as SubmissionDTO;
      throw new Error("Upload succeeded but server returned an unexpected response.");
    }

    const { data } = await http.post<SubmissionDTO>("/submissions", {
      questId: input.questId,
      comment: input.comment,
      proofUrl: input.proofUrl,
    });
    return data;
  },
};
