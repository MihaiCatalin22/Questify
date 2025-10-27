import { useParams, Link } from 'react-router-dom';
import { useSubmission, useReviewSubmission } from '../../hooks/useSubmissions';
import { useEffect, useMemo, useState } from 'react';
import http from '../../api/https';
import { useAuthContext } from '../../contexts/AuthContext';
import { toast } from 'react-hot-toast';

function isImageType(mt?: string | null) { return !!mt && mt.startsWith('image/'); }
function isVideoType(mt?: string | null) { return !!mt && mt.startsWith('video/'); }

export default function SubmissionDetail() {
  const { id } = useParams();
  const { data: s, isLoading, isError, error } = useSubmission(id ?? '');
  const review = useReviewSubmission(id ?? '');
  const { user } = useAuthContext();

  const [signedUrl, setSignedUrl] = useState<string | null>(null);
  const [mediaType, setMediaType] = useState<string | null>(null);
  const [fail, setFail] = useState<string | null>(null);
  const [note, setNote] = useState('');

  const status = (s as any)?.reviewStatus ?? (s as any)?.status ?? 'PENDING';
  const existingNote = (s as any)?.reviewNote as string | undefined;

  const canReview = useMemo(() => {
    const roles = (user?.roles ?? []) as string[];
    return roles.includes('ADMIN') || roles.includes('REVIEWER');
  }, [user]);

  useEffect(() => {
    let alive = true;
    async function run() {
      if (!id) return;
      try {
        const { data } = await http.get<{ url: string; expiresInSeconds: number }>(`/submissions/${id}/proof-url`);
        if (!alive) return;
        setSignedUrl(data.url);
        const mt = (s as any)?.mediaType as string | undefined;
        setMediaType(mt ?? null);
      } catch (e: any) {
        if (!alive) return;
        setFail(e?.response?.data?.error || e?.message || 'Failed to fetch proof URL');
      }
    }
    run();
    return () => { alive = false; };
  }, [id, s]);

  if (isLoading) return <div className="p-6">Loading submission…</div>;
  if (isError || !s) return <div className="p-6 text-red-600">{(error as any)?.message || 'Failed to load submission'}</div>;

  const img = isImageType(mediaType);
  const vid = isVideoType(mediaType);

  const onApprove = async () => {
    if (!id) return;
    try {
      await review.mutateAsync({ reviewStatus: 'APPROVED' as const });
      toast.success('Submission approved successfully');
      setNote('');
    } catch (e: any) {
      toast.error(e?.response?.data?.message || e?.message || 'Failed to approve submission');
    }
  };

  const onReject = async () => {
    if (!id) return;
    if (!note.trim()) {
      toast.error('Please add a review note when rejecting.');
      return;
    }
    try {
      await review.mutateAsync({ reviewStatus: 'REJECTED' as const, reviewNote: note.trim() });
      toast.success('Submission rejected');
      setNote('');
    } catch (e: any) {
      toast.error(e?.response?.data?.message || e?.message || 'Failed to reject submission');
    }
  };

  return (
    <div className="p-6 space-y-5 max-w-3xl">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Submission {s.id}</h1>
        <Link to={`/quests/${s.questId}`} className="underline text-sm">View Quest</Link>
      </div>

      <div className="card">
        <div className="card-body space-y-2">
          <div className="text-sm opacity-70">Quest ID: {s.questId}</div>
          <div className="text-sm opacity-70">User ID: {s.userId ?? '—'}</div>

          <div className="mt-1">
            <span className="font-medium">Status: </span>
            <span className="pill" style={{ marginLeft: 6 }}>{status}</span>
          </div>

          {existingNote && (
            <div className="text-sm">
              <span className="font-medium">Review note:</span>{' '}
              <span>{existingNote}</span>
            </div>
          )}

          <div className="text-xs muted">
            Created: {new Date(s.createdAt).toLocaleString()}
          </div>

          {s.comment && <p className="mt-2">{s.comment}</p>}
        </div>
      </div>

      <section className="card">
        <div className="card-body space-y-3">
          <h2 className="section-title">Proof</h2>

          {fail && <div className="text-sm text-red-600">{fail}</div>}

          {!fail && !signedUrl && (
            <div className="text-sm muted">Generating secure link…</div>
          )}

          {signedUrl && img && (
            <img
              src={signedUrl}
              alt="proof"
              className="rounded-xl border"
              style={{ maxWidth: '100%', maxHeight: 560, objectFit: 'contain' }}
              loading="lazy"
              referrerPolicy="no-referrer"
            />
          )}

          {signedUrl && vid && (
            <video
              className="rounded-xl border"
              style={{ maxWidth: '100%', maxHeight: 560 }}
              src={signedUrl}
              controls
              playsInline
              preload="metadata"
            />
          )}

          {signedUrl && !img && !vid && (
            <a className="link" href={signedUrl} target="_blank" rel="noreferrer">
              Open proof
            </a>
          )}
        </div>
      </section>

      {canReview && status === 'PENDING' && (
        <section className="card">
          <div className="card-body space-y-3">
            <h2 className="section-title">Review</h2>

            <div>
              <label className="label">Review note (required for rejection)</label>
              <textarea
                className="field"
                placeholder="Add feedback for the submitter…"
                value={note}
                onChange={(e) => setNote(e.target.value)}
                maxLength={500}
              />
            </div>

            <div className="flex gap-2">
              <button
                className="btn-primary"
                onClick={onApprove}
                disabled={review.isPending}
              >
                {review.isPending ? 'Approving…' : 'Approve'}
              </button>

              <button
                className="rounded-2xl border px-3 py-2"
                onClick={onReject}
                disabled={review.isPending}
                title="Requires a note"
              >
                {review.isPending ? 'Rejecting…' : 'Reject'}
              </button>
            </div>
          </div>
        </section>
      )}
    </div>
  );
}
