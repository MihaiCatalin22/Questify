import React from 'react';
import { Link } from 'react-router-dom';
import { useDeleteQuest, useLeaveQuest, useMyQuestsServerPage } from '../../hooks/useQuests';
import { useAuthContext } from '../../contexts/useAuthContext';
import type { QuestDTO } from '../../types/quest';
import { toast } from 'react-hot-toast';
import { Archive, ChevronLeft, ChevronRight, Compass, Flame, RefreshCcw, Trophy, XCircle } from 'lucide-react';
import { getErrorMessage } from '../../utils/errors';
import { StreaksApi, type StreakSummary } from '../../api/streaks';
import {
  Badge,
  Button,
  EmptyState,
  ErrorState,
  LoadingState,
  PageHeader,
  PageShell,
  Panel,
  SelectInput,
  StatusBadge,
} from '../../components/ui';

type Tab = 'ACTIVE' | 'ARCHIVED';

const LS_KEY = 'questify.completedFirstSeenAt';
const HIDE_AFTER_DAYS = 7;
const HIDE_AFTER_MS = HIDE_AFTER_DAYS * 24 * 60 * 60 * 1000;

type SeenMap = Record<string, number>;
type QuestListItem = QuestDTO & { participants?: unknown[] };

function loadSeen(): SeenMap {
  try {
    const raw = localStorage.getItem(LS_KEY);
    if (!raw) return {};
    const obj = JSON.parse(raw);
    if (obj && typeof obj === 'object') return obj as SeenMap;
  } catch {
    return {};
  }
  return {};
}
function saveSeen(map: SeenMap) {
  try {
    localStorage.setItem(LS_KEY, JSON.stringify(map));
  } catch {
    return;
  }
}

export default function QuestsList() {
  const { user } = useAuthContext();

  // UI state
  const [tab, setTab] = React.useState<Tab>('ACTIVE');
  const [page, setPage] = React.useState(0);
  const [size, setSize] = React.useState(12);
  const [showCompletedInActive, setShowCompletedInActive] = React.useState(false);
  const [seenCompletedAt, setSeenCompletedAt] = React.useState<SeenMap>(() => loadSeen());
  const [streak, setStreak] = React.useState<StreakSummary | null>(null);

  // Server-side paged + archived filter
  const { data: paged, isLoading, isError, error, isFetching, refetch } =
    useMyQuestsServerPage(tab, page, size);

  const del = useDeleteQuest();
  const leave = useLeaveQuest();

  React.useEffect(() => {
    let alive = true;
    StreaksApi.mine()
      .then((summary) => {
        if (alive) setStreak(summary);
      })
      .catch(() => {
        if (alive) setStreak(null);
      });
    return () => { alive = false; };
  }, []);

  // Reset to first page on tab/size change
  React.useEffect(() => { setPage(0); }, [tab, size]);

  const quests: QuestDTO[] = paged?.content ?? [];
  const totalPages = paged?.totalPages ?? 1;
  const total = paged?.totalElements ?? quests.length;

  // Remember first-seen completed timestamps
  React.useEffect(() => {
    if (!quests?.length) return;
    const now = Date.now();
    const next = { ...seenCompletedAt };
    let changed = false;
    for (const q of quests) {
      const id = String(q.id);
      const completed = Boolean(q.completedByCurrentUser);
      if (completed && next[id] == null) {
        next[id] = now;
        changed = true;
      }
    }
    if (changed) {
      setSeenCompletedAt(next);
      saveSeen(next);
    }
  }, [quests]); // eslint-disable-line react-hooks/exhaustive-deps

  const isCompletedAgedOut = React.useCallback((q: QuestDTO & { completedByCurrentUser?: boolean }) => {
    if (!q?.completedByCurrentUser) return false;
    const id = String(q.id);
    const firstSeen = seenCompletedAt[id];
    if (!firstSeen) return false;
    return Date.now() - firstSeen >= HIDE_AFTER_MS;
  }, [seenCompletedAt]);

  if (isLoading) return <LoadingState label="Loading quests..." />;
  if (isError) return <ErrorState message={getErrorMessage(error, 'Failed to load quests')} />;

  // Backend already filtered by "archived" flag.
  // Optionally hide long-ago completed on ACTIVE tab only.
  const list: QuestListItem[] = tab === 'ACTIVE'
    ? quests.filter((q) => (showCompletedInActive ? true : !q.completedByCurrentUser || !isCompletedAgedOut(q)))
    : quests
  ;

  const nextDisabled = page + 1 >= totalPages || isFetching;
  const prevDisabled = page <= 0 || isFetching;

  const goPrev = () => setPage(p => Math.max(0, p - 1));
  const goNext = () => { if (!nextDisabled) setPage(p => p + 1); };

  return (
    <PageShell>
      <PageHeader
        title="Quests"
        description="Manage the quests you own or joined. Active quests stay visible until they are completed or archived."
        actions={
          <>
            <Link to="/quests/discover" className="btn btn-secondary">
              <Compass className="h-4 w-4" />
              Discover
            </Link>
            <Link to="/quests/new" className="btn btn-primary">New Quest</Link>
          </>
        }
      />

      {streak && (
        <Panel className="p-4">
          <div className="grid gap-3 sm:grid-cols-3">
            <div className="flex items-center gap-3">
              <div className="grid h-10 w-10 place-items-center rounded-lg border border-[rgba(var(--accent),0.35)] bg-[rgba(var(--accent),0.12)] text-[rgb(var(--accent))]">
                <Flame className="h-5 w-5" />
              </div>
              <div>
                <div className="text-xs text-[rgb(var(--faint))]">Current streak</div>
                <div className="font-semibold">{streak.currentStreak} day(s)</div>
              </div>
            </div>
            <div className="flex items-center gap-3">
              <div className="grid h-10 w-10 place-items-center rounded-lg border border-[rgba(var(--accent-2),0.35)] bg-[rgba(var(--accent-2),0.12)] text-[rgb(var(--accent-2))]">
                <Trophy className="h-5 w-5" />
              </div>
              <div>
                <div className="text-xs text-[rgb(var(--faint))]">Level</div>
                <div className="font-semibold">Level {streak.level}</div>
              </div>
            </div>
            <div>
              <div className="mb-2 flex items-center justify-between text-xs text-[rgb(var(--faint))]">
                <span>XP progress</span>
                <span>{streak.levelXp}/{streak.nextLevelXp}</span>
              </div>
              <div className="h-2 overflow-hidden rounded-full bg-[rgba(var(--surface-3),0.9)]">
                <div
                  className="h-full rounded-full bg-[rgb(var(--accent))]"
                  style={{ width: `${Math.min(100, Math.round((streak.levelXp / streak.nextLevelXp) * 100))}%` }}
                />
              </div>
            </div>
          </div>
        </Panel>
      )}

      <Panel className="p-4">
        <div className="flex flex-wrap items-center gap-3">
          <div className="tabs">
          <button
            className={`tab ${tab === 'ACTIVE' ? 'tab-active' : ''}`}
            onClick={() => setTab('ACTIVE')}
          >
            Active
          </button>
          <button
            className={`tab ${tab === 'ARCHIVED' ? 'tab-active' : ''}`}
            onClick={() => setTab('ARCHIVED')}
          >
            Archived
          </button>
        </div>

        {tab === 'ACTIVE' && (
          <label className="flex items-center gap-2 text-sm text-[rgb(var(--muted))]">
            <input
              type="checkbox"
              checked={showCompletedInActive}
              onChange={(e) => setShowCompletedInActive(e.target.checked)}
              className="h-4 w-4 rounded border-[rgb(var(--border))] bg-[rgb(var(--bg-soft))]"
            />
            Show completed
          </label>
        )}

          <div className="ml-auto flex flex-wrap items-center gap-2 text-sm">
            <Button
              variant="ghost"
            onClick={goPrev}
            disabled={prevDisabled}
            title="Previous page"
          >
              <ChevronLeft className="h-4 w-4" />
              Prev
            </Button>

            <span className="px-2 text-[rgb(var(--muted))]">
            Page <strong>{page + 1}</strong> of <strong>{totalPages}</strong>
          </span>

            <Button
              variant="ghost"
            onClick={goNext}
            disabled={nextDisabled}
            title="Next page"
          >
              Next
              <ChevronRight className="h-4 w-4" />
            </Button>

            <SelectInput
              className="w-auto py-2"
            value={size}
            onChange={(e) => { setPage(0); setSize(Number(e.target.value)); }}
            title="Items per page"
          >
            <option value={12}>12 / page</option>
            <option value={24}>24 / page</option>
            <option value={48}>48 / page</option>
            </SelectInput>

            <span className="text-[rgb(var(--muted))]">Total: {total}</span>

            <Button
              variant="ghost"
            onClick={() => refetch()}
            disabled={isFetching}
            title="Refresh"
          >
              <RefreshCcw className={`h-4 w-4 ${isFetching ? 'animate-spin' : ''}`} />
              {isFetching ? 'Refreshing...' : 'Refresh'}
            </Button>
          </div>
        </div>
      </Panel>

      {list.length === 0 ? (
        <EmptyState
          title={tab === 'ACTIVE' ? 'No active quests on this page.' : 'No archived quests on this page.'}
          description="Change the tab, refresh the list, or create a new quest."
          action={<Link to="/quests/new" className="btn btn-secondary">Create quest</Link>}
        />
      ) : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {list.map((q) => {
            const participantsCount =
              q.participantsCount ??
              (Array.isArray(q.participants) ? q.participants.length : 0);

            const completed = Boolean(q.completedByCurrentUser);
            const archivedQuest = q.status === 'ARCHIVED';
            const isOwner = Boolean(user && String(user.id) === String(q.createdByUserId));

            const onArchive = async () => {
              try {
                await del.mutateAsync(q.id);
                // If page becomes empty after archiving, step back a page
                setTimeout(() => {
                  if ((paged?.content?.length ?? 0) <= 1 && page > 0) {
                    setPage(p => Math.max(0, p - 1));
                  }
                }, 0);
              } catch (e: unknown) {
                toast.error(getErrorMessage(e, 'Failed to archive quest'));
              }
            };

            const onLeave = async () => {
              if (isOwner) {
                toast('You cannot leave quests you own. Archive the quest instead.');
                return;
              }
              try {
                await leave.mutateAsync(String(q.id));
                toast.success('Left quest');
              } catch (e: unknown) {
                toast.error(getErrorMessage(e, 'Failed to leave quest'));
              }
            };

            return (
              <Panel key={q.id} className="flex min-h-[220px] flex-col p-5">
                <div className="flex items-start justify-between gap-3">
                  <h3 className="min-w-0 text-lg font-semibold leading-snug">
                    <Link to={`/quests/${q.id}`} className="hover:text-[rgb(var(--accent))]">{q.title}</Link>
                  </h3>
                  <div className="flex flex-wrap justify-end gap-2">
                    <Badge tone="info">{q.category ?? 'General'}</Badge>

                    {q.status && !completed && !archivedQuest && (
                      <StatusBadge status={q.status} />
                    )}

                    <Badge title="Participants">
                      Participants: {participantsCount}
                    </Badge>

                    {completed && (
                      <Badge tone="success">Completed</Badge>
                    )}

                    {archivedQuest && (
                      <Badge>Archived</Badge>
                    )}
                  </div>
                </div>

                {q.description && (
                  <p className="mt-3 line-clamp-3 text-sm leading-6 text-[rgb(var(--muted))]">{q.description}</p>
                )}

                <div className="mt-auto flex flex-wrap gap-2 pt-5">
                  {isOwner && !completed && !archivedQuest && (
                    <Link to={`/quests/${q.id}/edit`} className="btn btn-ghost">Edit</Link>
                  )}
                  {isOwner && (
                    <Button
                      variant="ghost"
                      onClick={onArchive}
                      disabled={del.isPending}
                    >
                      <Archive className="h-4 w-4" />
                      {del.isPending ? 'Archiving...' : 'Archive'}
                    </Button>
                  )}

                  {!archivedQuest && (
                    <Button
                      variant="ghost"
                      onClick={onLeave}
                      disabled={leave.isPending && !isOwner}
                      title={isOwner ? "You can't leave quests you own" : "Leave this quest"}
                    >
                      <XCircle className="h-4 w-4" />
                      {leave.isPending && !isOwner ? 'Leaving...' : 'Leave'}
                    </Button>
                  )}
                </div>
              </Panel>
            );
          })}
        </div>
      )}
    </PageShell>
  );
}
