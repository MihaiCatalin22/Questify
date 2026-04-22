import type { CoachSuggestion, CoachSuggestionsResponse } from "../../api/coach";
import type { QuestCategory } from "../../types/quest";

export type AcceptedQuestState = {
  questId: number;
  title: string;
};

export type SuggestionDraft = {
  suggestionKey: string;
  title: string;
  description: string;
  category: QuestCategory;
  difficulty: CoachSuggestion["difficulty"];
  estimatedMinutes: number;
  reason: string;
};

type CoachSessionState = {
  response: CoachSuggestionsResponse | null;
  acceptedQuests: Record<string, AcceptedQuestState>;
  shownTitlesByGoal: Record<string, string[]>;
};

const STORAGE_KEY = "questify:coach:session:v2";

const DEFAULT_SESSION_STATE: CoachSessionState = {
  response: null,
  acceptedQuests: {},
  shownTitlesByGoal: {},
};

function hasSessionStorage() {
  return typeof window !== "undefined" && typeof window.sessionStorage !== "undefined";
}

function readStoredState(): CoachSessionState {
  if (!hasSessionStorage()) return DEFAULT_SESSION_STATE;

  const raw = window.sessionStorage.getItem(STORAGE_KEY);
  if (!raw) return DEFAULT_SESSION_STATE;

  try {
    const parsed = JSON.parse(raw) as Partial<CoachSessionState> | null;
    return {
      response: parsed?.response ?? null,
      acceptedQuests: parsed?.acceptedQuests ?? {},
      shownTitlesByGoal: parsed?.shownTitlesByGoal ?? {},
    };
  } catch {
    return DEFAULT_SESSION_STATE;
  }
}

function writeStoredState(nextState: CoachSessionState) {
  if (!hasSessionStorage()) return;
  window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(nextState));
}

function updateStoredState(updater: (current: CoachSessionState) => CoachSessionState) {
  writeStoredState(updater(readStoredState()));
}

export function readCoachSessionState() {
  return readStoredState();
}

export function setStoredCoachResponse(response: CoachSuggestionsResponse | null) {
  updateStoredState((current) => ({
    ...current,
    response,
  }));
}

export function getStoredCoachResponse() {
  return readStoredState().response;
}

export function setStoredAcceptedQuests(acceptedQuests: Record<string, AcceptedQuestState>) {
  updateStoredState((current) => ({
    ...current,
    acceptedQuests,
  }));
}

export function getStoredAcceptedQuests() {
  return readStoredState().acceptedQuests;
}

export function rememberAcceptedQuest(suggestionKey: string, acceptedQuest: AcceptedQuestState) {
  updateStoredState((current) => ({
    ...current,
    acceptedQuests: {
      ...current.acceptedQuests,
      [suggestionKey]: acceptedQuest,
    },
  }));
}

export function normalizeGoalKey(goal: string) {
  const trimmed = goal.trim().toLowerCase();
  return trimmed || "__no_goal__";
}

export function mergeUniqueTitles(current: string[], next: string[]) {
  const merged = new Map<string, string>();
  for (const title of [...current, ...next]) {
    const trimmed = title.trim();
    if (!trimmed) continue;
    const key = trimmed.toLowerCase();
    if (!merged.has(key)) {
      merged.set(key, trimmed);
    }
  }
  return Array.from(merged.values());
}

export function getShownSuggestionTitles(goalKey: string) {
  return readStoredState().shownTitlesByGoal[goalKey] ?? [];
}

export function rememberShownSuggestionTitles(goalKey: string, titles: string[]) {
  updateStoredState((current) => ({
    ...current,
    shownTitlesByGoal: {
      ...current.shownTitlesByGoal,
      [goalKey]: titles,
    },
  }));
}

export function suggestionKeyFor(generatedAt: string, suggestion: CoachSuggestion, index: number) {
  return `${generatedAt}:${index}:${suggestion.title}`;
}

export function toSuggestionDraft(generatedAt: string, suggestion: CoachSuggestion, index: number): SuggestionDraft {
  return {
    suggestionKey: suggestionKeyFor(generatedAt, suggestion, index),
    title: suggestion.title,
    description: suggestion.description,
    category: suggestion.category,
    difficulty: suggestion.difficulty,
    estimatedMinutes: suggestion.estimatedMinutes,
    reason: suggestion.reason,
  };
}

export function findStoredSuggestionDraft(suggestionKey: string): SuggestionDraft | null {
  const response = readStoredState().response;
  if (!response?.generatedAt) return null;

  const index = response.suggestions.findIndex(
    (suggestion, currentIndex) => suggestionKeyFor(response.generatedAt, suggestion, currentIndex) === suggestionKey
  );
  if (index < 0) return null;

  return toSuggestionDraft(response.generatedAt, response.suggestions[index], index);
}
