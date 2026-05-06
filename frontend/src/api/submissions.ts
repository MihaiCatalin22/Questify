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

export type SubmissionSummaryRes = {
  submissionsTotal: number;
};

const ALLOWED = new Set<string>([
  "image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif",
  "video/mp4", "video/quicktime", "video/webm",
]);

const MAX_BYTES = 100 * 1024 * 1024; // 100 MB

type ApiErrorLike = {
  response?: {
    status?: number;
    data?: {
      message?: string;
      error?: string;
    };
  };
  message?: string;
};

type SubmissionCreateBody = {
  questId: string;
  note?: string;
  proofKey?: string;
  proofKeys?: string[];
  proofUrl?: string;
  proofUrls?: string[];
};

type LegacyReviewInput = { reviewStatus?: "APPROVED" | "REJECTED"; reviewNote?: string };
type ReviewInput = LegacyReviewInput | { status?: "APPROVED" | "REJECTED"; note?: string };

function asRecord(value: unknown): Record<string, unknown> | null {
  return typeof value === "object" && value !== null ? (value as Record<string, unknown>) : null;
}

function normalizeList<T>(payload: unknown): T[] {
  if (Array.isArray(payload)) return payload;
  const record = asRecord(payload);
  if (!record) return [];
  const c = [record.content, record.items, record.data, record.results, record.list];
  const found = c.find(Array.isArray);
  return found ?? [];
}

function toApiError(e: unknown): ApiErrorLike {
  return typeof e === "object" && e !== null ? (e as ApiErrorLike) : {};
}

function getStatus(e: unknown): number | undefined {
  return toApiError(e).response?.status;
}

function extractError(e: unknown): string {
  const apiError = toApiError(e);
  return (
    apiError.response?.data?.message ||
    apiError.response?.data?.error ||
    apiError.message ||
    "Request failed."
  );
}

function isDirectReviewInput(input: ReviewInput): input is { status?: "APPROVED" | "REJECTED"; note?: string } {
  return "status" in input || "note" in input;
}

function validateFile(f: File) {
  if (!ALLOWED.has(f.type)) {
    throw new Error("Only images (jpeg/png/webp/gif) or videos (mp4/mov/webm) are allowed.");
  }
  if (f.size > MAX_BYTES) throw new Error("File exceeds 100MB limit.");
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
    input: ReviewInput
  ): Promise<SubmissionDTO> {
    const body = isDirectReviewInput(input)
      ? input
      : { status: input.reviewStatus, note: input.reviewNote };

    const { data } = await http.post<SubmissionDTO>(`/submissions/${id}/review`, body);
    return data;
  },

  async create(input: CreateSubmissionInput): Promise<SubmissionDTO> {
    const many = input.files?.filter(Boolean) ?? [];

    // ---- multi-file (ONE submission) --------------------------------------
    if (many.length > 0) {
      many.forEach(validateFile);

      // Prefer multipart /submissions with files[]
      try {
        const form = new FormData();
        form.append("questId", String(input.questId));
        if (input.comment) form.append("comment", input.comment);

        for (const f of many) {
          // backend expects @RequestParam("files") List<MultipartFile>
          form.append("files", f, f.name);
        }

        const { data } = await http.post<SubmissionDTO>("/submissions", form, {
          headers: { /* let browser set multipart boundary */ },
        });
        return data;
      } catch (e: unknown) {
        const status = getStatus(e);

        // Fallback: upload each file to proof-service, then create JSON submission with proofKeys[]
        if (status === 415 || status === 404) {
          try {
            const uploads: { key: string; url?: string }[] = [];
            for (const f of many) {
              uploads.push(await uploadViaProofService(f));
            }
            const proofKeys = uploads.map(u => u.key);

            const { data } = await http.post<SubmissionDTO>("/submissions", {
              questId: input.questId,
              note: input.comment ?? undefined,
              proofKeys,
            });

            return data;
          } catch (e2: unknown) {
            throw new Error(extractError(e2));
          }
        }

        if (status === 413) throw new Error("Request too large. Reduce file sizes or count.");
        throw new Error(extractError(e));
      }
    }

    // ---- single-file (legacy) --------------------------------------------
    if (input.file) {
      const f = input.file;
      validateFile(f);

      try {
        const form = new FormData();
        form.append("questId", String(input.questId));
        if (input.comment) form.append("comment", input.comment);
        form.append("file", f, f.name);

        const { data } = await http.post<SubmissionDTO>("/submissions", form, {
          headers: { /* let browser set multipart boundary */ },
        });
        return data;
      } catch (e: unknown) {
        const status = getStatus(e);

        if (status === 415 || status === 404) {
          try {
            const up = await uploadViaProofService(f);
            const { data } = await http.post<SubmissionDTO>("/submissions", {
              questId: input.questId,
              note: input.comment ?? undefined,
              proofKey: up.key,
            });
            return data;
          } catch (e2: unknown) {
            throw new Error(extractError(e2));
          }
        }

        if (status === 413) throw new Error("File too large. Max allowed is 100MB.");
        throw new Error(extractError(e));
      }
    }

    // ---- JSON (keys/urls) ------------------------------------------------
    const body: SubmissionCreateBody = {
      questId: input.questId,
      note: input.comment,
    };

    if (input.proofKey) body.proofKey = input.proofKey;
    if (input.proofKeys?.length) body.proofKeys = input.proofKeys;

    if (input.proofUrl) body.proofUrl = input.proofUrl;
    if (input.proofUrls?.length) body.proofUrls = input.proofUrls;

    try {
      const { data } = await http.post<SubmissionDTO>("/submissions", body);
      return data;
    } catch (e: unknown) {
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

  async mineSummary(): Promise<SubmissionSummaryRes> {
    const { data } = await http.get<SubmissionSummaryRes>("/submissions/mine/summary");
    return data;
  },
};

function normalizePage<T>(payload: unknown): PageResp<T> {
  const content = normalizeList<T>(payload);
  const record = asRecord(payload) ?? {};
  return {
    content,
    totalElements: Number(record.totalElements ?? content.length ?? 0),
    totalPages: Number(record.totalPages ?? 1),
    number: Number(record.number ?? 0),
    size: Number(record.size ?? content.length ?? 0),
    first: Boolean(record.first ?? true),
    last: Boolean(record.last ?? true),
  };
}
