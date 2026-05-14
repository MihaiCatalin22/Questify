import { useMemo, useState } from "react";
import { toast } from "react-hot-toast";
import { ArrowLeft, CheckCircle2, Clock3, ShieldCheck, Sparkles } from "lucide-react";
import { Link, useNavigate, useParams } from "react-router-dom";

import { useAuthContext } from "../../contexts/useAuthContext";
import { useCreateQuest } from "../../hooks/useQuests";
import type { QuestCategory } from "../../types/quest";
import type { VerificationPolicyDTO } from "../../types/quest";
import {
  Badge,
  Button,
  EmptyState,
  FieldLabel,
  PageHeader,
  PageShell,
  Panel,
  SelectInput,
  TextArea,
  TextInput,
} from "../../components/ui";
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

const STOPWORDS = new Set([
  "the", "and", "for", "with", "from", "that", "this", "your", "you", "into", "about", "quest", "review", "learn",
  "practice", "build", "make", "create", "simple", "basic", "quick", "quickly", "daily", "weekly"
]);

function parseSignalLines(value: string): string[] {
  return value
    .split(/\r?\n|,/)
    .map((line) => line.trim())
    .filter((line) => line.length >= 2)
    .slice(0, 20);
}

function extractKeywords(text: string): string[] {
  const matches = (text.toLowerCase().match(/[a-z0-9]{3,}/g) ?? [])
    .filter((token) => !STOPWORDS.has(token));
  return Array.from(new Set(matches)).slice(0, 4);
}

function defaultPolicyFromSuggestion(title: string, description: string): VerificationPolicyDTO {
  const keywords = extractKeywords(`${title} ${description}`);
  const primary = keywords[0] ?? "task";
  const secondary = keywords[1] ?? "progress";
  return {
    requiredEvidence: [
      `${primary} related content`,
      `${secondary} related content`,
    ],
    optionalEvidence: [
      "visible notes or steps",
      "study/work context",
    ],
    disqualifiers: [
      "game hud or menu",
      "unrelated object only",
    ],
    minSupportScore: 0.75,
    taskType: "generic",
  };
}

function plusSevenDaysIso(startIso: string) {
  const start = new Date(startIso);
  return new Date(start.getTime() + 7 * 24 * 60 * 60 * 1000).toISOString();
}

function categoryLabel(category: QuestCategory) {
  return category.replaceAll("_", " ");
}

function difficultyTone(difficulty: "easy" | "medium" | "hard"): "success" | "warning" | "danger" {
  if (difficulty === "hard") return "danger";
  if (difficulty === "medium") return "warning";
  return "success";
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
  const initialPolicy = useMemo(() => defaultPolicyFromSuggestion(suggestion?.title ?? "", suggestion?.description ?? ""), [suggestion?.title, suggestion?.description]);
  const [requiredEvidence, setRequiredEvidence] = useState(initialPolicy.requiredEvidence.join("\n"));
  const [optionalEvidence, setOptionalEvidence] = useState(initialPolicy.optionalEvidence.join("\n"));
  const [disqualifiers, setDisqualifiers] = useState(initialPolicy.disqualifiers.join("\n"));
  const [taskType, setTaskType] = useState(initialPolicy.taskType ?? "generic");
  const [minSupportScore, setMinSupportScore] = useState(String(initialPolicy.minSupportScore ?? 0.75));

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
          description="The review data is missing from this browser session. Go back to the coach page and reopen the suggestion."
        />
      </PageShell>
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
    const parsedRequired = parseSignalLines(requiredEvidence);
    const parsedDisqualifiers = parseSignalLines(disqualifiers);
    const parsedOptional = parseSignalLines(optionalEvidence);
    const minSupport = Number(minSupportScore);
    if (parsedRequired.length < 2) {
      toast.error("Add at least two required evidence signals before creating this quest.");
      return;
    }
    if (parsedDisqualifiers.length < 2) {
      toast.error("Add at least two disqualifier signals before creating this quest.");
      return;
    }
    if (!Number.isFinite(minSupport) || minSupport < 0 || minSupport > 1) {
      toast.error("Min support score must be between 0 and 1.");
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
        verificationPolicy: {
          requiredEvidence: parsedRequired,
          optionalEvidence: parsedOptional,
          disqualifiers: parsedDisqualifiers,
          minSupportScore: minSupport,
          taskType: taskType.trim() || "generic",
        },
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
    <PageShell className="max-w-5xl">
      <Link
        to={`/coach/suggestions/${encodeURIComponent(draft.suggestionKey)}`}
        className="inline-flex items-center gap-2 text-sm text-[rgb(var(--muted))] hover:text-[rgb(var(--text))]"
      >
        <ArrowLeft className="h-4 w-4" />
        Back to details
      </Link>

      <PageHeader
        title="Review before creating"
        description="Adjust the suggestion if needed, then create it as a private quest that starts immediately and lasts seven days."
      />

      <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_320px]">
        <Panel className="p-5">
          <div className="space-y-5">
            <div>
              <FieldLabel>Title</FieldLabel>
              <TextInput value={title} onChange={(event) => setTitle(event.target.value)} />
            </div>

            <div>
              <FieldLabel>Description</FieldLabel>
              <TextArea
                rows={7}
                value={description}
                onChange={(event) => setDescription(event.target.value)}
              />
            </div>

            <div>
              <FieldLabel>Category</FieldLabel>
              <SelectInput
                value={category}
                onChange={(event) => setCategory(event.target.value as QuestCategory)}
              >
                {QUEST_CATEGORIES.map((currentCategory) => (
                  <option key={currentCategory} value={currentCategory}>
                    {categoryLabel(currentCategory)}
                  </option>
                ))}
              </SelectInput>
            </div>

            <div className="space-y-3 rounded-md border border-[rgb(var(--border-soft))] p-4">
              <div className="text-sm font-medium">Verification checklist</div>
              <p className="text-xs text-[rgb(var(--faint))]">
                Confirm this checklist before creating the quest. AI review uses these signals to evaluate proofs.
              </p>

              <div className="grid gap-3 md:grid-cols-2">
                <div>
                  <FieldLabel>Task type</FieldLabel>
                  <TextInput value={taskType} onChange={(event) => setTaskType(event.target.value)} maxLength={80} />
                </div>
                <div>
                  <FieldLabel>Min support score (0-1)</FieldLabel>
                  <TextInput value={minSupportScore} onChange={(event) => setMinSupportScore(event.target.value)} />
                </div>
              </div>

              <div>
                <FieldLabel>Required evidence (min 2)</FieldLabel>
                <TextArea rows={3} value={requiredEvidence} onChange={(event) => setRequiredEvidence(event.target.value)} />
              </div>

              <div>
                <FieldLabel>Optional evidence</FieldLabel>
                <TextArea rows={3} value={optionalEvidence} onChange={(event) => setOptionalEvidence(event.target.value)} />
              </div>

              <div>
                <FieldLabel>Disqualifiers (min 2)</FieldLabel>
                <TextArea rows={3} value={disqualifiers} onChange={(event) => setDisqualifiers(event.target.value)} />
              </div>
            </div>
          </div>

          <div className="mt-5 flex flex-wrap items-center justify-end gap-3 border-t border-[rgb(var(--border-soft))] pt-5">
            <Link
              to={`/coach/suggestions/${encodeURIComponent(draft.suggestionKey)}`}
              className="btn btn-secondary"
            >
              Cancel
            </Link>
            <Button onClick={onCreateQuest} disabled={createQuestM.isPending} variant="primary">
              <CheckCircle2 className="h-4 w-4" />
              {createQuestM.isPending ? "Creating quest..." : "Create quest"}
            </Button>
          </div>
        </Panel>

        <aside className="space-y-4">
          <Panel className="p-5">
            <div className="flex items-center gap-2 text-xs font-semibold text-[rgb(var(--muted))]">
              <Sparkles className="h-4 w-4" />
              Quest signals
            </div>
            <div className="mt-4 flex flex-wrap gap-2">
              <Badge tone={difficultyTone(draft.difficulty)} className="capitalize">
                {draft.difficulty}
              </Badge>
              <Badge>{draft.estimatedMinutes} min</Badge>
              <Badge>Private quest</Badge>
            </div>
          </Panel>

          <Panel className="p-5">
            <div className="flex items-center gap-2 text-xs font-semibold text-[rgb(var(--muted))]">
              <Clock3 className="h-4 w-4" />
              Timing
            </div>
            <ul className="mt-3 space-y-3 text-sm leading-6 text-[rgb(var(--muted))]">
              <li>Starts immediately when you confirm.</li>
              <li>Ends exactly 7 days later.</li>
            </ul>
          </Panel>

          <Panel className="p-5">
            <div className="flex items-center gap-2 text-xs font-semibold text-[rgb(var(--muted))]">
              <ShieldCheck className="h-4 w-4" />
              Why this was suggested
            </div>
            <p className="mt-3 text-sm leading-6 text-[rgb(var(--muted))]">{draft.reason}</p>
          </Panel>
        </aside>
      </div>
    </PageShell>
  );
}
