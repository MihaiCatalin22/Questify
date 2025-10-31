import { useParams, Link } from 'react-router-dom';
import { useQuest, useJoinQuest, useLeaveQuest, useMyQuests } from '../../hooks/useQuests';
import { useCreateSubmission, useSubmissionsForQuest } from '../../hooks/useSubmissions';
import { useState, useMemo, useEffect } from 'react';
import { useAuthContext } from '../../contexts/AuthContext';
import { toast } from 'react-hot-toast';
import http from '../../api/https';

function isImageType(mt?: string | null) { return !!mt && mt.startsWith('image/'); }
function isVideoType(mt?: string | null) { return !!mt && mt.startsWith('video/'); }

function toSameOriginS3(url?: string | null): string | null {
  if (!url) return null;
  try {
    const u = new URL(url);

    if (u.hostname === 'minio') {
      return `/s3${u.pathname}${u.search || ''}`;
    }

    if (u.hostname === 'localhost' && (u.port === '9000' || u.port === '9003')) {
      return `/s3${u.pathname}${u.search || ''}`;
    }

    return url; 
  } catch {
    return url;
  }
}

function SubmissionPreview({ submission }: { submission: any }) {
  const [signedUrl, setSignedUrl] = useState<string | null>(null);
  const [fail, setFail] = useState<string | null>(null);
  const mt = String(submission?.mediaType || '').toLowerCase();
  const isImg = isImageType(mt);
  const isVid = isVideoType(mt);

  useEffect(() => {
    let alive = true;
    async function run() {
      try {
        const { data } = await http.get<{ url: string; expiresInSeconds: number }>(
          `/submissions/${submission.id}/proof-url`
        );
        if (!alive) return;
        setSignedUrl(data.url);
      } catch (e: any) {
        if (!alive) return;
        setFail(e?.response?.data?.message || e?.message || 'Failed to fetch proof URL');
      }
    }
    if (submission?.id) run();
    return () => { alive = false; };
  }, [submission?.id]);

  const displayUrl = toSameOriginS3(signedUrl);

  return (
    <div className="mt-3 space-y-2">
      <div className="font-semibold text-sm">Proof</div>

      {fail && <div className="text-sm text-red-600">{fail}</div>}
      {!fail && !displayUrl && <div className="text-sm text-gray-500">Generating secure link…</div>}

      {displayUrl && isImg && (
        <img
          src={displayUrl}
          alt="proof"
          className="rounded-xl border"
          style={{ maxWidth: '100%', maxHeight: 560, objectFit: 'contain' }}
          loading="lazy"
          referrerPolicy="no-referrer"
        />
      )}
      {displayUrl && isVid && (
        <video
          className="rounded-xl border"
          style={{ maxWidth: '100%', maxHeight: 560 }}
          src={displayUrl}
          controls
          playsInline
          preload="metadata"
        />
      )}
      {displayUrl && !isImg && !isVid && (
        <a className="text-sm underline" href={displayUrl} target="_blank" rel="noreferrer noopener">
          Open proof
        </a>
      )}
    </div>
  );
}

function InfoModal({
  open, onClose, title, children,
}: { open: boolean; onClose: () => void; title: string; children: React.ReactNode }) {
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/50" onClick={onClose} />
      <div className="relative z-10 w-full max-w-md rounded-2xl border bg-white p-5 shadow-xl dark:bg-[#0f1115] dark:border-slate-700">
        <div className="flex items-start justify-between">
          <h3 className="text-lg font-semibold">{title}</h3>
          <button className="text-sm opacity-70 hover:opacity-100" onClick={onClose}>✕</button>
        </div>
        <div className="mt-3 text-sm">{children}</div>
        <div className="mt-5 text-right">
          <button className="px-3 py-1.5 rounded-lg border shadow text-sm hover:bg-gray-100 dark:hover:bg-[#161b26]" onClick={onClose}>
            OK
          </button>
        </div>
      </div>
    </div>
  );
}

export default function QuestDetail() {
  const { id } = useParams();
  const rawId = id ?? '';
  const safeQuestId = /^\d+$/.test(rawId) ? rawId : '';

  const { user } = useAuthContext();

  const { data: quest, isLoading, isError, error } = useQuest(safeQuestId);
  const { data: submissions } = useSubmissionsForQuest(safeQuestId);
  const { data: myQuests } = useMyQuests();
  const create = useCreateSubmission(safeQuestId);

  const join = useJoinQuest();
  const leave = useLeaveQuest();

  const [comment, setComment] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [proofUrl, setProofUrl] = useState('');

  const [localJoined, setLocalJoined] = useState<boolean | null>(null);

  const ownerId =
    (quest as any)?.createdByUserId ??
    (quest as any)?.createdBy?.id ??
    (quest as any)?.createdById ??
    null;

  const isOwner = !!(user && ownerId && String(user.id) === String(ownerId));
  const joinedFromServer = isOwner || !!myQuests?.some((q) => String(q.id) === String((quest as any)?.id));
  const joinedByMe = (localJoined == null) ? joinedFromServer : localJoined;

  const startDate = (quest as any)?.startDate ? new Date((quest as any).startDate) : null;
  const endDate   = (quest as any)?.endDate   ? new Date((quest as any).endDate)   : null;
  const now = new Date();

  const notStartedYet = !!(startDate && now < startDate);
  const ended = !!(endDate && now >= endDate);

  const visibility = (quest as any)?.visibility ?? 'PRIVATE';
  const status = (quest as any)?.status ?? 'ACTIVE';

  const participantCount =
    typeof (quest as any)?.participantsCount === 'number'
      ? (quest as any).participantsCount
      : (Array.isArray((quest as any)?.participants) ? (quest as any).participants.length : 0);

  const mySubs = useMemo(() => {
    const uid = user?.id != null ? String(user.id) : '';
    return (submissions ?? []).filter((s: any) => {
      const subUserId = String((s.userId ?? s.user?.id ?? ''));
      return uid && subUserId === uid;
    });
  }, [submissions, user?.id]);

  const completed = Boolean((quest as any)?.completedByCurrentUser);

  const canSubmit = Boolean(
    joinedByMe && !completed && status !== 'ARCHIVED' && !notStartedYet && !ended
  );

  const canSend = !!(file || (proofUrl && proofUrl.trim().length > 0));
  const canJoin = !joinedByMe && visibility === 'PUBLIC' && status !== 'ARCHIVED' && !!safeQuestId;
  const showLeaveButton = !ended && status !== 'ARCHIVED' && !!safeQuestId && joinedByMe;

  const [showOwnerLeaveModal, setShowOwnerLeaveModal] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await create.mutateAsync({
        questId: safeQuestId,
        comment: comment || undefined,
        file,
        proofUrl: file ? undefined : (proofUrl || undefined),
      });
      toast.success('Submission sent!');
      setComment(''); setFile(null); setProofUrl('');
    } catch (err: any) {
      const msg =
        err?.response?.data?.message ||
        err?.response?.data?.error ||
        err?.message ||
        'Failed to create submission';
      toast.error(String(msg));
    }
  };

  const onJoin = async () => {
    try {
      await join.mutateAsync(safeQuestId);
      setLocalJoined(true);
      toast.success('Joined quest');
    } catch (e: any) {
      toast.error(e?.response?.data?.message || e?.message || 'Failed to join quest');
    }
  };

  const onLeave = async () => {
    if (isOwner) {
      setShowOwnerLeaveModal(true);
      return;
    }
    try {
      await leave.mutateAsync(safeQuestId);
      setLocalJoined(false);
      toast.success('Left quest');
    } catch (e: any) {
      toast.error(e?.response?.data?.message || e?.message || 'Failed to leave quest');
    }
  };

  return (
    <div className="p-6">
      <div className="mx-auto" style={{ maxWidth: 1100 }}>
        {!safeQuestId ? (
          <div className="p-6 text-sm text-gray-600">No quest selected.</div>
        ) : isLoading ? (
          <div className="p-6">Loading quest…</div>
        ) : isError || !quest ? (
          <div className="p-6 text-red-600">{(error as any)?.message || 'Failed to load quest'}</div>
        ) : (
          <div className="space-y-6">
            <div className="rounded-2xl border bg-white shadow-sm dark:bg-[#0f1115]">
              <div className="p-5">
                <div className="flex items-start justify-between gap-4">
                  <div className="min-w-0">
                    <h1 className="text-2xl font-semibold leading-tight break-words">
                      {quest.title}
                    </h1>
                    {quest.description && (
                      <p className="mt-2 text-gray-700 dark:text-gray-300">{quest.description}</p>
                    )}

                    <div className="mt-3 flex flex-wrap items-center gap-2">
                      <span className="inline-block px-2 py-0.5 text-xs font-medium rounded-full border">
                        {quest.category ?? 'General'}
                      </span>

                      <span className="inline-block px-2 py-0.5 text-xs font-medium rounded-full border">
                        {visibility}
                      </span>

                      {(quest as any).status && !completed && (
                        <span className="inline-block px-2 py-0.5 text-xs font-medium rounded-full border">
                          {(quest as any).status}
                        </span>
                      )}

                      <span className="inline-block px-2 py-0.5 text-xs font-medium rounded-full border">
                        Participants: {participantCount}
                      </span>

                      {startDate && (
                        <span className="inline-block px-2 py-0.5 text-xs font-medium rounded-full border" title="Start date">
                          Starts: {startDate.toLocaleDateString()}
                        </span>
                      )}
                      {endDate && (
                        <span className="inline-block px-2 py-0.5 text-xs font-medium rounded-full border" title="End date">
                          Ends: {endDate.toLocaleDateString()}
                        </span>
                      )}

                      {completed ? (
                        <span
                          className="inline-block px-2 py-0.5 text-xs font-medium rounded-full border border-green-600 text-green-700 bg-green-50"
                          title="You have completed this quest"
                        >
                          Completed
                        </span>
                      ) : null}
                    </div>
                  </div>

                  <div className="shrink-0 flex items-center gap-2">
                    {isOwner && !completed && status !== 'ARCHIVED' && (
                      <Link
                        to={`/quests/${quest.id}/edit`}
                        className="px-3 py-1.5 rounded-lg border shadow text-sm hover:bg-gray-100 dark:hover:bg-[#161b26] dark:border-slate-700"
                      >
                        Edit
                      </Link>
                    )}

                    {canJoin && (
                      <button
                        onClick={onJoin}
                        className="px-3 py-1.5 rounded-lg border shadow text-sm hover:bg-gray-100 dark:hover:bg-[#161b26] dark:border-slate-700"
                        disabled={join.isPending}
                      >
                        {join.isPending ? 'Joining…' : 'Join'}
                      </button>
                    )}

                    {showLeaveButton && (
                      <button
                        onClick={onLeave}
                        className="px-3 py-1.5 rounded-lg border shadow text-sm hover:bg-gray-100 dark:hover:bg-[#161b26] dark:border-slate-700"
                        disabled={leave.isPending}
                        title={isOwner ? "You can't leave quests you own" : "Leave this quest"}
                      >
                        {leave.isPending ? 'Leaving…' : 'Leave'}
                      </button>
                    )}
                  </div>
                </div>
              </div>
            </div>

            <div className="grid md:grid-cols-2 gap-6">
              <section className="rounded-2xl border bg-white shadow-sm dark:bg-[#0f1115]">
                <div className="p-5">
                  <h2 className="font-semibold mb-3">Your Submissions</h2>

                  {mySubs.length === 0 ? (
                    <div className="border border-dashed rounded-2xl p-8 text-center text-sm text-gray-500 dark:border-slate-700">
                      No submissions yet.
                    </div>
                  ) : (
                    <ul className="space-y-3">
                      {mySubs.map((s: any) => {
                        const st = (s.status ?? s.reviewStatus);
                        const closed = Boolean(s.closed);

                        return (
                          <li key={s.id} className="border rounded-xl p-3 bg-white dark:bg-[#0f1115]">
                            <div className="flex items-center justify-between gap-3">
                              <div className="text-sm">
                                <div className="font-medium">{s.comment || 'Submission'}</div>
                                <div className="text-xs opacity-70">
                                  {new Date(s.createdAt).toLocaleString()}
                                </div>
                              </div>
                              <div className="flex items-center gap-2">
                                {st && (
                                  <span className="inline-block px-2 py-0.5 text-xs font-medium rounded-full border">
                                    {st}
                                  </span>
                                )}
                                {closed && (
                                  <span
                                    className="inline-block px-2 py-0.5 text-xs font-medium rounded-full border border-slate-400 text-slate-600 bg-slate-50"
                                    title="This submission is closed"
                                  >
                                    Closed
                                  </span>
                                )}
                              </div>
                            </div>

                            <SubmissionPreview submission={s} />
                          </li>
                        );
                      })}
                    </ul>
                  )}
                </div>
              </section>

              <aside className="rounded-2xl border bg-white shadow-sm dark:bg-[#0f1115]">
                <div className="p-5">
                  <h2 className="font-semibold mb-3">New Submission</h2>

                  {!joinedByMe ? (
                    <div className="text-sm text-gray-600 space-y-2">
                      <p>You don’t have this quest yet, so you can’t submit to it.</p>
                      {canJoin && (
                        <button
                          onClick={onJoin}
                          className="px-3 py-1.5 rounded-lg border shadow text-sm hover:bg-gray-100 dark:hover:bg-[#161b26] dark:border-slate-700"
                          disabled={join.isPending}
                        >
                          {join.isPending ? 'Joining…' : 'Join this quest'}
                        </button>
                      )}
                    </div>
                  ) : completed ? (
                    <div className="rounded-lg border border-green-600/30 bg-green-50 text-green-800 p-4 text-sm">
                      🎉 This quest is already completed. Congratulations!
                    </div>
                  ) : status === 'ARCHIVED' ? (
                    <div className="rounded-lg border border-slate-300 bg-slate-50 text-slate-700 p-4 text-sm">
                      This quest is archived and no longer accepts submissions.
                    </div>
                  ) : notStartedYet ? (
                    <div className="rounded-lg border border-amber-300 bg-amber-50 text-amber-800 p-4 text-sm">
                      Quest has not started yet, you can’t submit anything. {startDate ? `Starts on ${startDate.toLocaleString()}.` : ''}
                    </div>
                  ) : ended ? (
                    <div className="rounded-lg border border-slate-300 bg-slate-50 text-slate-700 p-4 text-sm">
                      Quest has ended, new submissions are closed. {endDate ? `Ended on ${endDate.toLocaleString()}.` : ''}
                    </div>
                  ) : (
                    <form className="space-y-4" onSubmit={submit}>
                      <div>
                        <label className="block text-sm font-medium mb-1">
                          Comment (optional)
                        </label>
                        <textarea
                          className="field"
                          value={comment}
                          onChange={(e) => setComment(e.target.value)}
                          placeholder="Add context for your proof…"
                          disabled={create.isPending || !canSubmit}
                        />
                      </div>

                      <div className="text-xs text-gray-500">
                        Attach a file <span className="opacity-60">or</span> paste a URL
                      </div>

                      <div className="space-y-2">
                        <input
                          type="file"
                          accept="image/*,video/*"
                          onChange={(e) => setFile(e.target.files?.[0] ?? null)}
                          className="block text-sm"
                          disabled={create.isPending || !!proofUrl || !canSubmit}
                        />
                        <input
                          placeholder="https://example.com/my-proof"
                          className="field"
                          value={proofUrl}
                          onChange={(e) => {
                            setProofUrl(e.target.value);
                            if (e.target.value) setFile(null);
                          }}
                          disabled={create.isPending || !canSubmit}
                        />
                      </div>

                      <div className="pt-1 flex items-center gap-3">
                        <button
                          type="submit"
                          className="btn-primary disabled:opacity-60"
                          disabled={create.isPending || !canSend || !canSubmit}
                          title={!canSend ? 'Attach a file or paste a URL' : 'Submit'}
                        >
                          {create.isPending ? 'Submitting…' : 'Submit'}
                        </button>
                        {!canSend && (
                          <span className="text-xs text-gray-500">
                            Provide a file or URL to submit.
                          </span>
                        )}
                      </div>
                    </form>
                  )}
                </div>
              </aside>
            </div>
          </div>
        )}
      </div>

      <InfoModal
        open={showOwnerLeaveModal}
        onClose={() => setShowOwnerLeaveModal(false)}
        title="Can’t leave your own quest"
      >
        <p>
          You are the owner of this quest, so you can’t leave it. You can archive or delete the quest instead.
        </p>
      </InfoModal>
    </div>
  );
}
