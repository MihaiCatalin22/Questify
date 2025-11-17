import { useParams, Link } from 'react-router-dom';
import { useSubmission, useReviewSubmission } from '../../hooks/useSubmissions';
import { useState, useEffect } from 'react';
import { useAuthContext } from '../../contexts/AuthContext';
import { toast } from 'react-hot-toast';
import http from '../../api/https';

// ---- helpers --------------------------------------------------------------

function hasReviewerRole(user?: any): boolean {
  const gather = [
    ...(user?.roles ?? []),
    ...(user?.realmRoles ?? []),
    ...((user?.resourceRoles?.['questify-frontend'] ?? [])),
    ...((user?.resourceRoles?.account ?? [])),
  ].filter(Boolean).map((r: any) => String(r).toUpperCase());

  const set = new Set(gather);
  return (
    set.has('ADMIN') ||
    set.has('REVIEWER') ||
    set.has('ROLE_ADMIN') ||
    set.has('ROLE_REVIEWER')
  );
}

/** Rewrite presigned MinIO URL to same-origin /s3/... */
function toSameOriginS3(url?: string | null): string | null {
  if (!url) return null;
  try {
    if (!/^https?:\/\//i.test(url)) return url;
    const u = new URL(url);
    const host = u.hostname;
    const port = u.port;
    const isMinioHost =
      (host === 'minio' || host === 'localhost' || host === '127.0.0.1' || host === 'host.docker.internal') &&
      (!port || port === '9000' || port === '9003');
    if (isMinioHost) return `/s3${u.pathname}${u.search || ''}`;
    return url;
  } catch { return url; }
}

/** Fixed-box inline renderer: try <img>, then <video>, then link */
function ProofInline({
  url,
  mediaType,
  height = 560,
}: { url: string; mediaType?: string | null; height?: number }) {
  const [mode, setMode] = useState<'img' | 'video' | 'link'>(
    mediaType?.startsWith('video/') ? 'video' : 'img'
  );

  const box: React.CSSProperties = {
    width: '100%',
    height,
    overflow: 'hidden',
    borderRadius: 12,
    border: '1px solid var(--border, #2f3545)',
  };
  const media: React.CSSProperties = {
    width: '100%',
    height: '100%',
    objectFit: 'contain',
    display: 'block',
  };

  if (mode === 'img') {
    return (
      <div style={box}>
        <img
          src={url}
          alt="proof"
          style={media}
          loading="lazy"
          referrerPolicy="no-referrer"
          onError={() => setMode('video')}
        />
      </div>
    );
  }
  if (mode === 'video') {
    return (
      <div style={box}>
        <video
          src={url}
          style={media}
          controls
          playsInline
          preload="metadata"
          onError={() => setMode('link')}
        />
      </div>
    );
  }
  return (
    <a className="link" href={url} target="_blank" rel="noreferrer">
      Open proof
    </a>
  );
}

// ---- page ----------------------------------------------------------------

export default function SubmissionDetail() {
  const { id } = useParams();
  const { data: s, isLoading, isError, error } = useSubmission(id ?? '');
  const review = useReviewSubmission(id ?? '');
  const { user } = useAuthContext();

  const [note, setNote] = useState('');
  const [displayUrl, setDisplayUrl] = useState<string | null>(null);
  const [contentType, setContentType] = useState<string | null>(null);
  const [fail, setFail] = useState<string | null>(null);

  const [mayReview, setMayReview] = useState<boolean | null>(null);
  useEffect(() => {
    let alive = true;
    (async () => {
      try {
        await http.get('/submissions/pending', { params: { size: 1 } });
        if (alive) setMayReview(true);
      } catch (e: any) {
        if (alive && e?.response?.status === 403) setMayReview(false);
      }
    })();
    return () => { alive = false; };
  }, []);
  const canReview = (mayReview ?? hasReviewerRole(user));

  const status = (s as any)?.reviewStatus ?? (s as any)?.status ?? 'PENDING';
  const existingNote = (s as any)?.reviewNote as string | undefined;

  useEffect(() => {
    let alive = true;
    (async () => {
      if (!id) return;
      try {
        const { data } = await http.get<{ url: string; expiresInSeconds: number }>(`/submissions/${id}/proof-url`);
        if (!alive) return;
        const sameOrigin = toSameOriginS3(data.url);
        setDisplayUrl(sameOrigin);
        setContentType((s as any)?.mediaType ?? null);
        setFail(null);
      } catch {
        const fallback = `/api/submissions/${id}/proof`;
        const sameOrigin = toSameOriginS3(fallback);
        if (!alive) return;
        setDisplayUrl(sameOrigin);
        setContentType((s as any)?.mediaType ?? null);
        setFail(null);
      }
    })();
    return () => { alive = false; };
  }, [id, s?.id]);

  if (isLoading) return <div className="p-6">Loading submission…</div>;
  if (isError || !s) return <div className="p-6 text-red-600">{(error as any)?.message || 'Failed to load submission'}</div>;

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
        <h1 className="text-2xl font-semibold">Submission</h1>
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
          {!fail && !displayUrl && <div className="text-sm text-gray-500">Generating secure link…</div>}

          {displayUrl && (
            <ProofInline url={displayUrl} mediaType={contentType ?? (s as any)?.mediaType} />
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
