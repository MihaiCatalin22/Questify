import { useEffect, useMemo, useState } from "react";
import { toast } from "react-hot-toast";
import { useAuth } from "react-oidc-context";

import { useDeleteMe, useExportMe, useMe, useUpdateMe } from "../../hooks/useUsers";
import { QuestsApi } from "../../api/quests";
import { SubmissionsApi } from "../../api/submissions";
import type { UpdateMeInput, UserExportDTO } from "../../api/users";

function downloadJson(obj: unknown, filename: string) {
  const text = JSON.stringify(obj, null, 2);
  const blob = new Blob([text], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

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


export default function ProfilePage() {
  const auth = useAuth();

  const meQ = useMe();
  const exportQ = useExportMe();
  const upd = useUpdateMe();
  const del = useDeleteMe();

  const me = meQ.data;
  const exp: UserExportDTO | undefined = exportQ.data;

  const [form, setForm] = useState<UpdateMeInput>({
    displayName: "",
    bio: "",
  });
  const [confirmText, setConfirmText] = useState("");

  const isDeleted = !!exp?.deletedAt;

  const [loadingSummary, setLoadingSummary] = useState(false);
  const [summary, setSummary] = useState<{
    accountAgeDays: number | null;
    questsTotal: number;
    questsCompleted: number;
    submissionsTotal: number;
  } | null>(null);

  const accountAge = useMemo(() => ageDays(exp?.createdAt ?? null), [exp?.createdAt]);

  useEffect(() => {
    if (!me) return;
    setForm({
        displayName: me.displayName ?? "",
        bio: (me.bio ?? "") as any,
    });
    }, [me?.id]);

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

  async function onExport() {
    try {
      const data = exportQ.data ?? (await exportQ.refetch().then((r) => r.data));
      if (!data) throw new Error("No export data");

      const ts = new Date().toISOString().slice(0, 19).replace(/[:T]/g, "-");
      downloadJson(data, `questify-export-${ts}.json`);
      toast.success("Download started");
    } catch (e: any) {
      toast.error(e?.message ?? "Export failed");
    }
  }

  async function onDelete() {
    if (confirmText.trim().toUpperCase() !== "DELETE") {
      toast.error('Type "DELETE" to confirm');
      return;
    }
    try {
      await del.mutateAsync();
      toast.success("Account data deleted/anonymized");

      try {
        await auth.signoutRedirect();
      } catch {
        await auth.removeUser();
        window.location.href = "/";
      }
    } catch (e: any) {
      toast.error(e?.message ?? "Deletion failed");
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

  if (meQ.isLoading) return <div className="p-6 opacity-70">Loading…</div>;
  if (meQ.isError)
    return (
      <div className="p-6 text-red-600">
        {(meQ.error as any)?.message || "Failed to load profile"}
      </div>
    );

  return (
    <div className="p-6 space-y-6 max-w-4xl">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-slate-900 dark:text-slate-100">
          Profile
        </h1>
        {isDeleted && (
          <span className="text-xs px-2 py-1 rounded-full border border-red-300 text-red-700 dark:border-red-800 dark:text-red-300">
            Deleted
          </span>
        )}
      </div>

      <div className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#0f1115] p-5 space-y-4">
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <div className="text-xs opacity-70">User ID</div>
            <div className="font-mono text-xs break-all">{me?.id}</div>
          </div>
          <div>
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
            onClick={onExport}
            className="rounded-2xl border px-4 py-2 text-sm shadow hover:shadow-md
                       bg-white dark:bg-[#0f1115] border-slate-200 dark:border-slate-800"
          >
            Export my data (JSON)
          </button>

          <button
            onClick={loadSummary}
            disabled={loadingSummary}
            className="rounded-2xl border px-4 py-2 text-sm shadow hover:shadow-md
                       bg-white dark:bg-[#0f1115] border-slate-200 dark:border-slate-800 disabled:opacity-60"
          >
            {loadingSummary ? "Loading stats…" : "Load stats"}
          </button>
        </div>

        <div className="text-xs opacity-70">
          <div>Created: {formatDate(exp?.createdAt)}</div>
          <div>Updated: {formatDate(exp?.updatedAt)}</div>
        </div>

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
        <h2 className="text-lg font-semibold text-red-700 dark:text-red-300">
          Danger zone
        </h2>
        <p className="text-sm opacity-80">
          Deleting your account will anonymize your profile and trigger removal of your submissions,
          participation/completion records, and uploaded proofs.
        </p>
        <p className="text-xs opacity-70">
          Prototype note: this does not delete your Keycloak account (identity provider). In production we would
          also disable/delete the IdP account and block logins.
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
            onClick={onDelete}
            disabled={del.isPending || isDeleted}
            className="rounded-2xl border px-4 py-2 text-sm shadow hover:shadow-md
                       border-red-300 dark:border-red-800 text-red-700 dark:text-red-300 disabled:opacity-60"
          >
            {del.isPending ? "Deleting…" : "Delete my account data"}
          </button>
        </div>

        {isDeleted && (
          <div className="text-xs text-red-700 dark:text-red-300">
            Deleted at: {formatDate(exp?.deletedAt)}
          </div>
        )}
      </div>
    </div>
  );
}
