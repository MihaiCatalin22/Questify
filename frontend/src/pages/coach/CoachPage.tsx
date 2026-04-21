import { useEffect, useEffectEvent, useMemo, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { toast } from "react-hot-toast";
import {
  ArrowUpRight,
  Bot,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  Clock3,
  RefreshCcw,
  ShieldCheck,
  Sparkles,
  Target,
  TriangleAlert,
  X,
} from "lucide-react";

import type { CoachSuggestion, CoachSuggestionsResponse } from "../../api/coach";
import type { CoachSettingsDTO, UpdateCoachSettingsInput } from "../../api/users";
import type { QuestCategory } from "../../types/quest";
import { useAuthContext } from "../../contexts/AuthContext";
import { useCreateQuest } from "../../hooks/useQuests";
import {
  useCoachDashboardSummary,
  useCoachSettings,
  useGenerateCoachSuggestions,
  useUpdateCoachSettings,
} from "../../hooks/useCoach";

type GenerationViewState = "idle" | "loading" | "success" | "fallback" | "error";

type ApiErrorPayload = {
  message?: string;
  error?: string;
};

type HttpLikeError = {
  message?: string;
  response?: {
    data?: ApiErrorPayload;
  };
};

type AcceptedQuestState = {
  questId: number;
  title: string;
};

type SuggestionDraft = {
  suggestionKey: string;
  title: string;
  description: string;
  category: QuestCategory;
  difficulty: CoachSuggestion["difficulty"];
  estimatedMinutes: number;
  reason: string;
};

const QUEST_CATEGORIES: QuestCategory[] = [
  "COMMUNITY",
  "FITNESS",
  "HABIT",
  "HOBBY",
  "OTHER",
  "STUDY",
  "WORK",
];

function formatDate(iso?: string | null) {
  if (!iso) return "Not yet";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return String(iso);
  return date.toLocaleString();
}

function plusSevenDaysIso(startIso: string) {
  const start = new Date(startIso);
  return new Date(start.getTime() + 7 * 24 * 60 * 60 * 1000).toISOString();
}

function isHttpLikeError(error: unknown): error is HttpLikeError {
  return typeof error === "object" && error !== null;
}

function extractApiMessage(error: unknown, fallback: string) {
  if (!isHttpLikeError(error)) return fallback;

  const responseData = error?.response?.data;
  if (typeof responseData?.message === "string" && responseData.message.trim()) {
    return responseData.message;
  }
  if (typeof responseData?.error === "string" && responseData.error.trim()) {
    return responseData.error;
  }
  if (typeof error?.message === "string" && error.message.trim()) {
    return error.message;
  }
  return fallback;
}

function mapSettingsToForm(settings?: CoachSettingsDTO | null): UpdateCoachSettingsInput {
  return {
    aiCoachEnabled: Boolean(settings?.aiCoachEnabled),
    coachGoal: settings?.coachGoal ?? "",
  };
}

function suggestionKeyFor(generatedAt: string, suggestion: CoachSuggestion, index: number) {
  return `${generatedAt}:${index}:${suggestion.title}`;
}

function summaryValue(value: string | number | null | undefined) {
  if (value == null || value === "") return "—";
  return String(value);
}

function difficultyTone(difficulty: CoachSuggestion["difficulty"]) {
  if (difficulty === "hard") {
    return "border-rose-200 bg-rose-50 text-rose-700 dark:border-rose-900/60 dark:bg-rose-950/40 dark:text-rose-200";
  }
  if (difficulty === "medium") {
    return "border-amber-200 bg-amber-50 text-amber-700 dark:border-amber-900/60 dark:bg-amber-950/40 dark:text-amber-200";
  }
  return "border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-900/60 dark:bg-emerald-950/40 dark:text-emerald-200";
}

function categoryLabel(category: QuestCategory) {
  return category.replaceAll("_", " ");
}

function normalizeGoalKey(goal: string) {
  const trimmed = goal.trim().toLowerCase();
  return trimmed || "__no_goal__";
}

function mergeUniqueTitles(current: string[], next: string[]) {
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

function toSuggestionDraft(generatedAt: string, suggestion: CoachSuggestion, index: number): SuggestionDraft {
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

export default function CoachPage() {
  const { user } = useAuthContext();
  const settingsQ = useCoachSettings();
  const summaryQ = useCoachDashboardSummary();
  const saveSettingsM = useUpdateCoachSettings();
  const generateM = useGenerateCoachSuggestions();
  const createQuestM = useCreateQuest();

  const [settingsForm, setSettingsForm] = useState<UpdateCoachSettingsInput>({
    aiCoachEnabled: false,
    coachGoal: "",
  });
  const [includeRecentHistory, setIncludeRecentHistory] = useState(true);
  const [suggestions, setSuggestions] = useState<CoachSuggestionsResponse | null>(null);
  const [viewState, setViewState] = useState<GenerationViewState>("idle");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [settingsExpanded, setSettingsExpanded] = useState(false);
  const [detailDraft, setDetailDraft] = useState<SuggestionDraft | null>(null);
  const [reviewDraft, setReviewDraft] = useState<SuggestionDraft | null>(null);
  const [acceptedQuests, setAcceptedQuests] = useState<Record<string, AcceptedQuestState>>({});

  const hasAutoGeneratedRef = useRef(false);
  const shownSuggestionTitlesRef = useRef<Record<string, string[]>>({});
  const settingsRef = useRef<HTMLElement | null>(null);

  useEffect(() => {
    if (!settingsQ.data) return;
    setSettingsForm(mapSettingsToForm(settingsQ.data));
  }, [settingsQ.data]);

  useEffect(() => {
    setAcceptedQuests({});
    setDetailDraft(null);
    setReviewDraft(null);
  }, [suggestions?.generatedAt]);

  const persistedGoalText = (settingsQ.data?.coachGoal ?? "").trim();
  const draftGoalText = (settingsForm.coachGoal ?? "").trim();
  const activeGoalText = useMemo(() => draftGoalText || persistedGoalText, [draftGoalText, persistedGoalText]);
  const generationGoalKey = useMemo(
    () => normalizeGoalKey(persistedGoalText || draftGoalText),
    [draftGoalText, persistedGoalText]
  );
  const activeOverlay = reviewDraft ? "review" : detailDraft ? "details" : null;

  function closeOverlay() {
    if (reviewDraft) {
      if (!createQuestM.isPending) {
        setReviewDraft(null);
      }
      return;
    }
    if (detailDraft) {
      setDetailDraft(null);
    }
  }

  useEffect(() => {
    if (!activeOverlay || typeof document === "undefined") return;

    const previousOverflow = document.body.style.overflow;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        if (reviewDraft) {
          if (!createQuestM.isPending) {
            setReviewDraft(null);
          }
          return;
        }
        if (detailDraft) {
          setDetailDraft(null);
        }
      }
    };

    document.body.style.overflow = "hidden";
    window.addEventListener("keydown", onKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown", onKeyDown);
    };
  }, [activeOverlay, createQuestM.isPending, detailDraft, reviewDraft]);

  const runGeneration = useEffectEvent(
    async (
      nextIncludeRecentHistory = includeRecentHistory,
      goalKeyOverride = generationGoalKey
    ) => {
    setViewState("loading");
    setErrorMessage(null);
    setDetailDraft(null);
    setReviewDraft(null);

    try {
      const excludedSuggestionTitles = shownSuggestionTitlesRef.current[goalKeyOverride] ?? [];
      const response = await generateM.mutateAsync({
        mode: "DEFAULT",
        includeRecentHistory: nextIncludeRecentHistory,
        excludedSuggestionTitles,
      });

      const returnedTitles = response.suggestions
        .map((suggestion) => suggestion.title.trim())
        .filter((title) => title.length > 0);
      shownSuggestionTitlesRef.current[goalKeyOverride] = mergeUniqueTitles(excludedSuggestionTitles, returnedTitles);

      setSuggestions(response);
      setViewState(response.status === "FALLBACK" ? "fallback" : "success");
    } catch (error: unknown) {
      const message = extractApiMessage(error, "Failed to generate coach suggestions");
      setSuggestions(null);
      setErrorMessage(message);
      setViewState("error");
      toast.error(message);
    }
    }
  );

  async function onSaveSettings() {
    try {
      const saved = await saveSettingsM.mutateAsync({
        aiCoachEnabled: Boolean(settingsForm.aiCoachEnabled),
        coachGoal: settingsForm.coachGoal ?? "",
      });

      setSettingsForm(mapSettingsToForm(saved));
      toast.success("Coach settings saved");

      if (!saved.aiCoachEnabled) {
        setSuggestions(null);
        setErrorMessage(null);
        setViewState("idle");
        setDetailDraft(null);
        setReviewDraft(null);
        hasAutoGeneratedRef.current = false;
        return;
      }

      hasAutoGeneratedRef.current = true;
      await runGeneration(includeRecentHistory, normalizeGoalKey(saved.coachGoal ?? ""));
    } catch (error: unknown) {
      toast.error(extractApiMessage(error, "Failed to save coach settings"));
    }
  }

  useEffect(() => {
    if (!settingsQ.data) return;
    if (!settingsQ.data.aiCoachEnabled) return;
    if (hasAutoGeneratedRef.current) return;

    hasAutoGeneratedRef.current = true;
    void runGeneration();
  }, [runGeneration, settingsQ.data]);

  function revealSettings() {
    setSettingsExpanded(true);
    settingsRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  function openSuggestionDetails(suggestion: CoachSuggestion, index: number) {
    if (!suggestions?.generatedAt) return;
    setDetailDraft(toSuggestionDraft(suggestions.generatedAt, suggestion, index));
  }

  function openReviewForSuggestion(suggestion: CoachSuggestion, index: number) {
    if (!suggestions?.generatedAt) return;
    const draft = toSuggestionDraft(suggestions.generatedAt, suggestion, index);
    if (acceptedQuests[draft.suggestionKey]) return;
    setDetailDraft(null);
    setReviewDraft(draft);
  }

  function openReviewFromDetailDraft() {
    if (!detailDraft || acceptedQuests[detailDraft.suggestionKey]) return;
    setReviewDraft(detailDraft);
    setDetailDraft(null);
  }

  async function confirmQuestCreation() {
    if (!reviewDraft) return;
    if (!user?.id) {
      toast.error("You need to be logged in to create a quest.");
      return;
    }

    const title = reviewDraft.title.trim();
    const description = reviewDraft.description.trim();

    if (title.length < 3) {
      toast.error("Quest title must be at least 3 characters.");
      return;
    }
    if (description.length < 10) {
      toast.error("Quest description must be at least 10 characters.");
      return;
    }

    const startDate = new Date().toISOString();
    const endDate = plusSevenDaysIso(startDate);

    try {
      const createdQuest = await createQuestM.mutateAsync({
        title,
        description,
        category: reviewDraft.category,
        startDate,
        endDate,
        createdByUserId: String(user.id),
        visibility: "PRIVATE",
      });

      setAcceptedQuests((current) => ({
        ...current,
        [reviewDraft.suggestionKey]: {
          questId: createdQuest.id,
          title: createdQuest.title,
        },
      }));
      setReviewDraft(null);
      await summaryQ.refetch();
      toast.success("Quest created from coach suggestion");
    } catch (error: unknown) {
      toast.error(extractApiMessage(error, "Failed to create quest from suggestion"));
    }
  }

  const persistedOptIn = Boolean(settingsQ.data?.aiCoachEnabled);
  const draftOptIn = Boolean(settingsForm.aiCoachEnabled);
  const isGenerating = generateM.isPending || viewState === "loading";
  const generationDisabled =
    !draftOptIn || !persistedOptIn || saveSettingsM.isPending || isGenerating || settingsQ.isLoading;
  const quickActionsDisabled = settingsQ.isLoading || saveSettingsM.isPending || isGenerating;
  const heroGoal = activeGoalText || "Add a goal to help the coach aim the next quest suggestions.";

  const summaryItems = [
    {
      label: "Goal",
      value: heroGoal,
      icon: Target,
      accent: "from-cyan-500/20 to-transparent",
    },
    {
      label: "Active quests",
      value: summaryValue(summaryQ.data?.activeQuests),
      icon: Sparkles,
      accent: "from-emerald-500/20 to-transparent",
    },
    {
      label: "Completed quests",
      value: summaryValue(summaryQ.data?.completedQuests),
      icon: ShieldCheck,
      accent: "from-amber-500/20 to-transparent",
    },
    {
      label: "Last coach refresh",
      value: suggestions?.generatedAt ? formatDate(suggestions.generatedAt) : "Not yet",
      icon: Clock3,
      accent: "from-fuchsia-500/20 to-transparent",
    },
  ] as const;

  if (settingsQ.isLoading) {
    return <div className="p-6 opacity-70">Loading AI Coach…</div>;
  }

  if (settingsQ.isError) {
    return (
      <div className="p-6 text-red-600">
        {extractApiMessage(settingsQ.error, "Failed to load coach settings")}
      </div>
    );
  }

  const displaySuggestions = suggestions?.suggestions ?? [];
  const isSystemFallback = viewState === "fallback" && suggestions?.source === "SYSTEM";
  const detailAcceptedQuest = detailDraft ? acceptedQuests[detailDraft.suggestionKey] : null;

  return (
    <div className="mx-auto max-w-7xl space-y-6 px-2 pb-10 sm:px-4">
      <section className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div className="min-w-0">
          <div className="flex items-center gap-2 text-sm font-medium text-slate-500 dark:text-slate-400">
            <Sparkles className="h-4 w-4 text-cyan-500" />
            AI Coach
          </div>
          <h1 className="mt-1 text-3xl font-semibold tracking-tight text-slate-950 dark:text-slate-50">Coach</h1>
          <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-600 dark:text-slate-300">
            Turn the next useful suggestion into a quest quickly. Summary and settings stay nearby, but suggestions are
            the main workspace.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2 text-xs">
          <span className="rounded-full border border-slate-200 px-2.5 py-1 text-slate-600 dark:border-slate-800 dark:text-slate-300">
            {draftOptIn ? (isGenerating ? "Generating" : "Ready") : "Coach disabled"}
          </span>
          {suggestions?.generatedAt && (
            <span className="rounded-full border border-slate-200 px-2.5 py-1 text-slate-600 dark:border-slate-800 dark:text-slate-300">
              Last refresh: {formatDate(suggestions.generatedAt)}
            </span>
          )}
        </div>
      </section>

      <section className="space-y-4">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <h2 className="text-2xl font-semibold text-slate-950 dark:text-slate-50">Suggested Next Quests</h2>
            <p className="mt-1 text-sm text-slate-600 dark:text-slate-300">
              Click a card to inspect it, then review before creating the quest.
            </p>
          </div>
          {suggestions?.generatedAt && (
            <div className="text-xs text-slate-500 dark:text-slate-400">
              Last updated {formatDate(suggestions.generatedAt)}
            </div>
          )}
        </div>

        {(!draftOptIn || !persistedOptIn) && (
          <div className="rounded-[24px] border border-dashed border-slate-300 bg-white p-6 text-sm text-slate-600 dark:border-slate-700 dark:bg-[#131a25] dark:text-slate-300">
            {draftOptIn && !persistedOptIn
              ? "AI Coach is enabled in the draft settings, but not saved yet. Save your settings before generating quest suggestions."
              : "AI Coach is currently off. Open the settings panel below, enable it, and save before generating quest suggestions."}
          </div>
        )}

        {draftOptIn && persistedOptIn && viewState === "idle" && (
          <div className="rounded-[24px] border border-dashed border-slate-300 bg-white p-6 text-sm text-slate-600 dark:border-slate-700 dark:bg-[#131a25] dark:text-slate-300">
            No suggestions yet. Generate a coach pass to populate the quest feed.
          </div>
        )}

        {draftOptIn && viewState === "loading" && (
          <div className="rounded-[24px] border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-[#131a25]">
            <div className="flex items-start gap-3 text-sm text-slate-600 dark:text-slate-300">
              <RefreshCcw className="mt-0.5 h-4 w-4 animate-spin" />
              <div>
                <div>Generating personalized quest suggestions…</div>
                <div className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                  The local model can take a while when preparing quest-ready cards, especially on a cold start.
                </div>
              </div>
            </div>
          </div>
        )}

        {draftOptIn && viewState === "error" && (
          <div className="rounded-[24px] border border-red-200 bg-red-50/80 p-6 text-sm text-red-700 dark:border-red-900 dark:bg-red-950/30 dark:text-red-300">
            <div className="font-medium">Coach request failed</div>
            <div className="mt-1">{errorMessage ?? "Failed to generate coach suggestions."}</div>
          </div>
        )}

        {draftOptIn && suggestions && (viewState === "success" || viewState === "fallback") && (
          <>
            {isSystemFallback && (
              <div className="rounded-[24px] border border-amber-200 bg-amber-50/90 p-5 text-sm text-amber-900 dark:border-amber-900/70 dark:bg-amber-950/30 dark:text-amber-100">
                <div className="flex items-start gap-3">
                  <TriangleAlert className="mt-0.5 h-4 w-4 shrink-0" />
                  <div>
                    <div className="font-semibold">AI suggestions were not available for this refresh.</div>
                    <div className="mt-1 text-sm leading-6 text-amber-800 dark:text-amber-200">
                      These are basic backup suggestions generated from your current goal and coach context. You can
                      still accept them as quests, but they are less tailored than a successful AI pass.
                    </div>
                  </div>
                </div>
              </div>
            )}

            {displaySuggestions.length > 0 ? (
              <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
                {displaySuggestions.map((suggestion, index) => {
                  const suggestionKey = suggestionKeyFor(suggestions.generatedAt, suggestion, index);
                  const acceptedQuest = acceptedQuests[suggestionKey];

                  return (
                    <article
                      key={suggestionKey}
                      role="button"
                      tabIndex={0}
                      onClick={() => openSuggestionDetails(suggestion, index)}
                      onKeyDown={(event) => {
                        if (event.key === "Enter" || event.key === " ") {
                          event.preventDefault();
                          openSuggestionDetails(suggestion, index);
                        }
                      }}
                      className="group flex h-full cursor-pointer flex-col overflow-hidden rounded-[28px] border border-slate-200 bg-white shadow-sm transition hover:-translate-y-0.5 hover:border-slate-300 hover:shadow-md focus:outline-none focus:ring-2 focus:ring-cyan-500 dark:border-slate-800 dark:bg-[#131a25] dark:hover:border-slate-700"
                    >
                      <div className="flex h-full flex-col gap-4 p-5">
                        <div className="flex flex-wrap items-center gap-2 text-xs">
                          <span
                            className={`rounded-full border px-2.5 py-1 text-xs font-semibold capitalize ${difficultyTone(
                              suggestion.difficulty
                            )}`}
                          >
                            {suggestion.difficulty}
                          </span>
                          <span className="rounded-full border border-slate-200 px-2.5 py-1 text-slate-600 dark:border-slate-700 dark:text-slate-300">
                            {suggestion.estimatedMinutes} min
                          </span>
                          <span className="rounded-full border border-slate-200 px-2.5 py-1 text-slate-600 dark:border-slate-700 dark:text-slate-300">
                            {categoryLabel(suggestion.category)}
                          </span>
                          {acceptedQuest && (
                            <span className="rounded-full border border-emerald-200 bg-emerald-50 px-2.5 py-1 text-emerald-700 dark:border-emerald-900/70 dark:bg-emerald-950/30 dark:text-emerald-200">
                              Accepted
                            </span>
                          )}
                        </div>

                        <div className="min-w-0">
                          <h3 className="text-xl font-semibold tracking-tight text-slate-950 dark:text-slate-50">
                            {suggestion.title}
                          </h3>
                          <p className="mt-2 text-sm leading-6 text-slate-700 dark:text-slate-200">
                            {suggestion.description}
                          </p>
                        </div>

                        <div className="rounded-[20px] border border-slate-200 bg-slate-50/80 px-4 py-3 text-sm leading-6 text-slate-600 dark:border-slate-800 dark:bg-[#0d131c] dark:text-slate-300">
                          {suggestion.reason}
                        </div>

                        <div className="mt-auto flex items-center justify-between gap-3 pt-2">
                          <div className="text-xs text-slate-500 dark:text-slate-400">
                            {acceptedQuest ? "Quest created from this card." : "Click for details or review first."}
                          </div>

                          {acceptedQuest ? (
                            <Link
                              to={`/quests/${acceptedQuest.questId}`}
                              onClick={(event) => event.stopPropagation()}
                              className="inline-flex items-center gap-2 rounded-2xl border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm font-medium text-emerald-700 hover:bg-emerald-100 dark:border-emerald-900/60 dark:bg-emerald-950/30 dark:text-emerald-200 dark:hover:bg-emerald-950/50"
                            >
                              View quest
                              <ArrowUpRight className="h-4 w-4" />
                            </Link>
                          ) : (
                            <button
                              onClick={(event) => {
                                event.stopPropagation();
                                openReviewForSuggestion(suggestion, index);
                              }}
                              className="rounded-2xl bg-slate-950 px-4 py-2.5 text-sm font-medium text-white shadow-lg shadow-slate-950/15 hover:bg-slate-800 dark:bg-slate-100 dark:text-slate-950 dark:hover:bg-white"
                            >
                              Accept as Quest
                            </button>
                          )}
                        </div>
                      </div>
                    </article>
                  );
                })}
              </div>
            ) : (
              <div className="rounded-[24px] border border-slate-200 bg-white p-6 text-sm text-slate-600 dark:border-slate-800 dark:bg-[#131a25] dark:text-slate-300">
                The coach returned no quest cards. Try refreshing after adjusting your goal.
              </div>
            )}

            <div className="grid gap-4 lg:grid-cols-2">
              <section className="rounded-[24px] border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-[#131a25]">
                <div className="text-xs font-semibold uppercase tracking-[0.22em] text-slate-500 dark:text-slate-400">
                  Reflection
                </div>
                <p className="mt-3 text-sm leading-6 text-slate-700 dark:text-slate-200">{suggestions.reflection}</p>
              </section>

              <section className="rounded-[24px] border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-[#131a25]">
                <div className="text-xs font-semibold uppercase tracking-[0.22em] text-slate-500 dark:text-slate-400">
                  Streak-Saving Nudge
                </div>
                <p className="mt-3 text-sm leading-6 text-slate-700 dark:text-slate-200">{suggestions.nudge}</p>
              </section>
            </div>
          </>
        )}
      </section>

      <div className="grid gap-4 xl:grid-cols-[minmax(0,1.1fr)_340px]">
        <section className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-[#131a25]">
          <div className="flex items-start justify-between gap-4">
            <div>
              <h2 className="text-xl font-semibold text-slate-950 dark:text-slate-50">Today&apos;s Summary</h2>
              <p className="mt-1 text-sm text-slate-600 dark:text-slate-300">
                Compact context for the coach, without getting in the way of the quest cards.
              </p>
            </div>
            {summaryQ.isFetching && (
              <span className="rounded-full border border-slate-200 px-2.5 py-1 text-xs text-slate-500 dark:border-slate-700 dark:text-slate-400">
                Refreshing…
              </span>
            )}
          </div>

          <div className="mt-5 grid gap-3 md:grid-cols-2">
            {summaryItems.map((item) => {
              const Icon = item.icon;
              const valueClass =
                item.label === "Goal"
                  ? "text-sm leading-6 text-slate-900 dark:text-slate-50"
                  : "text-xl font-semibold text-slate-950 dark:text-slate-50";

              return (
                <article
                  key={item.label}
                  className="relative overflow-hidden rounded-[22px] border border-slate-200 bg-slate-50/80 p-4 dark:border-slate-800 dark:bg-[#0d131c]"
                >
                  <div className={`pointer-events-none absolute inset-0 bg-gradient-to-br ${item.accent}`} />
                  <div className="relative">
                    <div className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.22em] text-slate-500 dark:text-slate-400">
                      <Icon className="h-4 w-4" />
                      {item.label}
                    </div>
                    <div className={`mt-3 ${valueClass}`}>{item.value}</div>
                  </div>
                </article>
              );
            })}
          </div>
        </section>

        <aside className="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-[#131a25]">
          <h2 className="text-xl font-semibold text-slate-950 dark:text-slate-50">Quick Actions</h2>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-300">
            Generate a fresh coach pass, refresh the current one, or jump to settings.
          </p>

          <div className="mt-5 space-y-3">
            <button
              onClick={() => void runGeneration()}
              disabled={quickActionsDisabled || !draftOptIn || !persistedOptIn}
              className="flex w-full items-center justify-between rounded-[18px] border border-slate-200 bg-slate-50 px-4 py-3.5 text-left transition hover:border-slate-300 hover:bg-white disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-800 dark:bg-[#0d131c] dark:hover:border-slate-700 dark:hover:bg-[#121925]"
            >
              <div>
                <div className="text-sm font-semibold text-slate-950 dark:text-slate-50">Generate Suggestions</div>
                <div className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                  Run the coach with your saved goal and current history settings.
                </div>
              </div>
              <Sparkles className="h-4 w-4 text-cyan-500" />
            </button>

            <button
              onClick={() => void runGeneration()}
              disabled={generationDisabled}
              className="flex w-full items-center justify-between rounded-[18px] border border-slate-200 bg-slate-50 px-4 py-3.5 text-left transition hover:border-slate-300 hover:bg-white disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-800 dark:bg-[#0d131c] dark:hover:border-slate-700 dark:hover:bg-[#121925]"
            >
              <div>
                <div className="text-sm font-semibold text-slate-950 dark:text-slate-50">Refresh Coach</div>
                <div className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                  {isGenerating ? "Generation is already running." : "Request a fresh set of suggestion cards."}
                </div>
              </div>
              <RefreshCcw className={`h-4 w-4 text-slate-500 ${isGenerating ? "animate-spin" : ""}`} />
            </button>

            <button
              onClick={revealSettings}
              className="flex w-full items-center justify-between rounded-[18px] border border-slate-200 bg-slate-50 px-4 py-3.5 text-left transition hover:border-slate-300 hover:bg-white dark:border-slate-800 dark:bg-[#0d131c] dark:hover:border-slate-700 dark:hover:bg-[#121925]"
            >
              <div>
                <div className="text-sm font-semibold text-slate-950 dark:text-slate-50">Open Settings</div>
                <div className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                  Expand the AI/privacy panel and adjust goal or history usage.
                </div>
              </div>
              <Bot className="h-4 w-4 text-slate-500" />
            </button>
          </div>
        </aside>
      </div>

      <section
        ref={settingsRef}
        className="overflow-hidden rounded-[28px] border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-[#131a25]"
      >
        <button
          onClick={() => setSettingsExpanded((current) => !current)}
          className="flex w-full items-center justify-between px-6 py-5 text-left"
        >
          <div>
            <div className="text-xs font-semibold uppercase tracking-[0.22em] text-slate-500 dark:text-slate-400">
              Privacy & AI Settings
            </div>
            <h2 className="mt-1 text-xl font-semibold text-slate-950 dark:text-slate-50">Coach settings</h2>
            <p className="mt-1 text-sm text-slate-600 dark:text-slate-300">
              Minimal quest context only. No uploads, proof media, or unrelated profile data are used.
            </p>
          </div>
          {settingsExpanded ? (
            <ChevronUp className="h-5 w-5 text-slate-500" />
          ) : (
            <ChevronDown className="h-5 w-5 text-slate-500" />
          )}
        </button>

        {settingsExpanded && (
          <div className="border-t border-slate-200 px-6 py-5 dark:border-slate-800">
            <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_260px]">
              <div className="space-y-5">
                <label className="flex items-start gap-3 rounded-[24px] border border-slate-200 bg-slate-50 p-4 dark:border-slate-800 dark:bg-[#0d131c]">
                  <input
                    type="checkbox"
                    checked={draftOptIn}
                    onChange={(event) =>
                      setSettingsForm((current) => ({
                        ...current,
                        aiCoachEnabled: event.target.checked,
                      }))
                    }
                    className="mt-1 h-4 w-4 rounded border-slate-300 text-slate-900 focus:ring-slate-400"
                  />
                  <div>
                    <div className="text-sm font-medium text-slate-900 dark:text-slate-100">Enable AI Coach</div>
                    <div className="mt-1 text-xs text-slate-600 dark:text-slate-300">
                      Required before the coach can generate suggestions for your account.
                    </div>
                  </div>
                </label>

                <div>
                  <label className="block text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                    Goal
                  </label>
                  <textarea
                    value={settingsForm.coachGoal ?? ""}
                    onChange={(event) =>
                      setSettingsForm((current) => ({
                        ...current,
                        coachGoal: event.target.value,
                      }))
                    }
                    rows={5}
                    placeholder="Example: Build speedrunning consistency with short, repeatable sessions that stay realistic after work."
                    className="mt-1 w-full rounded-[24px] border border-slate-200 bg-white px-4 py-3 text-sm leading-6 dark:border-slate-800 dark:bg-[#0d131c]"
                  />
                </div>
              </div>

              <div className="space-y-5">
                <label className="flex items-start gap-3 rounded-[24px] border border-slate-200 bg-slate-50 p-4 dark:border-slate-800 dark:bg-[#0d131c]">
                  <input
                    type="checkbox"
                    checked={includeRecentHistory}
                    onChange={(event) => setIncludeRecentHistory(event.target.checked)}
                    className="mt-1 h-4 w-4 rounded border-slate-300 text-slate-900 focus:ring-slate-400"
                  />
                  <div>
                    <div className="text-sm font-medium text-slate-900 dark:text-slate-100">Use recent quest history</div>
                    <div className="mt-1 text-xs text-slate-600 dark:text-slate-300">
                      Lets the coach tighten suggestions using recent completion timestamps and quest titles.
                    </div>
                  </div>
                </label>

                <div className="rounded-[24px] border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600 dark:border-slate-800 dark:bg-[#0d131c] dark:text-slate-300">
                  <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.22em] text-slate-500 dark:text-slate-400">
                    <ShieldCheck className="h-4 w-4" />
                    Privacy notes
                  </div>
                  <ul className="mt-3 space-y-2 text-sm leading-6">
                    <li>Suggestions use only minimal quest context.</li>
                    <li>Proof uploads and raw media are excluded.</li>
                    <li>Server-side validation enforces the returned quest shape.</li>
                  </ul>
                </div>
              </div>
            </div>

            <div className="mt-5 flex flex-wrap items-center gap-3">
              <button
                onClick={onSaveSettings}
                disabled={saveSettingsM.isPending}
                className="rounded-2xl bg-slate-950 px-4 py-2 text-sm font-medium text-white shadow-lg shadow-slate-950/15 hover:bg-slate-800 disabled:opacity-60 dark:bg-slate-100 dark:text-slate-950 dark:hover:bg-white"
              >
                {saveSettingsM.isPending ? "Saving…" : "Save coach settings"}
              </button>

              {!persistedOptIn && (
                <span className="text-xs text-amber-700 dark:text-amber-300">
                  Enable AI Coach and save before generating suggestions.
                </span>
              )}
            </div>
          </div>
        )}
      </section>

      {activeOverlay && (
        <div
          className="fixed inset-0 z-[9999] flex items-center justify-center bg-slate-950/75 p-4 backdrop-blur-sm"
          onClick={closeOverlay}
        >
          {detailDraft && !reviewDraft && (
            <div
              className="max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-[30px] border border-slate-200 bg-white shadow-2xl dark:border-slate-800 dark:bg-[#11161f]"
              onClick={(event) => event.stopPropagation()}
            >
              <div className="border-b border-slate-200 bg-slate-50/90 px-6 py-5 dark:border-slate-800 dark:bg-[#161d29]">
                <div className="flex items-start justify-between gap-4">
                  <div className="min-w-0">
                    <div className="text-xs font-semibold uppercase tracking-[0.24em] text-slate-500 dark:text-slate-400">
                      Suggestion Details
                    </div>
                    <h2 className="mt-1 text-2xl font-semibold tracking-tight text-slate-950 dark:text-slate-50">
                      {detailDraft.title}
                    </h2>
                    <p className="mt-2 text-sm leading-6 text-slate-600 dark:text-slate-300">
                      Review the full suggestion, then accept it as a quest if it still fits.
                    </p>
                  </div>
                  <button
                    onClick={() => setDetailDraft(null)}
                    className="rounded-full border border-slate-200 p-2 text-slate-600 hover:bg-slate-100 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-800"
                    aria-label="Close details"
                  >
                    <X className="h-4 w-4" />
                  </button>
                </div>
              </div>

              <div className="space-y-5 px-6 py-5">
                <div className="flex flex-wrap items-center gap-2 text-xs">
                  <span
                    className={`rounded-full border px-2.5 py-1 text-xs font-semibold capitalize ${difficultyTone(
                      detailDraft.difficulty
                    )}`}
                  >
                    {detailDraft.difficulty}
                  </span>
                  <span className="rounded-full border border-slate-200 px-2.5 py-1 text-slate-600 dark:border-slate-700 dark:text-slate-300">
                    {detailDraft.estimatedMinutes} min
                  </span>
                  <span className="rounded-full border border-slate-200 px-2.5 py-1 text-slate-600 dark:border-slate-700 dark:text-slate-300">
                    {categoryLabel(detailDraft.category)}
                  </span>
                </div>

                <div>
                  <div className="text-xs font-semibold uppercase tracking-[0.22em] text-slate-500 dark:text-slate-400">
                    Description
                  </div>
                  <p className="mt-2 text-sm leading-7 text-slate-700 dark:text-slate-200">
                    {detailDraft.description}
                  </p>
                </div>

                <div className="rounded-[24px] border border-slate-200 bg-slate-50 p-4 dark:border-slate-800 dark:bg-[#161d29]">
                  <div className="text-xs font-semibold uppercase tracking-[0.22em] text-slate-500 dark:text-slate-400">
                    Why the coach suggested it
                  </div>
                  <p className="mt-2 text-sm leading-6 text-slate-700 dark:text-slate-200">{detailDraft.reason}</p>
                </div>

                <div className="rounded-[24px] border border-slate-200 bg-white p-4 text-sm text-slate-600 dark:border-slate-800 dark:bg-[#0d131c] dark:text-slate-300">
                  <div className="text-xs font-semibold uppercase tracking-[0.22em] text-slate-500 dark:text-slate-400">
                    Quest defaults
                  </div>
                  <ul className="mt-3 space-y-2 leading-6">
                    <li>The quest starts immediately after you confirm it.</li>
                    <li>The quest ends 7 days later.</li>
                    <li>The quest is created as private visibility.</li>
                  </ul>
                </div>

                {detailAcceptedQuest && (
                  <div className="rounded-[24px] border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800 dark:border-emerald-900/60 dark:bg-emerald-950/30 dark:text-emerald-200">
                    <div className="flex items-center gap-2 font-medium">
                      <CheckCircle2 className="h-4 w-4" />
                      This suggestion is already a quest.
                    </div>
                    <Link
                      to={`/quests/${detailAcceptedQuest.questId}`}
                      className="mt-3 inline-flex items-center gap-2 rounded-2xl border border-emerald-300 px-3 py-2 text-sm font-medium hover:bg-emerald-100 dark:border-emerald-800 dark:hover:bg-emerald-950/50"
                    >
                      View quest
                      <ArrowUpRight className="h-4 w-4" />
                    </Link>
                  </div>
                )}
              </div>

              <div className="flex flex-wrap items-center justify-end gap-3 border-t border-slate-200 px-6 py-4 dark:border-slate-800">
                <button
                  onClick={() => setDetailDraft(null)}
                  className="rounded-2xl border border-slate-200 px-4 py-2 text-sm text-slate-700 hover:bg-slate-50 dark:border-slate-700 dark:text-slate-200 dark:hover:bg-slate-800"
                >
                  Close
                </button>
                {detailAcceptedQuest ? (
                  <Link
                    to={`/quests/${detailAcceptedQuest.questId}`}
                    className="rounded-2xl bg-slate-950 px-4 py-2 text-sm font-medium text-white shadow-lg shadow-slate-950/20 hover:bg-slate-800 dark:bg-slate-100 dark:text-slate-950 dark:hover:bg-white"
                  >
                    View quest
                  </Link>
                ) : (
                  <button
                    onClick={openReviewFromDetailDraft}
                    className="rounded-2xl bg-slate-950 px-4 py-2 text-sm font-medium text-white shadow-lg shadow-slate-950/20 hover:bg-slate-800 dark:bg-slate-100 dark:text-slate-950 dark:hover:bg-white"
                  >
                    Accept as Quest
                  </button>
                )}
              </div>
            </div>
          )}

          {reviewDraft && (
            <div
              className="max-h-[90vh] w-full max-w-3xl overflow-y-auto rounded-[30px] border border-slate-200 bg-white shadow-2xl dark:border-slate-800 dark:bg-[#11161f]"
              onClick={(event) => event.stopPropagation()}
            >
              <div className="border-b border-slate-200 bg-slate-50/90 px-6 py-5 dark:border-slate-800 dark:bg-[#161d29]">
                <div className="flex items-start justify-between gap-4">
                  <div className="min-w-0">
                    <div className="text-xs font-semibold uppercase tracking-[0.24em] text-slate-500 dark:text-slate-400">
                      Review Quest
                    </div>
                    <h2 className="mt-1 text-2xl font-semibold tracking-tight text-slate-950 dark:text-slate-50">
                      Review before creating
                    </h2>
                    <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-600 dark:text-slate-300">
                      Adjust the title, description, or category if needed. The quest starts immediately and ends in 7
                      days.
                    </p>
                  </div>
                  <button
                    onClick={() => setReviewDraft(null)}
                    disabled={createQuestM.isPending}
                    className="rounded-full border border-slate-200 p-2 text-slate-600 hover:bg-slate-100 disabled:opacity-60 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-800"
                    aria-label="Close review"
                  >
                    <X className="h-4 w-4" />
                  </button>
                </div>
              </div>

              <div className="space-y-5 px-6 py-5">
                <div className="grid gap-5 lg:grid-cols-[minmax(0,1.2fr)_minmax(260px,0.8fr)]">
                  <div className="space-y-4">
                    <div>
                      <label className="block text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                        Title
                      </label>
                      <input
                        value={reviewDraft.title}
                        onChange={(event) =>
                          setReviewDraft((current) =>
                            current
                              ? {
                                  ...current,
                                  title: event.target.value,
                                }
                              : current
                          )
                        }
                        className="mt-1 w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 text-sm dark:border-slate-700 dark:bg-[#0d131c]"
                      />
                    </div>

                    <div>
                      <label className="block text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                        Description
                      </label>
                      <textarea
                        rows={6}
                        value={reviewDraft.description}
                        onChange={(event) =>
                          setReviewDraft((current) =>
                            current
                              ? {
                                  ...current,
                                  description: event.target.value,
                                }
                              : current
                          )
                        }
                        className="mt-1 w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 text-sm leading-6 dark:border-slate-700 dark:bg-[#0d131c]"
                      />
                    </div>
                  </div>

                  <div className="space-y-4">
                    <div>
                      <label className="block text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                        Category
                      </label>
                      <select
                        value={reviewDraft.category}
                        onChange={(event) =>
                          setReviewDraft((current) =>
                            current
                              ? {
                                  ...current,
                                  category: event.target.value as QuestCategory,
                                }
                              : current
                          )
                        }
                        className="mt-1 w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 text-sm dark:border-slate-700 dark:bg-[#0d131c]"
                      >
                        {QUEST_CATEGORIES.map((category) => (
                          <option key={category} value={category}>
                            {categoryLabel(category)}
                          </option>
                        ))}
                      </select>
                    </div>

                    <div className="rounded-[24px] border border-slate-200 bg-slate-50 p-4 dark:border-slate-800 dark:bg-[#161d29]">
                      <div className="text-xs font-semibold uppercase tracking-[0.22em] text-slate-500 dark:text-slate-400">
                        Quest Signals
                      </div>
                      <div className="mt-3 flex flex-wrap gap-2">
                        <span
                          className={`rounded-full border px-2.5 py-1 text-xs font-medium capitalize ${difficultyTone(
                            reviewDraft.difficulty
                          )}`}
                        >
                          {reviewDraft.difficulty}
                        </span>
                        <span className="rounded-full border border-slate-200 px-2.5 py-1 text-xs font-medium text-slate-700 dark:border-slate-700 dark:text-slate-200">
                          {reviewDraft.estimatedMinutes} min
                        </span>
                        <span className="rounded-full border border-slate-200 px-2.5 py-1 text-xs font-medium text-slate-700 dark:border-slate-700 dark:text-slate-200">
                          Private quest
                        </span>
                      </div>

                      <div className="mt-4 space-y-2 text-sm text-slate-600 dark:text-slate-300">
                        <div>Starts immediately when you confirm.</div>
                        <div>Ends exactly 7 days later.</div>
                      </div>
                    </div>

                    <div className="rounded-[24px] border border-slate-200 bg-white p-4 text-sm text-slate-600 dark:border-slate-800 dark:bg-[#0d131c] dark:text-slate-300">
                      <div className="text-xs font-semibold uppercase tracking-[0.22em] text-slate-500 dark:text-slate-400">
                        Why the coach suggested it
                      </div>
                      <p className="mt-2 leading-6">{reviewDraft.reason}</p>
                    </div>
                  </div>
                </div>
              </div>

              <div className="flex flex-wrap items-center justify-end gap-3 border-t border-slate-200 px-6 py-4 dark:border-slate-800">
                <button
                  onClick={() => setReviewDraft(null)}
                  disabled={createQuestM.isPending}
                  className="rounded-2xl border border-slate-200 px-4 py-2 text-sm text-slate-700 hover:bg-slate-50 disabled:opacity-60 dark:border-slate-700 dark:text-slate-200 dark:hover:bg-slate-800"
                >
                  Cancel
                </button>
                <button
                  onClick={confirmQuestCreation}
                  disabled={createQuestM.isPending}
                  className="rounded-2xl bg-slate-950 px-4 py-2 text-sm font-medium text-white shadow-lg shadow-slate-950/20 hover:bg-slate-800 disabled:opacity-60 dark:bg-slate-100 dark:text-slate-950 dark:hover:bg-white"
                >
                  {createQuestM.isPending ? "Creating quest…" : "Create quest"}
                </button>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
