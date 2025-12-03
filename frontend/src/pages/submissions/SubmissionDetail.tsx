import { useParams, Link } from 'react-router-dom';
import { useSubmission, useReviewSubmission } from '../../hooks/useSubmissions';
import { useState, useEffect, useRef } from 'react';
import { useAuthContext } from '../../contexts/AuthContext';
import { toast } from 'react-hot-toast';
import http from '../../api/https';

// ---- helpers --------------------------------------------------------------

/** Get the bearer token from our AuthContext. */
function getBearer(auth: ReturnType<typeof useAuthContext> | any): string | null {
  try {
    const fromFn = auth?.getAuthToken?.();
    const fromField = auth?.jwt;
    return fromFn || fromField || null;
  } catch {
    return auth?.jwt ?? null;
  }
}

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

/** IMPORTANT: Do NOT rewrite presigned URLs */
function toSameOriginS3(url?: string | null): string | null {
  if (!url) return null;
  return url;
}

async function looksLikeImage(blob: Blob): Promise<boolean> {
  try {
    const head = await blob.slice(0, 16).arrayBuffer();
    const b = new Uint8Array(head);
    if (b[0] === 0xff && b[1] === 0xd8) return true; // JPEG
    if (b[0] === 0x89 && b[1] === 0x50 && b[2] === 0x4e && b[3] === 0x47) return true; // PNG
    if (b[0] === 0x47 && b[1] === 0x49 && b[2] === 0x46 && b[3] === 0x38) return true; // GIF
    if (b[0] === 0x52 && b[1] === 0x49 && b[2] === 0x46 && b[3] === 0x46 && b[8] === 0x57 && b[9] === 0x45 && b[10] === 0x42 && b[11] === 0x50) return true; // WEBP
    return false;
  } catch {
    return false;
  }
}

async function materializeRenderableSrc(url: string): Promise<{ src: string; revoke?: () => void }> {
  const res = await fetch(url, {
    credentials: 'same-origin',
    cache: 'no-store',
    redirect: 'follow',
    headers: { Accept: 'image/avif,image/webp,image/apng,image/*,*/*;q=0.8' },
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);

  const blob = await res.blob();
  const ct = res.headers.get('content-type') ?? '';
  const imageish = /^image\//i.test(ct) || (await looksLikeImage(blob));
  if (!imageish) {
    let excerpt = '';
    try { excerpt = (await blob.text()).slice(0, 300); } catch {}
    throw new Error(excerpt ? `Non-image response:\n${excerpt}` : 'Non-image response (no preview)');
  }

  try {
    // @ts-ignore
    const bmp = await createImageBitmap(blob);
    const canvas = document.createElement('canvas');
    canvas.width = bmp.width || 1;
    canvas.height = bmp.height || 1;
    const ctx = canvas.getContext('2d');
    if (!ctx) throw new Error('No 2D context');
    ctx.drawImage(bmp, 0, 0);
    const dataUrl = canvas.toDataURL('image/png');
    if (bmp.close) try { bmp.close(); } catch {}
    return { src: dataUrl };
  } catch {
    const obj = URL.createObjectURL(blob);
    return { src: obj, revoke: () => URL.revokeObjectURL(obj) };
  }
}

/** Auth-required fallback: axios → blob URL with explicit Authorization header */
async function fetchProtectedProofAsObjectURL(submissionId: string, bearer?: string): Promise<{ src: string; revoke: () => void }> {
  const res = await http.get(`/submissions/${submissionId}/proof`, {
    responseType: 'blob',
    headers: bearer ? { Authorization: `Bearer ${bearer}` } : undefined,
  });
  const obj = URL.createObjectURL(res.data as Blob);
  return { src: obj, revoke: () => URL.revokeObjectURL(obj) };
}

/** Image-only renderer */
function ProofImage({
  url,
  height = 560,
  eager = false,
}: { url: string; height?: number; eager?: boolean }) {
  const box: React.CSSProperties = {
    width: '100%',
    height,
    overflow: 'hidden',
    borderRadius: 12,
    border: '1px solid var(--border, #2f3545)',
    background: 'var(--card, #0f1115)',
  };
  const media: React.CSSProperties = {
    width: '100%',
    height: '100%',
    objectFit: 'contain',
    display: 'block',
  };

  const [src, setSrc] = useState(url);
  const [triedFallback, setTriedFallback] = useState(false);
  const [debug, setDebug] = useState<string | null>(null);
  const revokeRef = useRef<(() => void) | null>(null);

  useEffect(() => {
    setSrc(url);
    setDebug(null);
    setTriedFallback(false);
    if (revokeRef.current) { revokeRef.current(); revokeRef.current = null; }
  }, [url]);

  useEffect(() => {
    return () => { if (revokeRef.current) revokeRef.current(); };
  }, []);

  const handleError = async () => {
    try {
      if (triedFallback) {
        setDebug((d) => d ?? 'Image decode failed after fallback.');
        return;
      }
      setTriedFallback(true);
      const { src: safeSrc, revoke } = await materializeRenderableSrc(url);
      if (revokeRef.current) revokeRef.current();
      revokeRef.current = revoke ?? null;
      setSrc(safeSrc);
      setDebug(null);
    } catch (e: any) {
      setDebug(String(e?.message || e) || 'Failed to fetch image.');
    }
  };

  return (
    <div style={box}>
      {!debug ? (
        <img
          src={src}
          alt="proof"
          style={media}
          loading={eager ? 'eager' : 'lazy'}
          decoding="async"
          onError={handleError}
        />
      ) : (
        <div
          style={{
            ...media,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: 13,
            opacity: 0.8,
            whiteSpace: 'pre-wrap',
            padding: 16,
            textAlign: 'center',
          }}
          title={debug}
        >
          Unable to display image
          {debug ? `\n\n${debug}` : null}
        </div>
      )}
    </div>
  );
}

// ---- page ----------------------------------------------------------------

export default function SubmissionDetail() {
  const { id } = useParams();
  const { data: s, isLoading, isError, error } = useSubmission(id ?? '');
  const review = useReviewSubmission(id ?? '');
  const auth = useAuthContext();
  const token = getBearer(auth);
  const { user } = auth;

  const [note, setNote] = useState('');
  const [displayUrl, setDisplayUrl] = useState<string | null>(null);
  const [fail, setFail] = useState<string | null>(null);
  const revokeRef = useRef<(() => void) | null>(null);

  const [mayReview, setMayReview] = useState<boolean | null>(null);
  useEffect(() => {
    let alive = true;
    (async () => {
      try {
        await http.get('/submissions/pending', {
          params: { size: 1 },
          headers: token ? { Authorization: `Bearer ${token}` } : undefined
        });
        if (alive) setMayReview(true);
      } catch (e: any) {
        if (alive && e?.response?.status === 403) setMayReview(false);
      }
    })();
    return () => { alive = false; };
  }, [token]);
  const canReview = (mayReview ?? hasReviewerRole(user));

  const status = (s as any)?.reviewStatus ?? (s as any)?.status ?? 'PENDING';
  const existingNote = (s as any)?.reviewNote as string | undefined;

  useEffect(() => {
    let alive = true;
    (async () => {
      if (!id) return;

      if (revokeRef.current) { revokeRef.current(); revokeRef.current = null; }
      setDisplayUrl(null);
      setFail(null);

      // Prefer presigned URL — attach Authorization explicitly
      try {
        const { data } = await http.get<{ url: string; expiresInSeconds: number }>(
          `/submissions/${id}/proof-url`,
          { headers: token ? { Authorization: `Bearer ${token}` } : undefined }
        );
        if (!alive) return;
        setDisplayUrl(toSameOriginS3(data.url));
        return;
      } catch {
        // fall through
      }

      // Auth fallback: axios -> blob URL
      try {
        const { src, revoke } = await fetchProtectedProofAsObjectURL(String(id), token ?? undefined);
        if (!alive) return;
        revokeRef.current = revoke;
        setDisplayUrl(src);
      } catch (e: any) {
        if (!alive) return;
        setFail(String(e?.message || e) || 'Failed to fetch proof');
      }
    })();

    return () => {
      alive = false;
      if (revokeRef.current) { revokeRef.current(); revokeRef.current = null; }
    };
  }, [id, s?.id, token]);

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
            <ProofImage url={displayUrl} eager />
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
