import http from "./https";

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
  modelName?: string | null;
  rawStatus?: string | null;
  reviewedAt?: string | null;
};

export const AiReviewsApi = {
  async getForSubmission(submissionId: string | number): Promise<AiReviewDTO> {
    const { data } = await http.get<AiReviewDTO>(`/ai-reviews/submissions/${submissionId}`);
    return data;
  },

  async runForSubmission(submissionId: string | number): Promise<AiReviewDTO> {
    const { data } = await http.post<AiReviewDTO>(`/ai-reviews/submissions/${submissionId}/run`);
    return data;
  },
};
