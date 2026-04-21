import http from "./https";
import type { QuestCategory } from "../types/quest";

export type CoachSuggestionMode = "DEFAULT";
export type CoachSuggestionStatus = "SUCCESS" | "FALLBACK";
export type CoachSuggestionSource = "AI" | "SYSTEM";
export type CoachDifficulty = "easy" | "medium" | "hard";

export interface CoachSuggestionsRequest {
  mode?: CoachSuggestionMode;
  includeRecentHistory?: boolean;
  excludedSuggestionTitles?: string[];
}

export interface CoachSuggestion {
  title: string;
  description: string;
  category: QuestCategory;
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

const QUEST_CATEGORIES = new Set<QuestCategory>([
  "COMMUNITY",
  "FITNESS",
  "HABIT",
  "HOBBY",
  "OTHER",
  "STUDY",
  "WORK",
]);

function normalizeQuestCategory(value: unknown): QuestCategory {
  if (typeof value === "string" && QUEST_CATEGORIES.has(value as QuestCategory)) {
    return value as QuestCategory;
  }
  return "OTHER";
}

function normalizeSuggestions(value: unknown): CoachSuggestion[] {
  if (!Array.isArray(value)) return [];
  return value.map((item) => {
    const raw = typeof item === "object" && item !== null ? (item as Record<string, unknown>) : {};
    return {
      title: String(raw.title ?? ""),
      description: String(raw.description ?? ""),
      category: normalizeQuestCategory(raw.category),
      estimatedMinutes: Number(raw.estimatedMinutes ?? 0),
      difficulty: (raw.difficulty === "easy" || raw.difficulty === "medium" || raw.difficulty === "hard"
        ? raw.difficulty
        : "easy") as CoachDifficulty,
      reason: String(raw.reason ?? ""),
    };
  });
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
      suggestions: normalizeSuggestions(data.suggestions),
      reflection: String(data.reflection ?? ""),
      nudge: String(data.nudge ?? ""),
    };
  },
};
