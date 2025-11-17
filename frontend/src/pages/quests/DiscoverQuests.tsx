import { Link } from "react-router-dom";
import React from "react";
import { useDiscoverQuests, useJoinQuest, useMyQuests } from "../../hooks/useQuests";

export default function DiscoverQuests() {
  const { data: quests, isLoading, isError, error } = useDiscoverQuests();
  const { data: myQuests } = useMyQuests();
  const join = useJoinQuest();
  const [joiningId, setJoiningId] = React.useState<string | null>(null);

  if (isLoading) return <div className="p-6">Loading discover…</div>;
  if (isError) return <div className="p-6 text-red-600">{(error as any)?.message || "Failed to load discover"}</div>;

  const list = quests ?? [];
  const idStr = (v: unknown) => String(v);
  const isJoined = (qid: string) => !!myQuests?.some(q => idStr(q.id) === qid);

  return (
    <div className="p-6 space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Discover public quests</h1>
      </div>

      {list.length === 0 ? (
        <div className="text-sm text-gray-600">No public quests available right now.</div>
      ) : (
        <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-4">
          {list.map((q) => {
            const qid = idStr(q.id);
            const joined = isJoined(qid);
            const joining = joiningId === qid && join.isPending;

            const onJoin = async () => {
              if (joined) return;
              setJoiningId(qid);
              try { await join.mutateAsync(qid); }
              finally { setJoiningId(null); }
            };

            return (
              <div key={qid} className="rounded-2xl border p-4 space-y-2 bg-white shadow-sm dark:bg-[#0f1115]">
                <div className="flex items-start justify-between gap-2">
                  <h3 className="font-semibold text-lg">
                    <Link to={`/quests/${qid}`} className="underline">{q.title}</Link>
                  </h3>
                  <div className="flex items-center gap-2">
                    <span className="inline-flex items-center px-2 py-0.5 text-xs font-medium rounded-full border">
                      {q.category ?? 'General'}
                    </span>
                    <span className="inline-flex items-center px-2 py-0.5 text-xs font-medium rounded-full border">
                      PUBLIC
                    </span>
                  </div>
                </div>

                {q.description && <p className="text-sm text-gray-700 dark:text-gray-300 line-clamp-3">{q.description}</p>}

                <div className="flex gap-3 text-sm">
                  {!joined ? (
                    <button
                      onClick={onJoin}
                      className="underline"
                      disabled={joining}
                      title="Join this quest to submit proofs"
                    >
                      {joining ? "Joining…" : "Join"}
                    </button>
                  ) : (
                    <span className="text-xs opacity-70">Already joined</span>
                  )}
                  <Link to={`/quests/${qid}`} className="underline">View</Link>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
