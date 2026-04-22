import { useMemo, useState } from "react";
import { toast } from "react-hot-toast";
import { ArrowLeft, CheckCircle2, Clock3, ShieldCheck, Sparkles } from "lucide-react";
import { Link, useNavigate, useParams } from "react-router-dom";

import { useAuthContext } from "../../contexts/AuthContext";
import { useCreateQuest } from "../../hooks/useQuests";
import type { QuestCategory } from "../../types/quest";
import { findStoredSuggestionDraft, rememberAcceptedQuest } from "./coachSession";

const QUEST_CATEGORIES: QuestCategory[] = [
  "COMMUNITY",
  "FITNESS",
  "HABIT",
  "HOBBY",
  "OTHER",
  "STUDY",
  "WORK",
];

function plusSevenDaysIso(startIso: string) {
  const start = new Date(startIso);
  return new Date(start.getTime() + 7 * 24 * 60 * 60 * 1000).toISOString();
}

function categoryLabel(category: QuestCategory) {
  return category.replaceAll("_", " ");
}

function difficultyTone(difficulty: "easy" | "medium" | "hard") {
  if (difficulty === "hard") {
    return "border-rose-200 bg-rose-50 text-rose-700 dark:border-rose-900/60 dark:bg-rose-950/40 dark:text-rose-200";
  }
  if (difficulty === "medium") {
    return "border-amber-200 bg-amber-50 text-amber-700 dark:border-amber-900/60 dark:bg-amber-950/40 dark:text-amber-200";
  }
  return "border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-900/60 dark:bg-emerald-950/40 dark:text-emerald-200";
}

export default function CoachSuggestionReviewPage() {
  const { suggestionKey = "" } = useParams();
  const navigate = useNavigate();
  const { user } = useAuthContext();
  const createQuestM = useCreateQuest();

  const suggestion = useMemo(() => findStoredSuggestionDraft(suggestionKey), [suggestionKey]);
  const [title, setTitle] = useState(suggestion?.title ?? "");
  const [description, setDescription] = useState(suggestion?.description ?? "");
  const [category, setCategory] = useState<QuestCategory>(suggestion?.category ?? "OTHER");

  if (!suggestion) {
    return (
      <div className="mx-auto max-w-3xl space-y-4 px-2 pb-10 sm:px-4">
        <Link
          to="/coach"
          className="inline-flex items-center gap-2 text-sm text-slate-600 hover:text-slate-950 dark:text-slate-300 dark:hover:text-white"
        >
          <ArrowLeft className="h-4 w-4" />
          Back to coach
        </Link>
        <section className="rounded-[28px] border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-[#131a25]">
          <h1 className="text-2xl font-semibold text-slate-950 dark:text-slate-50">Suggestion not available</h1>
          <p className="mt-2 text-sm leading-6 text-slate-600 dark:text-slate-300">
            The review data is missing from this browser session. Go back to the coach page and reopen the suggestion.
          </p>
        </section>
      </div>
    );
  }

  const draft = suggestion;

  async function onCreateQuest() {
    if (!user?.id) {
      toast.error("You need to be logged in to create a quest.");
      return;
    }

    const trimmedTitle = title.trim();
    const trimmedDescription = description.trim();
    if (trimmedTitle.length < 3) {
      toast.error("Quest title must be at least 3 characters.");
      return;
    }
    if (trimmedDescription.length < 10) {
      toast.error("Quest description must be at least 10 characters.");
      return;
    }

    const startDate = new Date().toISOString();
    const endDate = plusSevenDaysIso(startDate);

    try {
      const createdQuest = await createQuestM.mutateAsync({
        title: trimmedTitle,
        description: trimmedDescription,
        category,
        startDate,
        endDate,
        createdByUserId: String(user.id),
        visibility: "PRIVATE",
      });

      rememberAcceptedQuest(draft.suggestionKey, {
        questId: createdQuest.id,
        title: createdQuest.title,
      });
      toast.success("Quest created from coach suggestion");
      navigate(`/quests/${createdQuest.id}`);
    } catch (error: unknown) {
      const message =
        typeof error === "object" &&
        error !== null &&
        "response" in error &&
        typeof (error as { response?: { data?: { message?: string; error?: string } } }).response?.data?.message ===
          "string"
          ? (error as { response?: { data?: { message?: string } } }).response?.data?.message
          : undefined;
      toast.error(message || "Failed to create quest from suggestion");
    }
  }

  return (
    <div className="mx-auto max-w-5xl space-y-6 px-2 pb-10 sm:px-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Link
          to={`/coach/suggestions/${encodeURIComponent(draft.suggestionKey)}`}
          className="inline-flex items-center gap-2 text-sm text-slate-600 hover:text-slate-950 dark:text-slate-300 dark:hover:text-white"
        >
          <ArrowLeft className="h-4 w-4" />
          Back to details
        </Link>
      </div>

      <section className="rounded-[30px] border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-[#131a25]">
        <div className="border-b border-slate-200 bg-slate-50/90 px-6 py-5 dark:border-slate-800 dark:bg-[#161d29]">
          <div className="text-xs font-semibold uppercase tracking-[0.24em] text-slate-500 dark:text-slate-400">
            Review Quest
          </div>
          <h1 className="mt-2 text-3xl font-semibold tracking-tight text-slate-950 dark:text-slate-50">
            Review before creating
          </h1>
          <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-600 dark:text-slate-300">
            Adjust the suggestion if needed, then create it as a private quest that starts immediately and lasts 7
            days.
          </p>
        </div>

        <div className="grid gap-6 px-6 py-6 lg:grid-cols-[minmax(0,1.2fr)_320px]">
          <div className="space-y-5">
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                Title
              </label>
              <input
                value={title}
                onChange={(event) => setTitle(event.target.value)}
                className="mt-1 w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 text-sm dark:border-slate-700 dark:bg-[#0d131c]"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                Description
              </label>
              <textarea
                rows={7}
                value={description}
                onChange={(event) => setDescription(event.target.value)}
                className="mt-1 w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 text-sm leading-6 dark:border-slate-700 dark:bg-[#0d131c]"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                Category
              </label>
              <select
                value={category}
                onChange={(event) => setCategory(event.target.value as QuestCategory)}
                className="mt-1 w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 text-sm dark:border-slate-700 dark:bg-[#0d131c]"
              >
                {QUEST_CATEGORIES.map((currentCategory) => (
                  <option key={currentCategory} value={currentCategory}>
                    {categoryLabel(currentCategory)}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <aside className="space-y-4">
            <section className="rounded-[24px] border border-slate-200 bg-slate-50/80 p-5 dark:border-slate-800 dark:bg-[#161d29]">
              <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.22em] text-slate-500 dark:text-slate-400">
                <Sparkles className="h-4 w-4" />
                Quest signals
              </div>
              <div className="mt-4 flex flex-wrap gap-2">
                <span
                  className={`rounded-full border px-2.5 py-1 text-xs font-medium capitalize ${difficultyTone(
                    draft.difficulty
                  )}`}
                >
                  {draft.difficulty}
                </span>
                <span className="rounded-full border border-slate-200 px-2.5 py-1 text-xs font-medium text-slate-700 dark:border-slate-700 dark:text-slate-200">
                  {draft.estimatedMinutes} min
                </span>
                <span className="rounded-full border border-slate-200 px-2.5 py-1 text-xs font-medium text-slate-700 dark:border-slate-700 dark:text-slate-200">
                  Private quest
                </span>
              </div>
            </section>

            <section className="rounded-[24px] border border-slate-200 bg-white p-5 dark:border-slate-800 dark:bg-[#0d131c]">
              <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.22em] text-slate-500 dark:text-slate-400">
                <Clock3 className="h-4 w-4" />
                Timing
              </div>
              <ul className="mt-3 space-y-3 text-sm leading-6 text-slate-700 dark:text-slate-200">
                <li>Starts immediately when you confirm.</li>
                <li>Ends exactly 7 days later.</li>
              </ul>
            </section>

            <section className="rounded-[24px] border border-slate-200 bg-white p-5 dark:border-slate-800 dark:bg-[#0d131c]">
              <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.22em] text-slate-500 dark:text-slate-400">
                <ShieldCheck className="h-4 w-4" />
                Why this was suggested
              </div>
              <p className="mt-3 text-sm leading-6 text-slate-700 dark:text-slate-200">{draft.reason}</p>
            </section>
          </aside>
        </div>

        <div className="flex flex-wrap items-center justify-end gap-3 border-t border-slate-200 px-6 py-4 dark:border-slate-800">
          <Link
            to={`/coach/suggestions/${encodeURIComponent(draft.suggestionKey)}`}
            className="rounded-2xl border border-slate-200 px-4 py-2 text-sm text-slate-700 hover:bg-slate-50 dark:border-slate-700 dark:text-slate-200 dark:hover:bg-slate-800"
          >
            Cancel
          </Link>
          <button
            onClick={onCreateQuest}
            disabled={createQuestM.isPending}
            className="inline-flex items-center gap-2 rounded-2xl bg-slate-950 px-4 py-2 text-sm font-medium text-white shadow-lg shadow-slate-950/20 hover:bg-slate-800 disabled:opacity-60 dark:bg-slate-100 dark:text-slate-950 dark:hover:bg-white"
          >
            <CheckCircle2 className="h-4 w-4" />
            {createQuestM.isPending ? "Creating quest…" : "Create quest"}
          </button>
        </div>
      </section>
    </div>
  );
}
