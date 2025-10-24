import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useUsers, useDeleteUser } from "../../hooks/useUsers";
import type { UserDTO } from "../../api/users";

export default function UsersList() {
  const { data = [], isLoading, isError, error } = useUsers();
  const del = useDeleteUser();
  const [q, setQ] = useState("");

  const filtered = useMemo<UserDTO[]>(() => {
    const list = data ?? [];
    const term = q.trim().toLowerCase();
    if (!term) return list;
    return list.filter((u) =>
      [u.username, u.email, u.displayName].some((v) =>
        v?.toLowerCase().includes(term)
      )
    );
  }, [data, q]);

  if (isLoading) return <div className="p-4">Loading users…</div>;
  if (isError)
    return (
      <div className="p-4 text-red-600">
        {(error as any)?.message || "Failed to load users"}
      </div>
    );

  return (
    <div className="p-6 space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-slate-900 dark:text-slate-100">Users</h1>
        <Link
          to="/users/new"
          className="rounded-2xl border px-3 py-1.5 hover:shadow
                     border-slate-200 text-slate-700
                     dark:border-slate-700 dark:text-slate-100"
        >
          New User
        </Link>
      </div>

      <input
        value={q}
        onChange={(e) => setQ(e.target.value)}
        placeholder="Search by username, email, display name"
        className="w-full rounded-xl px-3 py-2
                   border border-slate-200 text-slate-800 placeholder-slate-400
                   bg-white
                   dark:bg-slate-800 dark:text-slate-100 dark:placeholder-slate-400 dark:border-slate-700"
      />

      <div className="overflow-x-auto">
        <table
          className="min-w-full rounded-xl overflow-hidden
                     border border-slate-200
                     dark:border-slate-700"
        >
          <thead className="bg-slate-50 dark:bg-slate-800/60">
            <tr className="text-left">
              {["Username", "Email", "Display Name", "Roles", "Actions"].map((h) => (
                <th
                  key={h}
                  className="p-3 border-b border-slate-200 text-slate-700
                             dark:border-slate-700 dark:text-slate-200"
                >
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {filtered.map((u) => (
              <tr
                key={u.id}
                className="odd:bg-gray-50 dark:odd:bg-slate-800"
              >
                <td className="p-3 border-b border-slate-200 text-slate-800 dark:border-slate-700 dark:text-slate-100">
                  {u.username}
                </td>
                <td className="p-3 border-b border-slate-200 text-slate-600 dark:border-slate-700 dark:text-slate-300">
                  {u.email}
                </td>
                <td className="p-3 border-b border-slate-200 text-slate-800 dark:border-slate-700 dark:text-slate-100">
                  {u.displayName ?? "—"}
                </td>
                <td className="p-3 border-b border-slate-200 text-slate-700 dark:border-slate-700 dark:text-slate-200">
                  {(Array.isArray(u.roles) ? u.roles : []).join(", ") || "—"}
                </td>
                <td className="p-3 border-b border-slate-200 dark:border-slate-700">
                  <div className="space-x-2">
                    <Link
                      to={`/users/${u.id}/edit`}
                      className="underline text-blue-600 dark:text-blue-400"
                    >
                      Edit
                    </Link>
                    <button
                      onClick={() => del.mutate(String(u.id))}
                      className="underline text-rose-600 dark:text-rose-400 disabled:opacity-60"
                      disabled={del.isPending}
                    >
                      {del.isPending ? "Deleting…" : "Delete"}
                    </button>
                  </div>
                </td>
              </tr>
            ))}

            {filtered.length === 0 && (
              <tr>
                <td
                  colSpan={5}
                  className="p-6 text-center text-sm text-slate-500 dark:text-slate-400"
                >
                  No users found.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
