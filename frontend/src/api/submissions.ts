import http from "./https";
import type { SubmissionDTO, CreateSubmissionInput, SubmissionStatus } from "../types/submission";

export type PageResp<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
};

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

function extractError(e: any): string {
  return (
    e?.response?.data?.message ||
    e?.response?.data?.error ||
    e?.message ||
    "Request failed."
  );
}

async function uploadViaProofService(file: File): Promise<{ key: string; url?: string }> {
  const form = new FormData();
  form.append("file", file, file.name);
  const { data } = await http.post<{ key: string; url?: string }>("/uploads/direct", form, {
    headers: { /* let browser set multipart boundary */ },
  });
  if (!data?.key) throw new Error("Upload returned no key");
  return data;
}

export const SubmissionsApi = {
  async getProofUrl(id: string | number): Promise<string> {
    const { data } = await http.get<{ url: string }>(`/submissions/${id}/proof-url`);
    return data.url;
  },

  async list(status?: SubmissionStatus | "ALL"): Promise<SubmissionDTO[]> {
    const params = (status && status !== "ALL") ? { status } : undefined;
    const { data } = await http.get("/submissions", { params });
    return normalizeList<SubmissionDTO>(data);
  },

  async get(id: string | number): Promise<SubmissionDTO> {
    const { data } = await http.get<SubmissionDTO>(`/submissions/${id}`);
    return data;
  },

  async listByQuest(questId: string | number): Promise<SubmissionDTO[]> {
    const { data } = await http.get<SubmissionDTO[]>(`/submissions/quest/${questId}`);
    return normalizeList<SubmissionDTO>(data);
  },

  proofUrl(id: string | number) {
    return `/api/submissions/${id}/proof`;
  },

  async proofUrlJson(id: string | number): Promise<string> {
    const { data } = await http.get<{ url: string }>(`/submissions/${id}/proof-url`);
    return data.url;
  },

  async review(
    id: string | number,
    input:
      | { reviewStatus?: "APPROVED" | "REJECTED"; reviewNote?: string }
      | { status?: "APPROVED" | "REJECTED"; note?: string }
  ): Promise<SubmissionDTO> {
    const body =
      "status" in (input as any) || "note" in (input as any)
        ? input
        : { status: (input as any).reviewStatus, note: (input as any).reviewNote };
    const { data } = await http.post<SubmissionDTO>(`/submissions/${id}/review`, body);
    return data;
  },

  async create(input: CreateSubmissionInput): Promise<SubmissionDTO> {
    if (input.file) {
      const f = input.file as File;
      if (!ALLOWED.has(f.type)) {
        throw new Error("Only images (jpeg/png/webp/gif) or videos (mp4/mov/webm) are allowed.");
      }
      if (f.size > MAX_BYTES) throw new Error("File exceeds 100MB limit.");

      try {
        const form = new FormData();
        form.append("questId", String(input.questId));
        if (input.comment) form.append("comment", input.comment);
        form.append("file", f, f.name);

        const { data } = await http.post<SubmissionDTO>("/submissions", form, {
          headers: { /* let browser set multipart boundary */ },
        });
        return data;
      } catch (e: any) {
        const status = e?.response?.status as number | undefined;

        if (status === 415 || status === 404) {
          try {
            const up = await uploadViaProofService(f);
            const { data } = await http.post<SubmissionDTO>("/submissions", {
              questId: input.questId,
              note: input.comment ?? undefined,
              proofKey: up.key,
            });
            return data;
          } catch (e2: any) {
            throw new Error(extractError(e2));
          }
        }

        if (status === 413) throw new Error("File too large. Max allowed is 100MB.");
        if (status === 400) throw new Error(extractError(e));
        throw new Error(extractError(e));
      }
    }

    const body: any = {
      questId: input.questId,
      note: input.comment,
      proofUrl: input.proofUrl,
      proofKey: (input as any).proofKey,
    };

    try {
      const { data } = await http.post<SubmissionDTO>("/submissions", body);
      return data;
    } catch (e: any) {
      throw new Error(extractError(e));
    }
  },
  async minePage(page = 0, size = 10): Promise<PageResp<SubmissionDTO>> {
    const { data } = await http.get("/submissions/mine", {
      params: { page, size, sort: "createdAt,desc" },
    });

    if (Array.isArray(data)) {
      return {
        content: data as SubmissionDTO[],
        totalElements: data.length,
        totalPages: 1,
        number: 0,
        size: data.length,
        first: true,
        last: true,
      };
    }

    return normalizePage<SubmissionDTO>(data);
  },

};

function normalizePage<T>(payload: any): PageResp<T> {
  const content = normalizeList<T>(payload);
  return {
    content,
    totalElements: Number(payload?.totalElements ?? content.length ?? 0),
    totalPages: Number(payload?.totalPages ?? 1),
    number: Number(payload?.number ?? 0),
    size: Number(payload?.size ?? content.length ?? 0),
    first: Boolean(payload?.first ?? true),
    last: Boolean(payload?.last ?? true),
  };
}
