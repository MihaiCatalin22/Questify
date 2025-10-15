import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useUsers, useDeleteUser } from '../../hooks/useUsers';

export default function UsersList() {
  const { data, isLoading, isError, error } = useUsers();
  const del = useDeleteUser();
  const [q, setQ] = useState('');

  const filtered = useMemo(() => {
    const list = data ?? [];
    if (!q.trim()) return list;
    const term = q.toLowerCase();
    return list.filter(u =>
      [u.username, u.email, u.displayName].some(v => v?.toLowerCase().includes(term))
    );
  }, [data, q]);

  if (isLoading) return <div className="p-4">Loading users…</div>;
  if (isError) return <div className="p-4 text-red-600">{(error as any)?.message || 'Failed to load users'}</div>;

  return (
    <div className="p-6 space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Users</h1>
        <Link to="/users/new" className="rounded-2xl border px-3 py-1.5 hover:shadow">New User</Link>
      </div>

      <input
        value={q}
        onChange={(e) => setQ(e.target.value)}
        placeholder="Search by username, email, display name"
        className="w-full border rounded-xl px-3 py-2"
      />

      <div className="overflow-x-auto">
        <table className="min-w-full border rounded-xl">
          <thead>
            <tr className="text-left">
              <th className="p-3 border-b">Username</th>
              <th className="p-3 border-b">Email</th>
              <th className="p-3 border-b">Display Name</th>
              <th className="p-3 border-b">Roles</th>
              <th className="p-3 border-b">Actions</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map(u => (
              <tr key={u.id} className="odd:bg-gray-50">
                <td className="p-3 border-b">{u.username}</td>
                <td className="p-3 border-b">{u.email}</td>
                <td className="p-3 border-b">{u.displayName ?? '—'}</td>
                <td className="p-3 border-b">{u.roles.join(', ')}</td>
                <td className="p-3 border-b space-x-2">
                  <Link to={`/users/${u.id}/edit`} className="underline">Edit</Link>
                  <button
                    onClick={() => del.mutate(u.id)}
                    className="underline"
                    disabled={del.isPending}
                  >
                    {del.isPending ? 'Deleting…' : 'Delete'}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
