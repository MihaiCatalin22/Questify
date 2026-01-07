import { Link } from "react-router-dom";
import { useEffect, useMemo, useState } from "react";
import { useSubmissions } from "../../hooks/useSubmissions";
import type { SubmissionStatus } from "../../types/submission";

const TABS: Array<SubmissionStatus | "ALL"> = ["ALL", "APPROVED", "REJECTED", "SCANNING", "PENDING"];

export default function SubmissionsList() {
  const [tab, setTab] = useState<SubmissionStatus | "ALL">("ALL");
  const [q, setQ] = useState("");

  const [page, setPage] = useState(0);
  const [size, setSize] = useState(12);

  const { data, isLoading, isError, error, isFetching, refetch } = useSubmissions(tab);

  useEffect(() => {
    setPage(0);
  }, [tab, size, q]);

  const filtered = useMemo(() => {
    const list = data ?? [];
    if (!q.trim()) return list;
    const term = q.toLowerCase();
    return list.filter((s) =>
      [s.id, s.questId, s.userId, s.comment, s.proofUrl].some((v) =>
        v?.toString().toLowerCase().includes(term)
      )
    );
  }, [data, q]);

  const total = filtered.length;
  const totalPages = Math.max(1, Math.ceil(total / size));

  useEffect(() => {
    if (page > totalPages - 1) setPage(Math.max(0, totalPages - 1));
  }, [page, totalPages]);

  const pageItems = useMemo(() => {
    const start = page * size;
    return filtered.slice(start, start + size);
  }, [filtered, page, size]);

  const nextDisabled = page + 1 >= totalPages || !!isFetching;
  const prevDisabled = page <= 0 || !!isFetching;

  const goPrev = () => setPage((p) => Math.max(0, p - 1));
  const goNext = () => {
    if (!nextDisabled) setPage((p) => p + 1);
  };

  return (
    <div className="p-6 space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Submissions</h1>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        {TABS.map((t) => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={`rounded-full px-3 py-1.5 text-sm border ${
              tab === t ? "bg-black text-white" : "bg-white"
            }`}
            title={t === "ALL" ? "Show all statuses" : `Show ${t.toLowerCase()}`}
          >
            {t === "ALL" ? "All" : t.charAt(0) + t.slice(1).toLowerCase()}
          </button>
        ))}

        <input
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="Search by id, questId, userId, comment, url"
          className="w-full md:max-w-md border rounded-xl px-3 py-2 ml-auto"
        />
      </div>

      {/* Pager bar (same vibe as QuestsList) */}
      <div className="flex flex-wrap items-center gap-2 text-sm">
        <button
          className="rounded border px-2 py-1 disabled:opacity-50"
          onClick={goPrev}
          disabled={prevDisabled}
          title="Previous page"
        >
          ‹ Prev
        </button>

        <span className="px-2">
          Page <strong>{page + 1}</strong> of <strong>{totalPages}</strong>
        </span>

        <button
          className="rounded border px-2 py-1 disabled:opacity-50"
          onClick={goNext}
          disabled={nextDisabled}
          title="Next page"
        >
          Next ›
        </button>

        <select
          className="ml-2 rounded border px-2 py-1"
          value={size}
          onChange={(e) => {
            setPage(0);
            setSize(Number(e.target.value));
          }}
          title="Items per page"
        >
          <option value={12}>12 / page</option>
          <option value={24}>24 / page</option>
          <option value={48}>48 / page</option>
        </select>

        <span className="opacity-70">Total: {total}</span>

        <button
          className="ml-auto rounded border px-2 py-1"
          onClick={() => refetch?.()}
          disabled={!!isFetching}
          title="Refresh"
        >
          {isFetching ? "Refreshing…" : "Refresh"}
        </button>
      </div>

      {isLoading ? (
        <div>Loading submissions…</div>
      ) : isError ? (
        <div className="text-red-600">
          {(error as any)?.message || "Failed to load submissions"}
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="min-w-full border rounded-xl">
            <thead>
              <tr className="text-left">
                <th className="p-3 border-b">ID</th>
                <th className="p-3 border-b">Quest</th>
                <th className="p-3 border-b">User</th>
                <th className="p-3 border-b">Status</th>
                <th className="p-3 border-b">Created</th>
                <th className="p-3 border-b">Actions</th>
              </tr>
            </thead>
            <tbody>
              {pageItems.map((s) => (
                <tr key={s.id} className="odd:bg-gray-50">
                  <td className="p-3 border-b">{s.id}</td>
                  <td className="p-3 border-b">
                    <Link to={`/quests/${s.questId}`} className="underline">
                      {s.questId}
                    </Link>
                  </td>
                  <td className="p-3 border-b">{s.userId ?? "—"}</td>
                  <td className="p-3 border-b">{s.status}</td>
                  <td className="p-3 border-b text-sm opacity-70">
                    {new Date(s.createdAt).toLocaleString()}
                  </td>
                  <td className="p-3 border-b">
                    <Link to={`/submissions/${s.id}`} className="underline">
                      View
                    </Link>
                  </td>
                </tr>
              ))}

              {pageItems.length === 0 && (
                <tr>
                  <td className="p-3 opacity-70" colSpan={6}>
                    No submissions found.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
