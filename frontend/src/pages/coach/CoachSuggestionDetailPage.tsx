import { ArrowLeft, ArrowUpRight, Clock3, ShieldCheck, Sparkles, Target } from "lucide-react";
import { Link, useParams } from "react-router-dom";

import { getStoredAcceptedQuests, findStoredSuggestionDraft } from "./coachSession";

function categoryLabel(category: string) {
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

export default function CoachSuggestionDetailPage() {
  const { suggestionKey = "" } = useParams();
  const suggestion = findStoredSuggestionDraft(suggestionKey);
  const acceptedQuest = getStoredAcceptedQuests()[suggestionKey];

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
            The current coach suggestion could not be found in this browser session. Go back to the coach page and
            generate a fresh set before opening details again.
          </p>
        </section>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-4xl space-y-6 px-2 pb-10 sm:px-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Link
          to="/coach"
          className="inline-flex items-center gap-2 text-sm text-slate-600 hover:text-slate-950 dark:text-slate-300 dark:hover:text-white"
        >
          <ArrowLeft className="h-4 w-4" />
          Back to coach
        </Link>
        {acceptedQuest ? (
          <Link
            to={`/quests/${acceptedQuest.questId}`}
            className="inline-flex items-center gap-2 rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-2 text-sm font-medium text-emerald-700 hover:bg-emerald-100 dark:border-emerald-900/60 dark:bg-emerald-950/30 dark:text-emerald-200 dark:hover:bg-emerald-950/50"
          >
            View quest
            <ArrowUpRight className="h-4 w-4" />
          </Link>
        ) : (
          <Link
            to={`/coach/suggestions/${encodeURIComponent(suggestion.suggestionKey)}/review`}
            className="rounded-2xl bg-slate-950 px-4 py-2.5 text-sm font-medium text-white shadow-lg shadow-slate-950/15 hover:bg-slate-800 dark:bg-slate-100 dark:text-slate-950 dark:hover:bg-white"
          >
            Accept as Quest
          </Link>
        )}
      </div>

      <section className="rounded-[30px] border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-[#131a25]">
        <div className="border-b border-slate-200 bg-slate-50/90 px-6 py-5 dark:border-slate-800 dark:bg-[#161d29]">
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

          <h1 className="mt-4 text-3xl font-semibold tracking-tight text-slate-950 dark:text-slate-50">
            {suggestion.title}
          </h1>
          <p className="mt-3 max-w-3xl text-sm leading-7 text-slate-600 dark:text-slate-300">
            Review the quest details before sending it to the review/create page.
          </p>
        </div>

        <div className="grid gap-5 px-6 py-6 lg:grid-cols-[minmax(0,1.2fr)_320px]">
          <div className="space-y-5">
            <section className="rounded-[24px] border border-slate-200 bg-white p-5 dark:border-slate-800 dark:bg-[#0d131c]">
              <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.22em] text-slate-500 dark:text-slate-400">
                <Target className="h-4 w-4" />
                Description
              </div>
              <p className="mt-3 text-sm leading-7 text-slate-700 dark:text-slate-200">{suggestion.description}</p>
            </section>

            <section className="rounded-[24px] border border-slate-200 bg-slate-50/80 p-5 dark:border-slate-800 dark:bg-[#161d29]">
              <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.22em] text-slate-500 dark:text-slate-400">
                <Sparkles className="h-4 w-4" />
                Why it helps
              </div>
              <p className="mt-3 text-sm leading-6 text-slate-700 dark:text-slate-200">{suggestion.reason}</p>
            </section>
          </div>

          <aside className="space-y-4">
            <section className="rounded-[24px] border border-slate-200 bg-white p-5 dark:border-slate-800 dark:bg-[#0d131c]">
              <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.22em] text-slate-500 dark:text-slate-400">
                <Clock3 className="h-4 w-4" />
                Quest defaults
              </div>
              <ul className="mt-3 space-y-3 text-sm leading-6 text-slate-700 dark:text-slate-200">
                <li>Starts immediately after you confirm it.</li>
                <li>Ends 7 days later.</li>
                <li>Created as a private quest.</li>
              </ul>
            </section>

            <section className="rounded-[24px] border border-slate-200 bg-white p-5 dark:border-slate-800 dark:bg-[#0d131c]">
              <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.22em] text-slate-500 dark:text-slate-400">
                <ShieldCheck className="h-4 w-4" />
                Next step
              </div>
              <p className="mt-3 text-sm leading-6 text-slate-700 dark:text-slate-200">
                {acceptedQuest
                  ? "This suggestion already became a quest in your account."
                  : "Open the review page to adjust title, description, or category before creating the quest."}
              </p>
            </section>
          </aside>
        </div>
      </section>
    </div>
  );
}
