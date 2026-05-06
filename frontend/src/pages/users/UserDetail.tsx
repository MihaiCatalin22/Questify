import { Link, useParams } from "react-router-dom";
import { useUser } from "../../hooks/useUsers";
import { ErrorState, LoadingState, PageHeader, PageShell, Panel } from "../../components/ui";
import { getErrorMessage } from "../../utils/errors";

export default function UserDetail() {
  const { id } = useParams();
  const userId = id ?? "";

  const { data, isLoading, isError, error } = useUser(userId);

  if (isLoading) return <LoadingState label="Loading user..." />;
  if (isError)
    return (
      <ErrorState message={getErrorMessage(error, "Failed to load user")} />
    );

  const u = data!;

  return (
    <PageShell className="max-w-3xl">
      <PageHeader
        title="User"
        description="Read-only profile details from user-service."
        actions={<Link to="/users" className="btn btn-secondary">Back to list</Link>}
      />

      <Panel className="p-5 space-y-4">
        <div>
          <div className="text-xs text-[rgb(var(--faint))]">User ID</div>
          <div className="font-mono text-sm break-all">{u.id}</div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <div className="text-xs text-[rgb(var(--faint))]">Username</div>
            <div>{u.username || "—"}</div>
          </div>
          <div>
            <div className="text-xs text-[rgb(var(--faint))]">Display name</div>
            <div>{u.displayName ?? "—"}</div>
          </div>
        </div>

        <div>
          <div className="text-xs text-[rgb(var(--faint))]">Bio</div>
          <div className="whitespace-pre-wrap">{u.bio ?? "—"}</div>
        </div>
      </Panel>
    </PageShell>
  );
}
