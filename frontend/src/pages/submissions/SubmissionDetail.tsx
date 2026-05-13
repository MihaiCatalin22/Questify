import { useParams, Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useSubmission, useReviewSubmission } from '../../hooks/useSubmissions';
import { useState, useEffect, useRef } from 'react';
import { useAuthContext } from '../../contexts/useAuthContext';
import { toast } from 'react-hot-toast';
import http from '../../api/https';
import { AiReviewsApi, type AiReviewRecommendation } from '../../api/aiReviews';
import type { SubmissionDTO, SubmissionStatus } from '../../types/submission';
import { getErrorMessage, getResponseStatus } from '../../utils/errors';
import {
  Badge,
  Button,
  ErrorState,
  FieldLabel,
  LoadingState,
  PageHeader,
  PageShell,
  Panel,
  StatusBadge,
  TextArea,
} from '../../components/ui';

type SubmissionReviewView = SubmissionDTO & {
  reviewStatus?: SubmissionStatus;
  reviewNote?: string;
};

function getBearer(auth: ReturnType<typeof useAuthContext>): string | null {
  return auth.getAuthToken() || auth.jwt || null;
}

function hasReviewerRole(user?: { roles?: string[] } | null): boolean {
  const gather = (user?.roles ?? []).map((role) => role.toUpperCase());

  const set = new Set(gather);
  return (
    set.has('ADMIN') ||
    set.has('REVIEWER') ||
    set.has('ROLE_ADMIN') ||
    set.has('ROLE_REVIEWER')
  );
}

function toSameOriginS3(url?: string | null): string | null {
  if (!url) return null;
  return url;
}

function recommendationLabel(value: AiReviewRecommendation) {
  return value.replaceAll('_', ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase());
}

function recommendationTone(value: AiReviewRecommendation): React.ComponentProps<typeof Badge>['tone'] {
  if (value === 'LIKELY_VALID') return 'success';
  if (value === 'LIKELY_INVALID' || value === 'AI_FAILED') return 'danger';
  if (value === 'UNCLEAR' || value === 'UNSUPPORTED_MEDIA') return 'warning';
  return undefined;
}

function parseSupportScore(reasons?: string[]): number | null {
  if (!reasons || reasons.length === 0) return null;
  const scoreLine = reasons.find((line) => /support score/i.test(line));
  if (!scoreLine) return null;
  const match = scoreLine.match(/([01](?:\.\d+)?)/);
  if (!match) return null;
  const value = Number(match[1]);
  if (!Number.isFinite(value)) return null;
  return Math.max(0, Math.min(1, value));
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
    try {
      excerpt = (await blob.text()).slice(0, 300);
    } catch {
      excerpt = '';
    }
    throw new Error(excerpt ? `Non-image response:\n${excerpt}` : 'Non-image response (no preview)');
  }

  try {
    const bmp = await createImageBitmap(blob);
    const canvas = document.createElement('canvas');
    canvas.width = bmp.width || 1;
    canvas.height = bmp.height || 1;
    const ctx = canvas.getContext('2d');
    if (!ctx) throw new Error('No 2D context');
    ctx.drawImage(bmp, 0, 0);
    const dataUrl = canvas.toDataURL('image/png');
    bmp.close();
    return { src: dataUrl };
  } catch {
    const obj = URL.createObjectURL(blob);
    return { src: obj, revoke: () => URL.revokeObjectURL(obj) };
  }
}

async function fetchProtectedProofAsObjectURL(submissionId: string, bearer?: string): Promise<{ src: string; revoke: () => void }> {
  const res = await http.get(`/submissions/${submissionId}/proof`, {
    responseType: 'blob',
    headers: bearer ? { Authorization: `Bearer ${bearer}` } : undefined,
  });
  const obj = URL.createObjectURL(res.data as Blob);
  return { src: obj, revoke: () => URL.revokeObjectURL(obj) };
}

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
    } catch (e: unknown) {
      setDebug(getErrorMessage(e, 'Failed to fetch image.'));
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

export default function SubmissionDetail() {
  const { id } = useParams();
  const { data: s, isLoading, isError, error } = useSubmission(id ?? '');
  const submission = s as SubmissionReviewView | undefined;
  const loadedSubmissionId = submission?.id;
  const review = useReviewSubmission(id ?? '');
  const auth = useAuthContext();
  const token = getBearer(auth);
  const { user } = auth;
  const qc = useQueryClient();

  const [note, setNote] = useState('');
  const [displayUrls, setDisplayUrls] = useState<string[]>([]);
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
      } catch (e: unknown) {
        if (alive && getResponseStatus(e) === 403) setMayReview(false);
      }
    })();
    return () => { alive = false; };
  }, [token]);
  const canReview = (mayReview ?? hasReviewerRole(user));
  const aiReview = useQuery({
    queryKey: ['ai-review', id],
    queryFn: () => AiReviewsApi.getForSubmission(id ?? ''),
    enabled: Boolean(id && canReview),
    retry: false,
    refetchOnWindowFocus: false,
  });

  const runAiReview = useMutation({
    mutationFn: async () => {
      if (!id) throw new Error('Missing submission id');
      return AiReviewsApi.runForSubmission(id);
    },
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ['ai-review', id] });
      toast.success('AI review refreshed');
    },
    onError: (e: unknown) => {
      toast.error(getErrorMessage(e, 'Failed to run AI review'));
    },
  });

  const status = submission?.reviewStatus ?? submission?.status ?? 'PENDING';
  const existingNote = submission?.reviewNote;

  useEffect(() => {
    let alive = true;
    (async () => {
      if (!id) return;

      if (revokeRef.current) { revokeRef.current(); revokeRef.current = null; }
      setDisplayUrls([]);
      setFail(null);

      // 1) Optional multi endpoint: /submissions/:id/proof-urls -> { urls: string[] }
      try {
        const { data } = await http.get<{ urls: string[]; expiresInSeconds: number }>(
          `/submissions/${id}/proof-urls`,
          { headers: token ? { Authorization: `Bearer ${token}` } : undefined }
        );
        const urls = (data?.urls ?? []).map(toSameOriginS3).filter(Boolean) as string[];
        if (alive && urls.length) {
          setDisplayUrls(urls);
          return;
        }
      } catch {
        // fall through
      }

      // 2) Existing single endpoint: /submissions/:id/proof-url
      try {
        const { data } = await http.get<{ url: string; expiresInSeconds: number }>(
          `/submissions/${id}/proof-url`,
          { headers: token ? { Authorization: `Bearer ${token}` } : undefined }
        );
        if (!alive) return;
        const one = toSameOriginS3(data.url);
        if (one) setDisplayUrls([one]);
        return;
      } catch {
        // fall through
      }

      // 3) Auth fallback for primary proof only
      try {
        const { src, revoke } = await fetchProtectedProofAsObjectURL(String(id), token ?? undefined);
        if (!alive) return;
        revokeRef.current = revoke;
        setDisplayUrls([src]);
      } catch (e: unknown) {
        if (!alive) return;
        setFail(getErrorMessage(e, 'Failed to fetch proof'));
      }
    })();

    return () => {
      alive = false;
      if (revokeRef.current) { revokeRef.current(); revokeRef.current = null; }
    };
  }, [id, loadedSubmissionId, token]);

  if (isLoading) return <LoadingState label="Loading submission..." />;
  if (isError || !submission) return <ErrorState message={getErrorMessage(error, 'Failed to load submission')} />;

  const onApprove = async () => {
    if (!id) return;
    try {
      await review.mutateAsync({ reviewStatus: 'APPROVED' as const });
      toast.success('Submission approved successfully');
      setNote('');
    } catch (e: unknown) {
      toast.error(getErrorMessage(e, 'Failed to approve submission'));
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
    } catch (e: unknown) {
      toast.error(getErrorMessage(e, 'Failed to reject submission'));
    }
  };

  return (
    <PageShell className="max-w-4xl">
      <PageHeader
        title="Submission"
        description="Review the submitted proof, status, and related quest."
        actions={<Link to={`/quests/${submission.questId}`} className="btn btn-secondary">View Quest</Link>}
      />

      <Panel>
        <div className="card-body space-y-2">
          <div className="text-sm text-[rgb(var(--muted))]">Quest ID: {submission.questId}</div>
          <div className="text-sm text-[rgb(var(--muted))]">User ID: {submission.userId ?? '-'}</div>

          <div className="mt-2 flex items-center gap-2">
            <span className="font-medium">Status</span>
            <StatusBadge status={status} />
          </div>

          {existingNote && (
            <div className="text-sm">
              <span className="font-medium">Review note:</span>{' '}
              <span>{existingNote}</span>
            </div>
          )}

          <div className="text-xs text-[rgb(var(--faint))]">
            Created: {new Date(submission.createdAt).toLocaleString()}
          </div>

          {submission.comment && <p className="mt-2 text-sm leading-6 text-[rgb(var(--muted))]">{submission.comment}</p>}
        </div>
      </Panel>

      <Panel>
        <div className="card-body space-y-3">
          <h2 className="section-title">Proof</h2>

          {fail && <div className="text-sm text-red-300">{fail}</div>}
          {!fail && displayUrls.length === 0 && <div className="text-sm text-[rgb(var(--muted))]">Generating secure link...</div>}

          {displayUrls.length > 0 && (
            <div className="space-y-4">
              {displayUrls.map((u, idx) => (
                <div key={`${u}-${idx}`}>
                  {displayUrls.length > 1 ? (
                    <Badge className="mb-2">Proof {idx + 1} of {displayUrls.length}</Badge>
                  ) : null}
                  <ProofImage url={u} eager={idx === 0} height={520} />
                </div>
              ))}
            </div>
          )}
        </div>
      </Panel>

      {canReview && (
        <Panel>
          <div className="card-body space-y-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h2 className="section-title">AI review</h2>
              <div className="flex items-center gap-2">
                {aiReview.data?.modelName && (
                  <Badge>{aiReview.data.modelName}</Badge>
                )}
                <Button
                  variant="ghost"
                  onClick={() => runAiReview.mutate()}
                  disabled={runAiReview.isPending || !id}
                >
                  {runAiReview.isPending ? 'Running AI review...' : 'Run AI Review'}
                </Button>
              </div>
            </div>

            {aiReview.isLoading && (
              <div className="text-sm text-[rgb(var(--muted))]">AI review is running...</div>
            )}

            {aiReview.isError && getResponseStatus(aiReview.error) === 404 && (
              <div className="text-sm text-[rgb(var(--muted))]">
                AI review is not ready yet. Use “Run AI Review” or continue manual review.
              </div>
            )}

            {aiReview.isError && getResponseStatus(aiReview.error) !== 404 && (
              <div className="text-sm text-red-300">
                Failed to load AI review. Manual review is still available.
              </div>
            )}

            {aiReview.data && (
              <div className="space-y-3">
                <div className="rounded-md border border-[rgb(var(--border-soft))] bg-[rgb(var(--surface-2))] p-3 text-xs leading-6 text-[rgb(var(--muted))]">
                  <div className="font-semibold text-[rgb(var(--text))]">How to read this</div>
                  <div>AI review is advisory evidence only. Final decision is always the reviewer’s approve/reject action.</div>
                  <div className="mt-1">
                    Confidence/support scale: <span className="font-medium">0.00 to 1.00</span> (shown as 0% to 100%).
                  </div>
                </div>

                <div className="flex flex-wrap items-center gap-2">
                  <Badge tone={recommendationTone(aiReview.data.recommendation)}>
                    {recommendationLabel(aiReview.data.recommendation)}
                  </Badge>
                  <span className="text-sm text-[rgb(var(--muted))]">
                    Confidence {Math.round((aiReview.data.confidence ?? 0) * 100)}%
                  </span>
                  {parseSupportScore(aiReview.data.reasons) !== null && (
                    <span className="text-sm text-[rgb(var(--muted))]">
                      Support {Math.round((parseSupportScore(aiReview.data.reasons) ?? 0) * 100)}%
                    </span>
                  )}
                </div>

                {aiReview.data.recommendation === 'UNSUPPORTED_MEDIA' && (
                  <div className="text-sm text-[rgb(var(--muted))]">
                    This proof type needs manual review because the AI reviewer only checks image evidence in v1.
                  </div>
                )}

                {aiReview.data.generatedPolicy && (
                  <div className="text-sm text-amber-300">
                    This recommendation used an auto-generated verification policy from quest text and is conservative.
                  </div>
                )}

                {!!aiReview.data.decisionPath && (
                  <div className="text-xs text-[rgb(var(--faint))]">
                    Decision path: {aiReview.data.decisionPath}
                  </div>
                )}

                {(aiReview.data.modelUsed || aiReview.data.fallbackUsed) && (
                  <div className="text-xs text-[rgb(var(--faint))]">
                    Model used: {aiReview.data.modelUsed || aiReview.data.modelName || 'n/a'}
                    {aiReview.data.fallbackUsed ? ` (fallback)` : ''}
                    {aiReview.data.fallbackReason ? ` — ${aiReview.data.fallbackReason}` : ''}
                  </div>
                )}

                {!!aiReview.data.reasons?.length && (
                  <ul className="list-disc space-y-1 pl-5 text-sm leading-6 text-[rgb(var(--muted))]">
                    {aiReview.data.reasons.map((reason, idx) => (
                      <li key={`${reason}-${idx}`}>{reason}</li>
                    ))}
                  </ul>
                )}

                <div className="rounded-md border border-[rgb(var(--border-soft))] p-3 text-xs leading-6 text-[rgb(var(--muted))]">
                  <div><span className="font-semibold text-[rgb(var(--text))]">Matched evidence:</span> Signals from quest policy that AI found in proof/text.</div>
                  <div><span className="font-semibold text-[rgb(var(--text))]">Missing evidence:</span> Signals expected by policy but not found in proof/text.</div>
                  <div><span className="font-semibold text-[rgb(var(--text))]">Disqualifiers:</span> Contradicting signals (for example unrelated/game-like content).</div>
                  <div><span className="font-semibold text-[rgb(var(--text))]">OCR snippets:</span> Text extracted from the uploaded image and used as evidence.</div>
                </div>

                <div className="grid gap-3 md:grid-cols-2">
                  <div>
                    <div className="text-xs font-semibold uppercase tracking-wide text-[rgb(var(--faint))]">Matched evidence</div>
                    <ul className="mt-1 list-disc space-y-1 pl-5 text-sm leading-6 text-[rgb(var(--muted))]">
                      {(aiReview.data.matchedEvidence ?? []).map((item, idx) => (
                        <li key={`match-${idx}`}>{item}</li>
                      ))}
                      {(aiReview.data.matchedEvidence ?? []).length === 0 && <li>None</li>}
                    </ul>
                  </div>
                  <div>
                    <div className="text-xs font-semibold uppercase tracking-wide text-[rgb(var(--faint))]">Missing evidence</div>
                    <ul className="mt-1 list-disc space-y-1 pl-5 text-sm leading-6 text-[rgb(var(--muted))]">
                      {(aiReview.data.missingEvidence ?? []).map((item, idx) => (
                        <li key={`missing-${idx}`}>{item}</li>
                      ))}
                      {(aiReview.data.missingEvidence ?? []).length === 0 && <li>None</li>}
                    </ul>
                  </div>
                  <div>
                    <div className="text-xs font-semibold uppercase tracking-wide text-[rgb(var(--faint))]">Disqualifiers</div>
                    <ul className="mt-1 list-disc space-y-1 pl-5 text-sm leading-6 text-[rgb(var(--muted))]">
                      {(aiReview.data.matchedDisqualifiers ?? []).map((item, idx) => (
                        <li key={`disq-${idx}`}>{item}</li>
                      ))}
                      {(aiReview.data.matchedDisqualifiers ?? []).length === 0 && <li>None</li>}
                    </ul>
                  </div>
                  <div>
                    <div className="text-xs font-semibold uppercase tracking-wide text-[rgb(var(--faint))]">OCR snippets</div>
                    <ul className="mt-1 list-disc space-y-1 pl-5 text-sm leading-6 text-[rgb(var(--muted))]">
                      {(aiReview.data.ocrSnippets ?? []).map((item, idx) => (
                        <li key={`ocr-${idx}`}>{item}</li>
                      ))}
                      {(aiReview.data.ocrSnippets ?? []).length === 0 && <li>None</li>}
                    </ul>
                  </div>
                </div>

                {aiReview.data.reviewedAt && (
                  <div className="text-xs text-[rgb(var(--faint))]">
                    Reviewed: {new Date(aiReview.data.reviewedAt).toLocaleString()}
                  </div>
                )}
              </div>
            )}
          </div>
        </Panel>
      )}

      {canReview && status === 'PENDING' && (
        <Panel>
          <div className="card-body space-y-3">
            <h2 className="section-title">Review</h2>

            <div>
              <FieldLabel>Review note (required for rejection)</FieldLabel>
              <TextArea
                placeholder="Add feedback for the submitter..."
                value={note}
                onChange={(e) => setNote(e.target.value)}
                maxLength={500}
              />
            </div>

            <div className="flex gap-2">
              <Button
                variant="primary"
                onClick={onApprove}
                disabled={review.isPending}
              >
                {review.isPending ? 'Approving...' : 'Approve'}
              </Button>

              <Button
                onClick={onReject}
                disabled={review.isPending}
                title="Requires a note"
              >
                {review.isPending ? 'Rejecting...' : 'Reject'}
              </Button>
            </div>
          </div>
        </Panel>
      )}
    </PageShell>
  );
}
