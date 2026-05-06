import { ArrowLeft, ArrowUpRight, Clock3, ShieldCheck, Sparkles, Target } from "lucide-react";
import { Link, useParams } from "react-router-dom";

import { Badge, EmptyState, PageHeader, PageShell, Panel } from "../../components/ui";
import { findStoredSuggestionDraft, getStoredAcceptedQuests } from "./coachSession";

function categoryLabel(category: string) {
  return category.replaceAll("_", " ");
}

function difficultyTone(difficulty: "easy" | "medium" | "hard"): "success" | "warning" | "danger" {
  if (difficulty === "hard") return "danger";
  if (difficulty === "medium") return "warning";
  return "success";
}

export default function CoachSuggestionDetailPage() {
  const { suggestionKey = "" } = useParams();
  const suggestion = findStoredSuggestionDraft(suggestionKey);
  const acceptedQuest = getStoredAcceptedQuests()[suggestionKey];

  if (!suggestion) {
    return (
      <PageShell className="max-w-3xl">
        <Link
          to="/coach"
          className="inline-flex items-center gap-2 text-sm text-[rgb(var(--muted))] hover:text-[rgb(var(--text))]"
        >
          <ArrowLeft className="h-4 w-4" />
          Back to coach
        </Link>
        <EmptyState
          title="Suggestion not available."
          description="The current coach suggestion could not be found in this browser session. Go back to the coach page and generate a fresh set before opening details again."
        />
      </PageShell>
    );
  }

  return (
    <PageShell className="max-w-4xl">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Link
          to="/coach"
          className="inline-flex items-center gap-2 text-sm text-[rgb(var(--muted))] hover:text-[rgb(var(--text))]"
        >
          <ArrowLeft className="h-4 w-4" />
          Back to coach
        </Link>

        {acceptedQuest ? (
          <Link to={`/quests/${acceptedQuest.questId}`} className="btn btn-secondary">
            View quest
            <ArrowUpRight className="h-4 w-4" />
          </Link>
        ) : (
          <Link
            to={`/coach/suggestions/${encodeURIComponent(suggestion.suggestionKey)}/review`}
            className="btn btn-primary"
          >
            Accept as Quest
          </Link>
        )}
      </div>

      <PageHeader
        title={suggestion.title}
        description="Read through the suggestion before deciding whether to turn it into a quest."
        actions={
          <>
            <Badge tone={difficultyTone(suggestion.difficulty)} className="capitalize">
              {suggestion.difficulty}
            </Badge>
            <Badge>{suggestion.estimatedMinutes} min</Badge>
            <Badge>{categoryLabel(suggestion.category)}</Badge>
            {acceptedQuest ? <Badge tone="success">Accepted</Badge> : null}
          </>
        }
      />

      <div className="grid gap-4 lg:grid-cols-2">
        <Panel className="p-5 lg:col-span-2">
          <div className="flex items-center gap-2 text-xs font-semibold text-[rgb(var(--muted))]">
            <Target className="h-4 w-4" />
            Description
          </div>
          <p className="mt-3 text-sm leading-7 text-[rgb(var(--muted))]">{suggestion.description}</p>
        </Panel>

        <Panel className="p-5">
          <div className="flex items-center gap-2 text-xs font-semibold text-[rgb(var(--muted))]">
            <Sparkles className="h-4 w-4" />
            Why it helps
          </div>
          <p className="mt-3 text-sm leading-6 text-[rgb(var(--muted))]">{suggestion.reason}</p>
        </Panel>

        <Panel className="p-5">
          <div className="flex items-center gap-2 text-xs font-semibold text-[rgb(var(--muted))]">
            <Clock3 className="h-4 w-4" />
            Quest defaults
          </div>
          <ul className="mt-3 space-y-3 text-sm leading-6 text-[rgb(var(--muted))]">
            <li>Starts immediately after you confirm it.</li>
            <li>Ends 7 days later.</li>
            <li>Created as a private quest.</li>
          </ul>
        </Panel>

        <Panel className="p-5 lg:col-span-2">
          <div className="flex items-center gap-2 text-xs font-semibold text-[rgb(var(--muted))]">
            <ShieldCheck className="h-4 w-4" />
            What happens next
          </div>
          <p className="mt-3 text-sm leading-6 text-[rgb(var(--muted))]">
            {acceptedQuest
              ? "This suggestion already became a quest in your account."
              : "Open the review page to adjust title, description, or category before creating the quest."}
          </p>
        </Panel>
      </div>
    </PageShell>
  );
}
