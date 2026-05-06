import { Link } from "react-router-dom";
import React from "react";
import { useDiscoverQuests, useJoinQuest, useMyQuests } from "../../hooks/useQuests";
import { Badge, Button, EmptyState, ErrorState, LoadingState, PageHeader, PageShell, Panel } from "../../components/ui";
import { getErrorMessage } from "../../utils/errors";

export default function DiscoverQuests() {
  const { data: quests, isLoading, isError, error } = useDiscoverQuests();
  const { data: myQuests } = useMyQuests();
  const join = useJoinQuest();
  const [joiningId, setJoiningId] = React.useState<string | null>(null);

  if (isLoading) return <LoadingState label="Loading public quests..." />;
  if (isError) return <ErrorState message={getErrorMessage(error, "Failed to load discover")} />;

  const list = quests ?? [];
  const idStr = (v: unknown) => String(v);
  const isJoined = (qid: string) => !!myQuests?.some(q => idStr(q.id) === qid);

  return (
    <PageShell>
      <PageHeader
        title="Discover"
        description="Find public quests you can join and submit proof to."
      />

      {list.length === 0 ? (
        <EmptyState title="No public quests available right now." description="Check back later or create your own public quest." />
      ) : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
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
              <Panel key={qid} className="flex min-h-[200px] flex-col p-5">
                <div className="flex items-start justify-between gap-3">
                  <h3 className="min-w-0 text-lg font-semibold leading-snug">
                    <Link to={`/quests/${qid}`} className="hover:text-[rgb(var(--accent))]">{q.title}</Link>
                  </h3>
                  <div className="flex flex-wrap justify-end gap-2">
                    <Badge tone="info">{q.category ?? 'General'}</Badge>
                    <Badge tone="accent">Public</Badge>
                  </div>
                </div>

                {q.description && <p className="mt-3 line-clamp-3 text-sm leading-6 text-[rgb(var(--muted))]">{q.description}</p>}

                <div className="mt-auto flex gap-2 pt-5">
                  {!joined ? (
                    <Button
                      onClick={onJoin}
                      disabled={joining}
                      title="Join this quest to submit proofs"
                    >
                      {joining ? "Joining..." : "Join"}
                    </Button>
                  ) : (
                    <Badge tone="success">Already joined</Badge>
                  )}
                  <Link to={`/quests/${qid}`} className="btn btn-ghost">View</Link>
                </div>
              </Panel>
            );
          })}
        </div>
      )}
    </PageShell>
  );
}
