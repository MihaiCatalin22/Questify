import { useCallback, useEffect, useRef, useState } from "react";
import { toast } from "react-hot-toast";
import { ArrowLeft, Bot, ShieldCheck, Sparkles } from "lucide-react";
import { Link } from "react-router-dom";

import type { CoachSettingsDTO, UpdateCoachSettingsInput } from "../../api/users";
import { useAuthContext } from "../../contexts/useAuthContext";
import { useCoachSettings, useUpdateCoachSettings } from "../../hooks/useCoach";
import {
  Badge,
  Button,
  ErrorState,
  FieldLabel,
  LoadingState,
  PageHeader,
  PageShell,
  Panel,
  TextArea,
} from "../../components/ui";
import { setStoredCoachResponse } from "./coachSession";
import { readIncludeRecentHistoryPreference, writeIncludeRecentHistoryPreference } from "./coachPreferences";

type SettingsSaveState = "idle" | "saving" | "saved" | "error";

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

export default function CoachSettingsPage() {
  const { user } = useAuthContext();
  const settingsQ = useCoachSettings();
  const saveSettingsM = useUpdateCoachSettings();

  const [settingsForm, setSettingsForm] = useState<UpdateCoachSettingsInput>({
    aiCoachEnabled: false,
    coachGoal: "",
  });
  const [includeRecentHistory, setIncludeRecentHistory] = useState(true);
  const [settingsSaveState, setSettingsSaveState] = useState<SettingsSaveState>("idle");
  const autosaveTimerRef = useRef<ReturnType<typeof window.setTimeout> | null>(null);

  useEffect(() => {
    setIncludeRecentHistory(readIncludeRecentHistoryPreference(user?.id));
  }, [user?.id]);

  useEffect(() => {
    if (!settingsQ.data) return;
    setSettingsForm(mapSettingsToForm(settingsQ.data));
  }, [settingsQ.data]);

  const persistedGoal = (settingsQ.data?.coachGoal ?? "").trim();
  const draftGoal = (settingsForm.coachGoal ?? "").trim();
  const persistedOptIn = Boolean(settingsQ.data?.aiCoachEnabled);
  const draftOptIn = Boolean(settingsForm.aiCoachEnabled);
  const settingsDirty = draftOptIn !== persistedOptIn || draftGoal !== persistedGoal;

  const persistSettings = useCallback(
    async (showToastOnSuccess: boolean, override?: UpdateCoachSettingsInput) => {
      const previousGoal = (settingsQ.data?.coachGoal ?? "").trim();
      const payload = {
        aiCoachEnabled: Boolean(override?.aiCoachEnabled ?? settingsForm.aiCoachEnabled),
        coachGoal: override?.coachGoal ?? settingsForm.coachGoal ?? "",
      };

      setSettingsSaveState("saving");
      const saved = await saveSettingsM.mutateAsync({
        aiCoachEnabled: payload.aiCoachEnabled,
        coachGoal: payload.coachGoal,
      });

      const savedGoal = (saved.coachGoal ?? "").trim();
      if (!saved.aiCoachEnabled || savedGoal !== previousGoal) {
        setStoredCoachResponse(null);
      }

      setSettingsForm(mapSettingsToForm(saved));
      setSettingsSaveState("saved");
      if (showToastOnSuccess) {
        toast.success("Coach settings saved");
      }
      return saved;
    },
    [saveSettingsM, settingsForm.aiCoachEnabled, settingsForm.coachGoal, settingsQ.data?.coachGoal]
  );

  useEffect(() => {
    if (!settingsQ.data) return;
    if (!settingsDirty) return;
    if (saveSettingsM.isPending) return;

    if (autosaveTimerRef.current !== null) {
      clearTimeout(autosaveTimerRef.current);
    }

    autosaveTimerRef.current = window.setTimeout(() => {
      void persistSettings(false).catch((error: unknown) => {
        setSettingsSaveState("error");
        toast.error(extractApiMessage(error, "Failed to save coach settings"));
      });
    }, 700);

    return () => {
      if (autosaveTimerRef.current !== null) {
        clearTimeout(autosaveTimerRef.current);
        autosaveTimerRef.current = null;
      }
    };
  }, [persistSettings, saveSettingsM.isPending, settingsDirty, settingsQ.data]);

  useEffect(() => {
    if (settingsSaveState !== "saved") return;
    const timer = window.setTimeout(() => setSettingsSaveState("idle"), 1600);
    return () => clearTimeout(timer);
  }, [settingsSaveState]);

  async function onSaveNow() {
    try {
      await persistSettings(true);
    } catch (error: unknown) {
      setSettingsSaveState("error");
      toast.error(extractApiMessage(error, "Failed to save coach settings"));
    }
  }

  if (settingsQ.isLoading) return <LoadingState label="Loading coach settings..." />;
  if (settingsQ.isError) {
    return <ErrorState message={extractApiMessage(settingsQ.error, "Failed to load coach settings")} />;
  }

  return (
    <PageShell className="max-w-5xl">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Link
          to="/coach"
          className="inline-flex items-center gap-2 text-sm text-[rgb(var(--muted))] hover:text-[rgb(var(--text))]"
        >
          <ArrowLeft className="h-4 w-4" />
          Back to coach
        </Link>

        <div className="flex flex-wrap items-center gap-2">
          {settingsSaveState === "saving" ? <Badge>Saving...</Badge> : null}
          {settingsSaveState === "saved" ? <Badge tone="success">Saved</Badge> : null}
          {settingsSaveState === "error" ? <Badge tone="danger">Save failed</Badge> : null}
        </div>
      </div>

      <PageHeader
        title="Coach Settings"
        description="These settings are saved for your account. The history preference is kept on this device for this signed-in user."
      />

      <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_320px]">
        <Panel className="p-5">
          <div className="space-y-5">
            <label className="flex items-start gap-3 rounded-lg border border-[rgb(var(--border-soft))] bg-[rgba(var(--bg-soft),0.58)] p-4">
              <input
                type="checkbox"
                checked={draftOptIn}
                onChange={(event) => {
                  const next = {
                    ...settingsForm,
                    aiCoachEnabled: event.target.checked,
                  };
                  setSettingsSaveState("idle");
                  setSettingsForm(next);
                  if (autosaveTimerRef.current !== null) {
                    clearTimeout(autosaveTimerRef.current);
                    autosaveTimerRef.current = null;
                  }
                  void persistSettings(false, next).catch((error: unknown) => {
                    setSettingsSaveState("error");
                    toast.error(extractApiMessage(error, "Failed to save coach settings"));
                  });
                }}
                className="mt-1 h-4 w-4 rounded border-[rgb(var(--border))] bg-[rgb(var(--bg-soft))]"
              />
              <div>
                <div className="text-sm font-medium text-[rgb(var(--text))]">Enable AI Coach</div>
                <div className="mt-1 text-xs leading-6 text-[rgb(var(--muted))]">
                  Turns suggestion generation on for your account and keeps your saved goal active across refreshes.
                </div>
              </div>
            </label>

            <div>
              <FieldLabel>Coach goal</FieldLabel>
              <TextArea
                value={settingsForm.coachGoal ?? ""}
                onChange={(event) =>
                  setSettingsForm((current) => {
                    setSettingsSaveState("idle");
                    return {
                      ...current,
                      coachGoal: event.target.value,
                    };
                  })
                }
                onBlur={() => {
                  if (autosaveTimerRef.current !== null) {
                    clearTimeout(autosaveTimerRef.current);
                    autosaveTimerRef.current = null;
                  }
                  if (settingsDirty && !saveSettingsM.isPending) {
                    void persistSettings(false).catch((error: unknown) => {
                      setSettingsSaveState("error");
                      toast.error(extractApiMessage(error, "Failed to save coach settings"));
                    });
                  }
                }}
                rows={7}
                placeholder="Example: Improve my school grades by getting more consistent with math drills and short history review sessions."
              />
            </div>

            <label className="flex items-start gap-3 rounded-lg border border-[rgb(var(--border-soft))] bg-[rgba(var(--bg-soft),0.58)] p-4">
              <input
                type="checkbox"
                checked={includeRecentHistory}
                onChange={(event) => {
                  const nextValue = event.target.checked;
                  setIncludeRecentHistory(nextValue);
                  writeIncludeRecentHistoryPreference(user?.id, nextValue);
                }}
                className="mt-1 h-4 w-4 rounded border-[rgb(var(--border))] bg-[rgb(var(--bg-soft))]"
              />
              <div>
                <div className="text-sm font-medium text-[rgb(var(--text))]">Use recent quest history</div>
                <div className="mt-1 text-xs leading-6 text-[rgb(var(--muted))]">
                  Lets the coach use recent completions and active quest titles. This preference stays on this device
                  for your account.
                </div>
              </div>
            </label>
          </div>

          <div className="mt-5 flex flex-wrap items-center justify-end gap-3 border-t border-[rgb(var(--border-soft))] pt-5">
            <Link to="/coach" className="btn btn-secondary">
              Back to coach
            </Link>
            <Button
              onClick={onSaveNow}
              disabled={saveSettingsM.isPending}
              variant="primary"
            >
              {saveSettingsM.isPending ? "Saving..." : "Save now"}
            </Button>
          </div>
        </Panel>

        <aside className="space-y-4">
          <Panel className="p-5">
            <div className="flex items-center gap-2 text-xs font-semibold text-[rgb(var(--muted))]">
              <Bot className="h-4 w-4" />
              Current state
            </div>
            <div className="mt-4 space-y-3 text-sm leading-6 text-[rgb(var(--muted))]">
              <div>AI coach: {draftOptIn ? "Enabled" : "Disabled"}</div>
              <div>Goal saved: {draftGoal ? "Yes" : "No"}</div>
              <div>Recent history: {includeRecentHistory ? "Included" : "Excluded"}</div>
            </div>
          </Panel>

          <Panel className="p-5">
            <div className="flex items-center gap-2 text-xs font-semibold text-[rgb(var(--muted))]">
              <Sparkles className="h-4 w-4" />
              Quality note
            </div>
            <p className="mt-3 text-sm leading-6 text-[rgb(var(--muted))]">
              Multi-part goals work better when they name the actual sub-areas you care about. Be explicit about the
              skills, subjects, or routines you want the coach to spread suggestions across.
            </p>
          </Panel>

          <Panel className="p-5">
            <div className="flex items-center gap-2 text-xs font-semibold text-[rgb(var(--muted))]">
              <ShieldCheck className="h-4 w-4" />
              Privacy notes
            </div>
            <ul className="mt-3 space-y-2 text-sm leading-6 text-[rgb(var(--muted))]">
              <li>Coach generation uses saved goal text and minimal quest context.</li>
              <li>Proof uploads and raw media stay out of the coach path.</li>
              <li>Server-side validation still enforces the returned quest structure.</li>
            </ul>
          </Panel>
        </aside>
      </div>
    </PageShell>
  );
}
