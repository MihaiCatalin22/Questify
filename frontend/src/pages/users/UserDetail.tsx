import { Link, useParams } from "react-router-dom";
import { useUser } from "../../hooks/useUsers";

export default function UserDetail() {
  const { id } = useParams();
  const userId = id ?? "";

  const { data, isLoading, isError, error } = useUser(userId);

  if (isLoading) return <div className="p-6 opacity-70">Loading…</div>;
  if (isError)
    return (
      <div className="p-6 text-red-600">
        {(error as any)?.message || "Failed to load user"}
      </div>
    );

  const u = data!;

  return (
    <div className="p-6 space-y-4 max-w-3xl">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-slate-900 dark:text-slate-100">
          User
        </h1>
        <Link to="/users" className="underline text-sm">
          Back to list
        </Link>
      </div>

      <div className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#0f1115] p-5 space-y-3">
        <div>
          <div className="text-xs opacity-70">User ID</div>
          <div className="font-mono text-sm break-all">{u.id}</div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <div className="text-xs opacity-70">Username</div>
            <div>{u.username || "—"}</div>
          </div>
          <div>
            <div className="text-xs opacity-70">Display name</div>
            <div>{u.displayName ?? "—"}</div>
          </div>
        </div>

        <div>
          <div className="text-xs opacity-70">Bio</div>
          <div className="whitespace-pre-wrap">{u.bio ?? "—"}</div>
        </div>
      </div>
    </div>
  );
}
