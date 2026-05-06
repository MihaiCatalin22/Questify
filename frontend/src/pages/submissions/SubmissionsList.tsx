import { Link } from "react-router-dom";
import { useEffect, useMemo, useState } from "react";
import { useSubmissions } from "../../hooks/useSubmissions";
import type { SubmissionStatus } from "../../types/submission";
import { ChevronLeft, ChevronRight, RefreshCcw } from "lucide-react";
import { getErrorMessage } from "../../utils/errors";
import {
  Button,
  EmptyState,
  ErrorState,
  LoadingState,
  PageHeader,
  PageShell,
  Panel,
  SelectInput,
  StatusBadge,
  TextInput,
} from "../../components/ui";

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
    <PageShell>
      <PageHeader
        title="Submissions"
        description="Browse proof submissions by status and open a review/detail view when needed."
      />

      <Panel className="space-y-4 p-4">
        <div className="flex flex-wrap items-center gap-3">
          <div className="tabs">
            {TABS.map((t) => (
              <button
                key={t}
                onClick={() => setTab(t)}
                className={`tab ${tab === t ? "tab-active" : ""}`}
                title={t === "ALL" ? "Show all statuses" : `Show ${t.toLowerCase()}`}
              >
                {t === "ALL" ? "All" : t.charAt(0) + t.slice(1).toLowerCase()}
              </button>
            ))}
          </div>

          <TextInput
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Search by id, quest, user, comment, url"
            className="w-full md:ml-auto md:max-w-md"
          />
        </div>

        <div className="flex flex-wrap items-center gap-2 text-sm">
          <Button variant="ghost" onClick={goPrev} disabled={prevDisabled} title="Previous page">
            <ChevronLeft className="h-4 w-4" />
            Prev
          </Button>

          <span className="px-2 text-[rgb(var(--muted))]">
            Page <strong>{page + 1}</strong> of <strong>{totalPages}</strong>
          </span>

          <Button variant="ghost" onClick={goNext} disabled={nextDisabled} title="Next page">
            Next
            <ChevronRight className="h-4 w-4" />
          </Button>

          <SelectInput
            className="w-auto py-2"
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
          </SelectInput>

          <span className="text-[rgb(var(--muted))]">Total: {total}</span>

          <Button
            className="sm:ml-auto"
            variant="ghost"
            onClick={() => refetch?.()}
            disabled={!!isFetching}
            title="Refresh"
          >
            <RefreshCcw className={`h-4 w-4 ${isFetching ? "animate-spin" : ""}`} />
            {isFetching ? "Refreshing..." : "Refresh"}
          </Button>
        </div>
      </Panel>

      {isLoading ? (
        <LoadingState label="Loading submissions..." />
      ) : isError ? (
        <ErrorState message={getErrorMessage(error, "Failed to load submissions")} />
      ) : (
        <div className="table-shell">
          <table className="data-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Quest</th>
                <th>User</th>
                <th>Status</th>
                <th>Created</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {pageItems.map((s) => (
                <tr key={s.id}>
                  <td className="font-mono text-xs">{s.id}</td>
                  <td>
                    <Link to={`/quests/${s.questId}`} className="link">
                      {s.questId}
                    </Link>
                  </td>
                  <td>{s.userId ?? "-"}</td>
                  <td><StatusBadge status={s.status} /></td>
                  <td className="text-sm text-[rgb(var(--muted))]">
                    {new Date(s.createdAt).toLocaleString()}
                  </td>
                  <td>
                    <Link to={`/submissions/${s.id}`} className="link">
                      View
                    </Link>
                  </td>
                </tr>
              ))}

              {pageItems.length === 0 && (
                <tr>
                  <td colSpan={6}>
                    <EmptyState title="No submissions found." description="Adjust the filter or search query." />
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </PageShell>
  );
}
