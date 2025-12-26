import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useUsers } from "../../hooks/useUsers";
import type { UserDTO } from "../../api/users";

export default function UsersList() {
  const [q, setQ] = useState("");
  const { data = [], isLoading, isError, error } = useUsers(q.trim());

  const filtered = useMemo<UserDTO[]>(() => {
    const list = data ?? [];
    const term = q.trim().toLowerCase();
    if (!term) return list;
    return list.filter((u) =>
      [u.username, u.email, u.displayName].some((v) =>
        (v ?? "").toLowerCase().includes(term)
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
        <h1 className="text-2xl font-semibold text-slate-900 dark:text-slate-100">
          Users
        </h1>
      </div>

      <input
        value={q}
        onChange={(e) => setQ(e.target.value)}
        placeholder="Search by username / display name"
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
              {["Username", "Display Name", "User ID", "Actions"].map((h) => (
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
              <tr key={u.id} className="odd:bg-gray-50 dark:odd:bg-slate-800">
                <td className="p-3 border-b border-slate-200 text-slate-800 dark:border-slate-700 dark:text-slate-100">
                  {u.username || "—"}
                </td>
                <td className="p-3 border-b border-slate-200 text-slate-800 dark:border-slate-700 dark:text-slate-100">
                  {u.displayName ?? "—"}
                </td>
                <td className="p-3 border-b border-slate-200 text-slate-700 dark:border-slate-700 dark:text-slate-200 font-mono text-xs">
                  {u.id}
                </td>
                <td className="p-3 border-b border-slate-200 dark:border-slate-700">
                  <Link
                    to={`/users/${encodeURIComponent(u.id)}`}
                    className="underline text-blue-600 dark:text-blue-400"
                  >
                    View
                  </Link>
                </td>
              </tr>
            ))}

            {filtered.length === 0 && (
              <tr>
                <td
                  colSpan={4}
                  className="p-6 text-center text-sm text-slate-500 dark:text-slate-400"
                >
                  No users found.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <p className="text-xs opacity-70">
        Note: roles come from Keycloak token claims; user-service does not expose roles for other users.
      </p>
    </div>
  );
}
