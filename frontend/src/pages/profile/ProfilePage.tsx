import { useEffect, useMemo, useRef, useState } from "react";
import { toast } from "react-hot-toast";
import { useAuth } from "react-oidc-context";

import { useDeleteMe, useMe, useUpdateMe } from "../../hooks/useUsers";
import { QuestsApi } from "../../api/quests";
import { SubmissionsApi } from "../../api/submissions";
import { UsersApi, type ExportJobDTO, type ExportJobStatus, type UpdateMeInput } from "../../api/users";

const EXPORT_STORAGE_KEY = "questify:lastExportJob";

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

export default function ProfilePage() {
  const auth = useAuth();

  const meQ = useMe();
  const upd = useUpdateMe();
  const del = useDeleteMe();

  const me = meQ.data;

  const [form, setForm] = useState<UpdateMeInput>({
    displayName: "",
    bio: "",
  });

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
  } | null>(null);

  // GDPR export job state
  const [exportJob, setExportJob] = useState<ExportJobDTO | null>(null);
  const [exportStarting, setExportStarting] = useState(false);
  const lastToastStatus = useRef<ExportJobStatus | null>(null);
  const pollTimer = useRef<number | null>(null);

  // Keep profile form in sync
  useEffect(() => {
    if (!me) return;
    setForm({
      displayName: me.displayName ?? "",
      bio: me.bio ?? "",
    });
  }, [me?.id]);

  // Persist export job to localStorage so leaving Profile doesn't "lose" it
  useEffect(() => {
    if (!exportJob) {
      localStorage.removeItem(EXPORT_STORAGE_KEY);
      return;
    }
    try {
      localStorage.setItem(EXPORT_STORAGE_KEY, JSON.stringify(exportJob));
    } catch {
      // ignore storage quota / blocked storage
    }
  }, [exportJob]);

  // Restore last export job on mount (and refresh from backend)
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
      } catch (e: any) {
        const status = e?.response?.status;
        // If it's not accessible / not found, clear it so the UI doesn't get stuck
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
    } catch (e: any) {
      toast.error(e?.message ?? "Failed to update profile");
    }
  }

  async function startExport() {
    try {
      setExportStarting(true);

      // Optional: clear previous job from state (keeps UI clean)
      // (ZIP is still accessible if you stored its jobId elsewhere; we store only latest)
      // setExportJob(null);

      const created = await UsersApi.requestExportJob();

      const job: ExportJobDTO = {
        jobId: created.jobId,
        status: created.status,
        expiresAt: created.expiresAt,
      };

      setExportJob(job);
      lastToastStatus.current = created.status;
      toast.success("Export job started. We'll download the ZIP when it's ready.");
    } catch (e: any) {
      toast.error(e?.message ?? "Failed to start export");
    } finally {
      setExportStarting(false);
    }
  }

  async function downloadExport(jobId: string) {
    try {
      const url = await UsersApi.getExportDownloadUrl(jobId);
      window.location.href = url;
    } catch (e: any) {
      const status = e?.response?.status;
      const data = e?.response?.data;

      if (status === 409) {
        // could be not ready OR failed
        if (data?.reason) {
          toast.error(String(data.reason));
          return;
        }
        if (data?.error) {
          toast.error(String(data.error));
          return;
        }
        toast("Export not ready yet — try again in a moment.");
        return;
      }

      if (status === 410) {
        toast.error("Export expired. Please request a new one.");
        return;
      }

      toast.error(e?.message ?? "Failed to download export");
    }
  }

  // Poll export status while RUNNING/PENDING
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

        if (next.status === "COMPLETED") {
          if (pollTimer.current) {
            window.clearInterval(pollTimer.current);
            pollTimer.current = null;
          }
          toast.success("Export ready! Downloading…");
          await downloadExport(jobId);
        }
      } catch (e: any) {
        toast.error(e?.message ?? "Failed to check export status");
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
      toast.success("Account deleted. Signing you out…");

      try {
        await auth.signoutRedirect();
      } catch {
        await auth.removeUser();
        window.location.href = "/";
      }
    } catch (e: any) {
      toast.error(e?.message ?? "Deletion failed");
    } finally {
      setDeleteModalOpen(false);
    }
  }

  async function loadSummary() {
    try {
      setLoadingSummary(true);

      const [active, archived, subs] = await Promise.all([
        QuestsApi.mineOrParticipatingSummary(false),
        QuestsApi.mineOrParticipatingSummary(true),
        SubmissionsApi.mineSummary(),
      ]);

      setSummary({
        accountAgeDays: accountAge,
        questsTotal: Number(active.questsTotal ?? 0) + Number(archived.questsTotal ?? 0),
        questsCompleted: Number(active.questsCompleted ?? 0) + Number(archived.questsCompleted ?? 0),
        submissionsTotal: Number(subs.submissionsTotal ?? 0),
      });
    } catch (e: any) {
      toast.error(e?.message ?? "Failed to load summary");
    } finally {
      setLoadingSummary(false);
    }
  }

  useEffect(() => {
    if (!me) return;
    loadSummary();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [me?.id]);

  if (meQ.isLoading) return <div className="p-6 opacity-70">Loading…</div>;
  if (meQ.isError)
    return (
      <div className="p-6 text-red-600">
        {(meQ.error as any)?.message || "Failed to load profile"}
      </div>
    );

  return (
    <div className="p-6 space-y-6 max-w-4xl">
      {deleteModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="w-full max-w-lg rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#0f1115] p-5 space-y-4">
            <div className="text-lg font-semibold text-red-700 dark:text-red-300">
              Confirm account deletion
            </div>

            <div className="text-sm opacity-80 space-y-2">
              <p>You are about to permanently delete your account.</p>
              <ul className="list-disc pl-5 space-y-1">
                <li>You will be signed out immediately.</li>
                <li>You won’t be able to log in again with this account.</li>
                <li>If you want to use Questify again, you will need a new account.</li>
              </ul>
              <p className="text-xs opacity-70">This action is irreversible.</p>
            </div>

            <div className="flex items-center justify-end gap-2">
              <button
                onClick={() => setDeleteModalOpen(false)}
                className="rounded-2xl border px-4 py-2 text-sm shadow hover:shadow-md
                           bg-white dark:bg-[#0f1115] border-slate-200 dark:border-slate-800"
              >
                Cancel
              </button>
              <button
                onClick={confirmDelete}
                disabled={del.isPending}
                className="rounded-2xl border px-4 py-2 text-sm shadow hover:shadow-md
                           border-red-300 dark:border-red-800 text-red-700 dark:text-red-300 disabled:opacity-60"
              >
                {del.isPending ? "Deleting…" : "Yes, delete my account"}
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-slate-900 dark:text-slate-100">Profile</h1>
        {isDeleted && (
          <span className="text-xs px-2 py-1 rounded-full border border-red-300 text-red-700 dark:border-red-800 dark:text-red-300">
            Deleted
          </span>
        )}
      </div>

      <div className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#0f1115] p-5 space-y-4">
        <div className="flex items-start justify-between gap-4">
          <div>
            <div className="text-lg font-semibold">{me?.username || "—"}</div>
            <div className="text-sm opacity-70">{me?.email || "—"}</div>
          </div>

          <div className="text-right text-sm opacity-70">
            <div className="text-xs opacity-70">Account age</div>
            <div>{accountAge == null ? "—" : `${accountAge} day(s)`}</div>
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label className="block text-xs opacity-70">Display name</label>
            <input
              value={form.displayName ?? ""}
              onChange={(e) => setForm((s) => ({ ...s, displayName: e.target.value }))}
              disabled={isDeleted}
              className="mt-1 w-full rounded-2xl border px-3 py-2 text-sm bg-white dark:bg-[#0b0d12]
                         border-slate-200 dark:border-slate-800 disabled:opacity-60"
            />
          </div>
        </div>

        <div>
          <label className="block text-xs opacity-70">Bio</label>
          <textarea
            value={form.bio ?? ""}
            onChange={(e) => setForm((s) => ({ ...s, bio: e.target.value }))}
            disabled={isDeleted}
            rows={4}
            className="mt-1 w-full rounded-2xl border px-3 py-2 text-sm bg-white dark:bg-[#0b0d12]
                       border-slate-200 dark:border-slate-800 disabled:opacity-60"
          />
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <button
            onClick={onSave}
            disabled={upd.isPending || isDeleted}
            className="rounded-2xl border px-4 py-2 text-sm shadow hover:shadow-md
                       bg-white dark:bg-[#0f1115] border-slate-200 dark:border-slate-800 disabled:opacity-60"
          >
            {upd.isPending ? "Saving…" : "Save changes"}
          </button>

          <button
            onClick={startExport}
            disabled={exportStarting || isDeleted || exportJob?.status === "RUNNING" || exportJob?.status === "PENDING"}
            className="rounded-2xl border px-4 py-2 text-sm shadow hover:shadow-md
                       bg-white dark:bg-[#0f1115] border-slate-200 dark:border-slate-800 disabled:opacity-60"
          >
            {exportStarting
              ? "Starting export…"
              : exportJob?.status === "RUNNING" || exportJob?.status === "PENDING"
                ? "Export in progress…"
                : "Request data export (ZIP)"}
          </button>

          {exportJob?.status === "COMPLETED" && exportJob.jobId && (
            <button
              onClick={() => downloadExport(exportJob.jobId)}
              disabled={isDeleted}
              className="rounded-2xl border px-4 py-2 text-sm shadow hover:shadow-md
                         bg-white dark:bg-[#0f1115] border-slate-200 dark:border-slate-800 disabled:opacity-60"
            >
              Download export (ZIP)
            </button>
          )}
        </div>

        <div className="text-xs opacity-70 space-y-1">
          <div>Created: {formatDate(me?.createdAt)}</div>
          <div>Updated: {formatDate(me?.updatedAt)}</div>

          {exportJob && (
            <div className="pt-1 space-y-1">
              <div>
                Export status: <span className="font-medium">{exportJob.status}</span>
              </div>
              <div>Export jobId: <span className="font-mono">{exportJob.jobId}</span></div>
              {exportJob.expiresAt && <div>Export expires: {formatDate(exportJob.expiresAt)}</div>}
              {exportJob.createdAt && <div>Export created: {formatDate(exportJob.createdAt)}</div>}
              {exportJob.lastProgressAt && <div>Last progress: {formatDate(exportJob.lastProgressAt)}</div>}

              {exportJob.status === "FAILED" && (
                <div className="text-xs text-red-700 dark:text-red-300">
                  {exportJob.failureReason ? (
                    <div>Reason: {exportJob.failureReason}</div>
                  ) : exportJob.missingParts?.length ? (
                    <div>Missing parts: {exportJob.missingParts.join(", ")}</div>
                  ) : null}
                </div>
              )}
            </div>
          )}
        </div>

        {loadingSummary && <div className="text-xs opacity-70">Loading summary…</div>}

        {summary && (
          <div className="rounded-2xl border border-slate-200 dark:border-slate-800 p-4">
            <div className="text-sm font-semibold mb-2">Summary</div>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-sm">
              <div>
                <div className="text-xs opacity-70">Quests participated</div>
                <div className="text-lg">{summary.questsTotal}</div>
              </div>
              <div>
                <div className="text-xs opacity-70">Quests completed</div>
                <div className="text-lg">{summary.questsCompleted}</div>
              </div>
              <div>
                <div className="text-xs opacity-70">Submissions</div>
                <div className="text-lg">{summary.submissionsTotal}</div>
              </div>
            </div>
          </div>
        )}
      </div>

      <div className="rounded-2xl border border-red-200 dark:border-red-900 bg-white dark:bg-[#0f1115] p-5 space-y-3">
        <h2 className="text-lg font-semibold text-red-700 dark:text-red-300">Danger zone</h2>

        <p className="text-sm opacity-80">
          Deleting your account will permanently disable your login and remove/anonymize your data (submissions,
          participation/completion records, and uploaded proofs).
        </p>

        <p className="text-xs opacity-70">
          This action is irreversible. If you want to use Questify again, you will need to create a new account.
        </p>

        <div className="flex flex-col sm:flex-row sm:items-center gap-2">
          <input
            value={confirmText}
            onChange={(e) => setConfirmText(e.target.value)}
            placeholder='Type "DELETE" to confirm'
            disabled={isDeleted}
            className="w-full sm:w-72 rounded-2xl border px-3 py-2 text-sm bg-white dark:bg-[#0b0d12]
                       border-red-200 dark:border-red-900 disabled:opacity-60"
          />
          <button
            onClick={onDeleteClick}
            disabled={del.isPending || isDeleted}
            className="rounded-2xl border px-4 py-2 text-sm shadow hover:shadow-md
                       border-red-300 dark:border-red-800 text-red-700 dark:text-red-300 disabled:opacity-60"
          >
            Delete my account
          </button>
        </div>

        {isDeleted && (
          <div className="text-xs text-red-700 dark:text-red-300">
            Deleted at: {formatDate(me?.deletedAt)}
          </div>
        )}
      </div>
    </div>
  );
}
