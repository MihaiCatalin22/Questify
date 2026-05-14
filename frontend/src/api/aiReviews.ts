import http from "./https";

export type AiReviewRecommendation =
  | "LIKELY_VALID"
  | "UNCLEAR"
  | "LIKELY_INVALID"
  | "UNSUPPORTED_MEDIA"
  | "AI_FAILED";

export type AiReviewRunStatus = "PENDING" | "RUNNING" | "COMPLETED" | "FAILED";

export type AiReviewDTO = {
  submissionId: number;
  questId: number;
  userId: string;
  status: AiReviewRunStatus;
  recommendation: AiReviewRecommendation;
  confidence: number;
  supportScore: number;
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

export type AiReviewRunAcceptedDTO = {
  submissionId: number;
  status: AiReviewRunStatus;
  resultEndpoint: string;
};

export type AiReviewStatusDTO = {
  submissionId: number;
  status: AiReviewRunStatus;
  reviewedAt?: string | null;
};

export const AiReviewsApi = {
  async getForSubmission(submissionId: string | number): Promise<AiReviewDTO> {
    const { data } = await http.get<AiReviewDTO>(`/ai-reviews/submissions/${submissionId}`);
    return data;
  },

  async runForSubmission(submissionId: string | number): Promise<AiReviewRunAcceptedDTO> {
    const { data } = await http.post<AiReviewRunAcceptedDTO>(`/ai-reviews/submissions/${submissionId}/run`);
    return data;
  },

  async getStatus(submissionId: string | number): Promise<AiReviewStatusDTO> {
    const { data } = await http.get<AiReviewStatusDTO>(`/ai-reviews/submissions/${submissionId}/status`);
    return data;
  },
};
