import { Link } from 'react-router-dom';
import { useMemo, useState } from 'react';
import { useSubmissions } from '../../hooks/useSubmissions';

export default function SubmissionsList() {
  const { data, isLoading, isError, error } = useSubmissions();
  const [q, setQ] = useState('');
  const [status, setStatus] = useState<'ALL' | 'PENDING' | 'APPROVED' | 'REJECTED'>('ALL');

  const filtered = useMemo(() => {
    const list = data ?? [];
    const byStatus = status === 'ALL' ? list : list.filter(s => s.status === status);
    if (!q.trim()) return byStatus;
    const term = q.toLowerCase();
    return byStatus.filter(s =>
      [s.id, s.questId, s.userId, s.comment, s.proofUrl]
        .some(v => v?.toString().toLowerCase().includes(term))
    );
  }, [data, q, status]);

  if (isLoading) return <div className="p-6">Loading submissions…</div>;
  if (isError) return <div className="p-6 text-red-600">{(error as any)?.message || 'Failed to load submissions'}</div>;

  return (
    <div className="p-6 space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Submissions</h1>
      </div>

      <div className="flex flex-col md:flex-row gap-3">
        <input
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="Search by id, questId, userId, comment, url"
          className="w-full md:max-w-md border rounded-xl px-3 py-2"
        />
        <select
          value={status}
          onChange={(e) => setStatus(e.target.value as any)}
          className="border rounded-xl px-3 py-2 w-full md:w-48"
        >
          <option value="ALL">All statuses</option>
          <option value="PENDING">Pending</option>
          <option value="APPROVED">Approved</option>
          <option value="REJECTED">Rejected</option>
        </select>
      </div>

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
            {filtered.map(s => (
              <tr key={s.id} className="odd:bg-gray-50">
                <td className="p-3 border-b">{s.id}</td>
                <td className="p-3 border-b">
                  <Link to={`/quests/${s.questId}`} className="underline">{s.questId}</Link>
                </td>
                <td className="p-3 border-b">{s.userId ?? '—'}</td>
                <td className="p-3 border-b">{s.status}</td>
                <td className="p-3 border-b text-sm opacity-70">
                  {new Date(s.createdAt).toLocaleString()}
                </td>
                <td className="p-3 border-b">
                  <Link to={`/submissions/${s.id}`} className="underline">View</Link>
                </td>
              </tr>
            ))}
            {filtered.length === 0 && (
              <tr><td className="p-3 opacity-70" colSpan={6}>No submissions found.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
