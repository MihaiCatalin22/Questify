import { useEffect, useMemo, useRef, useState } from "react";
import { toast } from "react-hot-toast";
import { useAuth } from "react-oidc-context";
import { Link } from "react-router-dom";
import { Bot, Download, Flame, ShieldAlert, Trophy, UserRound } from "lucide-react";

import { useDeleteMe, useMe, useUpdateMe } from "../../hooks/useUsers";
import { QuestsApi } from "../../api/quests";
import { SubmissionsApi } from "../../api/submissions";
import { StreaksApi, type StreakSummary } from "../../api/streaks";
import { UsersApi, type ExportJobDTO, type ExportJobStatus, type UpdateMeInput } from "../../api/users";
import {
  Badge,
  Button,
  ErrorState,
  FieldLabel,
  LoadingState,
  PageHeader,
  PageShell,
  Panel,
  StatusBadge,
  TextArea,
  TextInput,
} from "../../components/ui";

const EXPORT_STORAGE_KEY = "questify:lastExportJob";

type ApiErrorPayload = {
  message?: string;
  error?: string;
  reason?: string;
};

type HttpLikeError = {
  message?: string;
  response?: {
    status?: number;
    data?: ApiErrorPayload;
  };
};

function formatDate(iso?: string | null) {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return String(iso);
  return d.toLocaleString();
}

function ageDays(createdAt?: string | null) {
  if (!createdAt) return null;
  const t = Date.parse(createdAt);
  if (Number.isNaN(t)) return null;
  const days = Math.floor((Date.now() - t) / 86_400_000);
  return Math.max(0, days);
}

function isTerminalStatus(s: ExportJobStatus) {
  return s === "COMPLETED" || s === "FAILED" || s === "EXPIRED";
}

function safeParseStoredJob(raw: string | null): ExportJobDTO | null {
  if (!raw) return null;
  try {
    const obj = JSON.parse(raw);
    if (!obj?.jobId) return null;
    return obj as ExportJobDTO;
  } catch {
    return null;
  }
}

function isHttpLikeError(error: unknown): error is HttpLikeError {
  return typeof error === "object" && error !== null;
}

function extractApiMessage(error: unknown, fallback: string) {
  if (!isHttpLikeError(error)) return fallback;

  const responseData = error.response?.data;
  if (typeof responseData?.message === "string" && responseData.message.trim()) {
    return responseData.message;
  }
  if (typeof responseData?.error === "string" && responseData.error.trim()) {
    return responseData.error;
  }
  if (typeof responseData?.reason === "string" && responseData.reason.trim()) {
    return responseData.reason;
  }
  if (typeof error.message === "string" && error.message.trim()) {
    return error.message;
  }

  return fallback;
}

function getErrorStatus(error: unknown) {
  if (!isHttpLikeError(error)) return undefined;
  return error.response?.status;
}

export default function ProfilePage() {
  const auth = useAuth();

  const meQ = useMe();
  const upd = useUpdateMe();
  const del = useDeleteMe();

  const me = meQ.data;

  const [form, setForm] = useState<UpdateMeInput>({ displayName: "", bio: "" });
  const [confirmText, setConfirmText] = useState("");
  const [deleteModalOpen, setDeleteModalOpen] = useState(false);

  const isDeleted = !!me?.deletedAt;
  const accountAge = useMemo(() => ageDays(me?.createdAt ?? null), [me?.createdAt]);

  const [loadingSummary, setLoadingSummary] = useState(false);
  const [summary, setSummary] = useState<{
    accountAgeDays: number | null;
    questsTotal: number;
    questsCompleted: number;
    submissionsTotal: number;
    streak: StreakSummary | null;
  } | null>(null);

  const [exportJob, setExportJob] = useState<ExportJobDTO | null>(null);
  const [exportStarting, setExportStarting] = useState(false);

  const lastToastStatus = useRef<ExportJobStatus | null>(null);
  const pollTimer = useRef<number | null>(null);

  // avoid spamming download attempts
  const lastSoftDownloadAttemptAt = useRef<number>(0);

  useEffect(() => {
    if (!me) return;
    setForm({
      displayName: me.displayName ?? "",
      bio: me.bio ?? "",
    });
  }, [me]);

  // persist export job
  useEffect(() => {
    if (!exportJob) {
      localStorage.removeItem(EXPORT_STORAGE_KEY);
      return;
    }
    try {
      localStorage.setItem(EXPORT_STORAGE_KEY, JSON.stringify(exportJob));
    } catch {
      // ignore
    }
  }, [exportJob]);

  // restore last export job on mount
  useEffect(() => {
    if (!auth.isAuthenticated) return;

    const stored = safeParseStoredJob(localStorage.getItem(EXPORT_STORAGE_KEY));
    if (!stored?.jobId) return;

    setExportJob(stored);
    lastToastStatus.current = stored.status ?? null;

    (async () => {
      try {
        const fresh = await UsersApi.getExportJob(stored.jobId);
        setExportJob((prev) => ({ ...(prev ?? stored), ...fresh }));
      } catch (error: unknown) {
        const status = getErrorStatus(error);
        if (status === 403 || status === 404) {
          localStorage.removeItem(EXPORT_STORAGE_KEY);
          setExportJob(null);
        }
      }
    })();
  }, [auth.isAuthenticated]);

  async function onSave() {
    try {
      await upd.mutateAsync({
        displayName: (form.displayName ?? "").trim() || null,
        bio: (form.bio ?? "").trim() || null,
      });
      toast.success("Profile updated");
    } catch (error: unknown) {
      toast.error(extractApiMessage(error, "Failed to update profile"));
    }
  }

  async function startExport() {
    try {
      setExportStarting(true);

      const created = await UsersApi.requestExportJob();
      const job: ExportJobDTO = {
        jobId: created.jobId,
        status: created.status,
        expiresAt: created.expiresAt,
      };

      setExportJob(job);
      lastToastStatus.current = created.status;
      toast.success("Export job started. We'll download the ZIP when it is ready.");
    } catch (error: unknown) {
      toast.error(extractApiMessage(error, "Failed to start export"));
    } finally {
      setExportStarting(false);
    }
  }

  async function downloadExport(jobId: string, quiet409: boolean = false) {
    try {
      const url = await UsersApi.getExportDownloadUrl(jobId);
      window.location.href = url;
      return true;
    } catch (error: unknown) {
      const status = getErrorStatus(error);
      const data = isHttpLikeError(error) ? error.response?.data : undefined;

      if (status === 409) {
        if (quiet409) return false;

        if (data?.reason) {
          toast.error(String(data.reason));
          return false;
        }
        if (data?.error) {
          toast.error(String(data.error));
          return false;
        }
        toast("Export not ready yet — try again in a moment.");
        return false;
      }

      if (status === 410) {
        toast.error("Export expired. Please request a new one.");
        return false;
      }

      toast.error(extractApiMessage(error, "Failed to download export"));
      return false;
    }
  }

  // poll export status
  useEffect(() => {
    const jobId = exportJob?.jobId;
    const status = exportJob?.status;

    if (!jobId) return;

    if (pollTimer.current) {
      window.clearInterval(pollTimer.current);
      pollTimer.current = null;
    }

    if (!status || isTerminalStatus(status)) return;

    pollTimer.current = window.setInterval(async () => {
      try {
        const next = await UsersApi.getExportJob(jobId);

        setExportJob((prev) => ({
          ...(prev ?? { jobId, status: next.status }),
          ...next,
        }));

        // toast transitions
        if (lastToastStatus.current !== next.status) {
          lastToastStatus.current = next.status;

          if (next.status === "FAILED") {
            const msg =
              next.failureReason
                ? `Export failed: ${next.failureReason}`
                : next.missingParts?.length
                  ? `Export failed (missing: ${next.missingParts.join(", ")})`
                  : "Export failed.";
            toast.error(msg);
          }

          if (next.status === "EXPIRED") toast.error("Export expired. Please request a new one.");
        }

        // normal completion path
        if (next.status === "COMPLETED") {
          if (pollTimer.current) {
            window.clearInterval(pollTimer.current);
            pollTimer.current = null;
          }
          toast.success("Export ready. Downloading...");
          await downloadExport(jobId);
          return;
        }

        // soft attempt: if parts are done but status hasn't flipped yet
        if (next.status === "RUNNING" && (next.missingParts?.length ?? 0) === 0) {
          const now = Date.now();
          if (now - lastSoftDownloadAttemptAt.current > 8000) {
            lastSoftDownloadAttemptAt.current = now;
            await downloadExport(jobId, true); // quiet 409
          }
        }
      } catch (error: unknown) {
        toast.error(extractApiMessage(error, "Failed to check export status"));
        if (pollTimer.current) {
          window.clearInterval(pollTimer.current);
          pollTimer.current = null;
        }
      }
    }, 4000);

    return () => {
      if (pollTimer.current) {
        window.clearInterval(pollTimer.current);
        pollTimer.current = null;
      }
    };
  }, [exportJob?.jobId, exportJob?.status]);

  function onDeleteClick() {
    if (confirmText.trim().toUpperCase() !== "DELETE") {
      toast.error('Type "DELETE" to confirm');
      return;
    }
    setDeleteModalOpen(true);
  }

  async function confirmDelete() {
    try {
      await del.mutateAsync();
      toast.success("Account deleted. Signing you out...");

      try {
        await auth.signoutRedirect();
      } catch {
        await auth.removeUser();
        window.location.href = "/";
      }
    } catch (error: unknown) {
      toast.error(extractApiMessage(error, "Deletion failed"));
    } finally {
      setDeleteModalOpen(false);
    }
  }

  async function loadSummary() {
    try {
      setLoadingSummary(true);

      const [active, archived, subs, streak] = await Promise.all([
        QuestsApi.mineOrParticipatingSummary(false),
        QuestsApi.mineOrParticipatingSummary(true),
        SubmissionsApi.mineSummary(),
        StreaksApi.mine().catch(() => null),
      ]);

      setSummary({
        accountAgeDays: accountAge,
        questsTotal: Number(active.questsTotal ?? 0) + Number(archived.questsTotal ?? 0),
        questsCompleted: Number(active.questsCompleted ?? 0) + Number(archived.questsCompleted ?? 0),
        submissionsTotal: Number(subs.submissionsTotal ?? 0),
        streak,
      });
    } catch (error: unknown) {
      toast.error(extractApiMessage(error, "Failed to load summary"));
    } finally {
      setLoadingSummary(false);
    }
  }

  useEffect(() => {
    if (!me) return;
    loadSummary();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [me?.id]);

  if (meQ.isLoading) return <LoadingState label="Loading profile..." />;
  if (meQ.isError) {
    return (
      <ErrorState message={extractApiMessage(meQ.error, "Failed to load profile")} />
    );
  }

  return (
    <PageShell className="max-w-5xl">
      {deleteModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <Panel className="w-full max-w-lg p-5 space-y-4">
            <div className="flex items-center gap-2 text-lg font-semibold text-red-200">
              <ShieldAlert className="h-5 w-5" />
              Confirm account deletion
            </div>

            <div className="space-y-2 text-sm leading-6 text-[rgb(var(--muted))]">
              <p>You are about to permanently delete your account.</p>
              <ul className="list-disc pl-5 space-y-1">
                <li>You will be signed out immediately.</li>
                <li>You will not be able to log in again with this account.</li>
                <li>If you want to use Questify again, you will need a new account.</li>
              </ul>
              <p className="text-xs text-[rgb(var(--faint))]">This action is irreversible.</p>
            </div>

            <div className="flex items-center justify-end gap-2">
              <Button
                onClick={() => setDeleteModalOpen(false)}
              >
                Cancel
              </Button>
              <Button
                onClick={confirmDelete}
                disabled={del.isPending}
                variant="danger"
              >
                {del.isPending ? "Deleting..." : "Yes, delete my account"}
              </Button>
            </div>
          </Panel>
        </div>
      )}

      <PageHeader
        title="Profile"
        description="Manage your profile details, data export, AI Coach access, and account deletion."
        actions={isDeleted ? <Badge tone="danger">Deleted</Badge> : null}
      />

      <Panel className="p-5 space-y-5">
        <div className="flex items-start justify-between gap-4">
          <div className="flex min-w-0 items-start gap-3">
            <div className="grid h-11 w-11 shrink-0 place-items-center rounded-lg border border-[rgb(var(--border-soft))] bg-[rgba(var(--accent),0.12)] text-[rgb(var(--accent))]">
              <UserRound className="h-5 w-5" />
            </div>
            <div className="min-w-0">
            <div className="text-lg font-semibold">{me?.username || "—"}</div>
              <div className="truncate text-sm text-[rgb(var(--muted))]">{me?.email || "—"}</div>
            </div>
          </div>

          <div className="text-right text-sm text-[rgb(var(--muted))]">
            <div className="text-xs text-[rgb(var(--faint))]">Account age</div>
            <div>{accountAge == null ? "—" : `${accountAge} day(s)`}</div>
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <FieldLabel>Display name</FieldLabel>
            <TextInput
              value={form.displayName ?? ""}
              onChange={(e) => setForm((s) => ({ ...s, displayName: e.target.value }))}
              disabled={isDeleted}
            />
          </div>
        </div>

        <div>
          <FieldLabel>Bio</FieldLabel>
          <TextArea
            value={form.bio ?? ""}
            onChange={(e) => setForm((s) => ({ ...s, bio: e.target.value }))}
            disabled={isDeleted}
            rows={4}
          />
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <Button
            onClick={onSave}
            disabled={upd.isPending || isDeleted}
            variant="primary"
          >
            {upd.isPending ? "Saving..." : "Save changes"}
          </Button>

          <Button
            onClick={startExport}
            disabled={exportStarting || isDeleted || exportJob?.status === "RUNNING" || exportJob?.status === "PENDING"}
          >
            <Download className="h-4 w-4" />
            {exportStarting
              ? "Starting export..."
              : exportJob?.status === "RUNNING" || exportJob?.status === "PENDING"
                ? "Export in progress..."
                : "Request data export (ZIP)"}
          </Button>

          {exportJob?.jobId && (
            <Button
              onClick={() => downloadExport(exportJob.jobId)}
              disabled={isDeleted}
            >
              Download export (if ready)
            </Button>
          )}
        </div>

        <div className="space-y-1 text-xs text-[rgb(var(--faint))]">
          <div>Created: {formatDate(me?.createdAt)}</div>
          <div>Updated: {formatDate(me?.updatedAt)}</div>

          {exportJob && (
            <div className="pt-1 space-y-1">
              <div>
                Export status: <StatusBadge status={exportJob.status} />
              </div>
              <div>
                Export jobId: <span className="font-mono">{exportJob.jobId}</span>
              </div>
              {exportJob.expiresAt && <div>Export expires: {formatDate(exportJob.expiresAt)}</div>}
              {exportJob.createdAt && <div>Export created: {formatDate(exportJob.createdAt)}</div>}
              {exportJob.lastProgressAt && <div>Last progress: {formatDate(exportJob.lastProgressAt)}</div>}
              {!!exportJob.missingParts?.length && <div>Missing parts: {exportJob.missingParts.join(", ")}</div>}

              {exportJob.status === "FAILED" && exportJob.failureReason && (
                <div className="text-xs text-red-300">Reason: {exportJob.failureReason}</div>
              )}
            </div>
          )}
        </div>

        {loadingSummary && <div className="text-xs text-[rgb(var(--faint))]">Loading summary...</div>}

        {summary && (
          <div className="rounded-lg border border-[rgb(var(--border-soft))] bg-[rgba(var(--surface-2),0.45)] p-4">
            <div className="text-sm font-semibold mb-2">Summary</div>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-sm">
              <div>
                <div className="text-xs text-[rgb(var(--faint))]">Quests participated</div>
                <div className="text-lg">{summary.questsTotal}</div>
              </div>
              <div>
                <div className="text-xs text-[rgb(var(--faint))]">Quests completed</div>
                <div className="text-lg">{summary.questsCompleted}</div>
              </div>
              <div>
                <div className="text-xs text-[rgb(var(--faint))]">Submissions</div>
                <div className="text-lg">{summary.submissionsTotal}</div>
              </div>
            </div>
            {summary.streak && (
              <div className="mt-4 grid grid-cols-1 gap-3 border-t border-[rgb(var(--border-soft))] pt-4 text-sm sm:grid-cols-3">
                <div>
                  <div className="flex items-center gap-1 text-xs text-[rgb(var(--faint))]">
                    <Flame className="h-3.5 w-3.5 text-[rgb(var(--accent))]" />
                    Current streak
                  </div>
                  <div className="text-lg">{summary.streak.currentStreak} day(s)</div>
                </div>
                <div>
                  <div className="flex items-center gap-1 text-xs text-[rgb(var(--faint))]">
                    <Trophy className="h-3.5 w-3.5 text-[rgb(var(--accent-2))]" />
                    Level
                  </div>
                  <div className="text-lg">Level {summary.streak.level}</div>
                </div>
                <div>
                  <div className="text-xs text-[rgb(var(--faint))]">XP</div>
                  <div className="text-lg">{summary.streak.totalXp}</div>
                </div>
              </div>
            )}
          </div>
        )}
      </Panel>

      <Panel className="p-5 space-y-3">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div className="flex gap-3">
            <Bot className="mt-1 h-5 w-5 text-[rgb(var(--accent))]" />
            <div>
              <h2 className="text-lg font-semibold">AI Coach</h2>
              <p className="mt-1 text-sm leading-6 text-[rgb(var(--muted))]">
              Open your dedicated coach workspace to manage AI Coach opt-in, set a goal, and generate tailored
              suggestions.
            </p>
            </div>
          </div>

          <Link
            to="/coach"
            className="btn btn-secondary"
          >
            Open AI Coach
          </Link>
        </div>

        <p className="text-xs text-[rgb(var(--faint))]">
          AI Coach uses only minimal recent quest context and never includes proof media or raw uploads.
        </p>
      </Panel>

      <Panel className="border-[rgba(var(--coral),0.45)] p-5 space-y-3">
        <h2 className="text-lg font-semibold text-red-200">Danger zone</h2>

        <p className="text-sm leading-6 text-[rgb(var(--muted))]">
          Deleting your account will permanently disable your login and remove/anonymize your data (submissions,
          participation/completion records, and uploaded proofs).
        </p>

        <p className="text-xs text-[rgb(var(--faint))]">
          This action is irreversible. If you want to use Questify again, you will need to create a new account.
        </p>

        <div className="flex flex-col sm:flex-row sm:items-center gap-2">
          <TextInput
            value={confirmText}
            onChange={(e) => setConfirmText(e.target.value)}
            placeholder='Type "DELETE" to confirm'
            disabled={isDeleted}
            className="sm:w-72"
          />
          <Button
            onClick={onDeleteClick}
            disabled={del.isPending || isDeleted}
            variant="danger"
          >
            Delete my account
          </Button>
        </div>

        {isDeleted && (
          <div className="text-xs text-red-300">
            Deleted at: {formatDate(me?.deletedAt)}
          </div>
        )}
      </Panel>
    </PageShell>
  );
}
