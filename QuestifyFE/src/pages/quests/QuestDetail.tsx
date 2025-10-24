import { useParams, Link } from 'react-router-dom';
import { useQuest } from '../../hooks/useQuests';
import { useCreateSubmission, useSubmissionsForQuest } from '../../hooks/useSubmissions';
import { useState } from 'react';
import { useAuthContext } from '../../contexts/AuthContext';

export default function QuestDetail() {
  const { id } = useParams();
  const questId = id ?? '';
  const { user } = useAuthContext();

  const { data: quest, isLoading, isError } = useQuest(questId);
  const { data: submissions } = useSubmissionsForQuest(questId);
  const create = useCreateSubmission(questId);

  const [comment, setComment] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [proofUrl, setProofUrl] = useState('');

  // ✅ no hooks below this point that would be conditionally skipped
  if (isLoading) return <div className="p-6">Loading quest…</div>;
  if (isError || !quest) return <div className="p-6 text-red-600">Failed to load quest</div>;

  const ownerId =
    (quest as any).createdByUserId ??
    (quest as any)?.createdBy?.id ??
    (quest as any)?.createdById ??
    null;

  const isOwner = !!(user && ownerId && String(user.id) === String(ownerId));
  const isParticipant =
    !!user &&
    Array.isArray((quest as any).participants) &&
    (quest as any).participants.some((p: any) => String(p.id) === String(user.id));

  const participantCount = Array.isArray((quest as any).participants)
    ? (quest as any).participants.length
    : 0;

  const canSubmit = Boolean(isOwner || isParticipant);

  // ✅ simple boolean; no hook necessary
  const canSend = !!(file || (proofUrl && proofUrl.trim().length > 0));

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    await create.mutateAsync({
      questId,
      comment: comment || undefined,
      file,
      proofUrl: file ? undefined : (proofUrl || undefined),
    });
    setComment('');
    setFile(null);
    setProofUrl('');
  };

  return (
    <div className="p-6">
      <div className="mx-auto" style={{ maxWidth: 1100 }}>
        <div className="space-y-6">
          {/* Header / summary card */}
          <div className="rounded-2xl border bg-white shadow-sm">
            <div className="p-5">
              <div className="flex items-start justify-between gap-4">
                <div className="min-w-0">
                  <h1 className="text-2xl font-semibold leading-tight break-words">
                    {quest.title}
                  </h1>
                  {quest.description && (
                    <p className="mt-2 text-gray-700">{quest.description}</p>
                  )}

                  <div className="mt-3 flex flex-wrap items-center gap-2">
                    <span className="inline-block px-2 py-0.5 text-xs font-medium rounded-full border">
                      {quest.category ?? 'General'}
                    </span>
                    {(quest as any).status && (
                      <span className="inline-block px-2 py-0.5 text-xs font-medium rounded-full border">
                        {(quest as any).status}
                      </span>
                    )}
                    <span className="inline-block px-2 py-0.5 text-xs font-medium rounded-full border">
                      Participants: {participantCount}
                    </span>
                  </div>
                </div>

                <div className="shrink-0">
                  {isOwner && (
                    <Link
                      to={`/quests/${quest.id}/edit`}
                      className="px-3 py-1.5 rounded-lg border shadow text-sm hover:bg-gray-100"
                    >
                      Edit
                    </Link>
                  )}
                </div>
              </div>
            </div>
          </div>

          {/* Two columns */}
          <div className="grid md:grid-cols-2 gap-6">
            {/* Submissions */}
            <section className="rounded-2xl border bg-white shadow-sm">
              <div className="p-5">
                <h2 className="font-semibold mb-3">Submissions</h2>

                {(submissions ?? []).length === 0 ? (
                  <div className="border border-dashed rounded-2xl p-8 text-center text-sm text-gray-500">
                    No submissions yet.
                  </div>
                ) : (
                  <ul className="space-y-3">
                    {(submissions ?? []).map((s) => (
                      <li key={s.id} className="border rounded-xl p-3 bg-white">
                        <div className="flex items-center justify-between gap-3">
                          <div className="text-sm">
                            <div className="font-medium">{s.comment || 'Submission'}</div>
                            <div className="text-xs opacity-70">
                              {new Date(s.createdAt).toLocaleString()}
                            </div>
                          </div>
                          <span className="inline-block px-2 py-0.5 text-xs font-medium rounded-full border">
                            {(s as any).status ?? (s as any).reviewStatus}
                          </span>
                        </div>

                        {s.proofUrl && (
                          <a
                            href={s.proofUrl}
                            target="_blank"
                            rel="noreferrer"
                            className="text-sm underline mt-2 inline-block"
                          >
                            View proof
                          </a>
                        )}
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </section>

            {/* New submission */}
            <aside className="rounded-2xl border bg-white shadow-sm">
              <div className="p-5">
                <h2 className="font-semibold mb-3">New Submission</h2>

                {!canSubmit ? (
                  <p className="text-sm text-gray-600">
                    You don’t have this quest, so you can’t submit to it.
                  </p>
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
                        disabled={create.isPending}
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
                        disabled={create.isPending || !!proofUrl}
                      />
                      <input
                        placeholder="https://example.com/my-proof"
                        className="field"
                        value={proofUrl}
                        onChange={(e) => {
                          setProofUrl(e.target.value);
                          if (e.target.value) setFile(null);
                        }}
                        disabled={create.isPending}
                      />
                    </div>

                    <div className="pt-1 flex items-center gap-3">
                      <button
                        type="submit"
                        className="btn-primary disabled:opacity-60"
                        disabled={create.isPending || !canSend}
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
      </div>
    </div>
  );
}
