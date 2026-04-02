import http from "./https";

export type CoachSuggestionMode = "DEFAULT";
export type CoachSuggestionStatus = "SUCCESS" | "FALLBACK";
export type CoachSuggestionSource = "AI" | "SYSTEM";
export type CoachDifficulty = "easy" | "medium" | "hard";

export interface CoachSuggestionsRequest {
  mode?: CoachSuggestionMode;
  includeRecentHistory?: boolean;
}

export interface CoachSuggestion {
  title: string;
  estimatedMinutes: number;
  difficulty: CoachDifficulty;
  reason: string;
}

export interface CoachSuggestionsResponse {
  status: CoachSuggestionStatus;
  source: CoachSuggestionSource;
  model?: string | null;
  generatedAt: string;
  suggestions: CoachSuggestion[];
  reflection: string;
  nudge: string;
}

export const CoachApi = {
  async generateSuggestions(input: CoachSuggestionsRequest): Promise<CoachSuggestionsResponse> {
    const { data } = await http.post<CoachSuggestionsResponse>("/coach/suggestions", input, {
      headers: { "Cache-Control": "no-store" },
    });
    return {
      status: data.status,
      source: data.source,
      model: data.model ?? null,
      generatedAt: String(data.generatedAt ?? ""),
      suggestions: Array.isArray(data.suggestions) ? data.suggestions : [],
      reflection: String(data.reflection ?? ""),
      nudge: String(data.nudge ?? ""),
    };
  },
};
