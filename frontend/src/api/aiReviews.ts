import http from "./https";
import type { AxiosError } from "axios";

export type AiReviewRecommendation =
  | "LIKELY_VALID"
  | "UNCLEAR"
  | "LIKELY_INVALID"
  | "UNSUPPORTED_MEDIA"
  | "AI_FAILED";

export type AiReviewDTO = {
  submissionId: number;
  questId: number;
  userId: string;
  recommendation: AiReviewRecommendation;
  confidence: number;
  reasons: string[];
  decisionNote?: string | null;
  matchedEvidence?: string[];
  missingEvidence?: string[];
  matchedDisqualifiers?: string[];
  ocrSnippets?: string[];
  observedSignals?: string[];
  decisionPath?: string | null;
  generatedPolicy?: boolean;
  modelUsed?: string | null;
  fallbackUsed?: boolean;
  fallbackReason?: string | null;
  modelName?: string | null;
  rawStatus?: string | null;
  reviewedAt?: string | null;
};

type ApiErrorPayload = {
  message?: string;
  error?: string;
};

type ApiError = AxiosError<ApiErrorPayload>;

function logAiReviewError(action: string, submissionId: string | number, error: unknown) {
  const apiError = error as ApiError;
  const status = apiError?.response?.status;
  const payload = apiError?.response?.data;
  console.error(`[ai-review] ${action} failed`, {
    submissionId,
    status,
    payload,
    message: apiError?.message,
  });
}

export const AiReviewsApi = {
  async getForSubmission(submissionId: string | number): Promise<AiReviewDTO> {
    console.info("[ai-review] get request", { submissionId, endpoint: `/ai-reviews/submissions/${submissionId}` });
    try {
      const { data } = await http.get<AiReviewDTO>(`/ai-reviews/submissions/${submissionId}`);
      console.info("[ai-review] get response", { submissionId, data });
      return data;
    } catch (error: unknown) {
      logAiReviewError("get", submissionId, error);
      throw error;
    }
  },

  async runForSubmission(submissionId: string | number): Promise<AiReviewDTO> {
    console.info("[ai-review] run request", { submissionId, endpoint: `/ai-reviews/submissions/${submissionId}/run` });
    try {
      const { data } = await http.post<AiReviewDTO>(`/ai-reviews/submissions/${submissionId}/run`);
      console.info("[ai-review] run response", { submissionId, data });
      return data;
    } catch (error: unknown) {
      logAiReviewError("run", submissionId, error);
      throw error;
    }
  },
};
