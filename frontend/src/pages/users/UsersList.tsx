import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useUsers } from "../../hooks/useUsers";
import type { UserDTO } from "../../api/users";
import { ErrorState, LoadingState, PageHeader, PageShell, Panel, TextInput } from "../../components/ui";
import { getErrorMessage } from "../../utils/errors";

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

  if (isLoading) return <LoadingState label="Loading users..." />;
  if (isError)
    return (
      <ErrorState message={getErrorMessage(error, "Failed to load users")} />
    );

  return (
    <PageShell>
      <PageHeader
        title="Users"
        description="View user records exposed by user-service. Account creation and auth changes still happen in Keycloak."
      />

      <Panel className="p-4">
      <TextInput
        value={q}
        onChange={(e) => setQ(e.target.value)}
        placeholder="Search by username / display name"
      />
      </Panel>

      <div className="table-shell">
        <table className="data-table">
          <thead>
            <tr>
              {["Username", "Display Name", "User ID", "Actions"].map((h) => (
                <th key={h}>
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {filtered.map((u) => (
              <tr key={u.id}>
                <td>
                  {u.username || "—"}
                </td>
                <td>
                  {u.displayName ?? "—"}
                </td>
                <td className="font-mono text-xs text-[rgb(var(--muted))]">
                  {u.id}
                </td>
                <td>
                  <Link
                    to={`/users/${encodeURIComponent(u.id)}`}
                    className="link"
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
                  className="p-6 text-center text-sm text-[rgb(var(--muted))]"
                >
                  No users found.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <p className="text-xs text-[rgb(var(--faint))]">
        Note: roles come from Keycloak token claims; user-service does not expose roles for other users.
      </p>
    </PageShell>
  );
}
