import React from 'react';
import { Link } from 'react-router-dom';
import { useDeleteQuest, useLeaveQuest, useMyQuests } from '../../hooks/useQuests';
import { useAuthContext } from '../../contexts/AuthContext';
import type { QuestDTO } from '../../types/quest';
import { toast } from 'react-hot-toast';

type Tab = 'ACTIVE' | 'ARCHIVED';

const LS_KEY = 'questify.completedFirstSeenAt';
const HIDE_AFTER_DAYS = 7;
const HIDE_AFTER_MS = HIDE_AFTER_DAYS * 24 * 60 * 60 * 1000;

type SeenMap = Record<string, number>;

function loadSeen(): SeenMap {
  try {
    const raw = localStorage.getItem(LS_KEY);
    if (!raw) return {};
    const obj = JSON.parse(raw);
    if (obj && typeof obj === 'object') return obj as SeenMap;
  } catch {}
  return {};
}

function saveSeen(map: SeenMap) {
  try { localStorage.setItem(LS_KEY, JSON.stringify(map)); } catch {}
}

export default function QuestsList() {
  const { user } = useAuthContext();
  const { data: quests, isLoading, isError, error } = useMyQuests();
  const del = useDeleteQuest();
  const leave = useLeaveQuest();

  const [tab, setTab] = React.useState<Tab>('ACTIVE');
  const [showCompletedInActive, setShowCompletedInActive] = React.useState(false);
  const [seenCompletedAt, setSeenCompletedAt] = React.useState<SeenMap>(() => loadSeen());

  React.useEffect(() => {
    if (!quests?.length) return;
    const now = Date.now();
    const next = { ...seenCompletedAt };
    let changed = false;
    for (const q of quests) {
      const id = String(q.id);
      const completed = Boolean((q as any).completedByCurrentUser);
      if (completed && next[id] == null) {
        next[id] = now;
        changed = true;
      }
    }
    if (changed) {
      setSeenCompletedAt(next);
      saveSeen(next);
    }
  }, [quests]); 

  const isCompletedAgedOut = React.useCallback((q: QuestDTO & { completedByCurrentUser?: boolean }) => {
    if (!q?.completedByCurrentUser) return false;
    const id = String(q.id);
    const firstSeen = seenCompletedAt[id];
    if (!firstSeen) return false;
    return Date.now() - firstSeen >= HIDE_AFTER_MS;
  }, [seenCompletedAt]);

  if (isLoading) return <div className="p-6">Loading quests…</div>;
  if (isError) return <div className="p-6 text-red-600">{(error as any)?.message || 'Failed to load quests'}</div>;

  const all: QuestDTO[] = quests ?? [];
  const active = all.filter((q: any) => (q as any).status !== 'ARCHIVED');
  const archived = all.filter((q: any) => (q as any).status === 'ARCHIVED');

  const visibleActive = active.filter((q: any) => {
    if (showCompletedInActive) return true;
    return !(q as any).completedByCurrentUser || !isCompletedAgedOut(q);
  });

  const list = (tab === 'ACTIVE' ? visibleActive : archived) as (QuestDTO & any)[];

  return (
    <div className="p-6 space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">My Quests</h1>
        <div className="flex items-center gap-2">
          <Link to="/quests/new" className="rounded-2xl border px-3 py-1.5 hover:shadow">
            New Quest
          </Link>
          <Link to="/quests/discover" className="rounded-2xl border px-3 py-1.5 hover:shadow">
            Discover
          </Link>
        </div>
      </div>

      <div className="flex items-center gap-2">
        <button
          className={`rounded-full px-3 py-1.5 text-sm border ${tab === 'ACTIVE' ? 'bg-black text-white' : 'bg-white'}`}
          onClick={() => setTab('ACTIVE')}
        >
          Active
        </button>
        <button
          className={`rounded-full px-3 py-1.5 text-sm border ${tab === 'ARCHIVED' ? 'bg-black text-white' : 'bg-white'}`}
          onClick={() => setTab('ARCHIVED')}
        >
          Archived
        </button>

        {tab === 'ACTIVE' && (
          <label className="ml-3 flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={showCompletedInActive}
              onChange={(e) => setShowCompletedInActive(e.target.checked)}
            />
            Show completed
          </label>
        )}
      </div>

      {list.length === 0 ? (
        <div className="text-sm text-gray-600">
          {tab === 'ACTIVE' ? 'No active quests yet.' : 'No archived quests.'}
        </div>
      ) : (
        <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-4">
          {list.map((q) => {
            const participantsCount =
              (q as any).participantsCount ??
              (Array.isArray((q as any).participants) ? (q as any).participants.length : 0);

            const completed = Boolean((q as any).completedByCurrentUser);
            const archivedQuest = (q as any).status === 'ARCHIVED';
            const isOwner = user && String(user.id) === String((q as any).createdByUserId);

            const onDelete = () => del.mutate(q.id);

            const onLeave = async () => {
              if (isOwner) {
                toast('You can’t leave quests you own. Archive or delete the quest instead.', {
                  icon: 'ℹ️',
                });
                return;
              }
              try {
                await leave.mutateAsync(String(q.id));
                toast.success('Left quest');
              } catch (e: any) {
                const msg =
                  e?.response?.data?.message ||
                  e?.message ||
                  'Failed to leave quest';
                toast.error(String(msg));
              }
            };

            return (
              <div key={q.id} className="rounded-2xl border p-4 space-y-2 bg-white shadow-sm dark:bg-[#0f1115]">
                <div className="flex items-start justify-between gap-2">
                  <h3 className="font-semibold text-lg">
                    <Link to={`/quests/${q.id}`} className="underline">{q.title}</Link>
                  </h3>
                  <div className="flex items-center gap-2">
                    <span className="inline-flex items-center px-2 py-0.5 text-xs font-medium rounded-full border">
                      {q.category ?? 'General'}
                    </span>

                    {(q as any).status && !completed && !archivedQuest && (
                      <span className="inline-flex items-center px-2 py-0.5 text-xs font-medium rounded-full border">
                        {(q as any).status}
                      </span>
                    )}

                    <span className="inline-flex items-center px-2 py-0.5 text-xs font-medium rounded-full border" title="Participants">
                      Participants: {participantsCount}
                    </span>

                    {completed && (
                      <span className="inline-flex items-center px-2 py-0.5 text-xs font-medium rounded-full border border-green-600 text-green-700 bg-green-50">
                        Completed
                      </span>
                    )}

                    {archivedQuest && (
                      <span className="inline-flex items-center px-2 py-0.5 text-xs font-medium rounded-full border border-slate-400 text-slate-600 bg-slate-50">
                        Archived
                      </span>
                    )}
                  </div>
                </div>

                {q.description && (
                  <p className="text-sm text-gray-700 dark:text-gray-300 line-clamp-3">{q.description}</p>
                )}

                <div className="flex gap-3 text-sm">
                  {isOwner && !completed && !archivedQuest && (
                    <Link to={`/quests/${q.id}/edit`} className="underline">Edit</Link>
                  )}
                  {isOwner && (
                    <button
                      onClick={onDelete}
                      className="underline"
                      disabled={del.isPending}
                    >
                      {del.isPending ? 'Deleting…' : 'Delete'}
                    </button>
                  )}

                  {!archivedQuest && (
                    <button
                      onClick={onLeave}
                      className="underline"
                      disabled={leave.isPending && !isOwner}
                      title={isOwner ? "You can't leave quests you own" : "Leave this quest"}
                    >
                      {leave.isPending && !isOwner ? 'Leaving…' : 'Leave'}
                    </button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
