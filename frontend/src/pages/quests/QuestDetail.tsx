import React, { useState, useMemo, useEffect, useRef } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useQuest, useJoinQuest, useLeaveQuest, useMyQuests } from '../../hooks/useQuests';
import { useCreateSubmission, useSubmissionsForQuest } from '../../hooks/useSubmissions';
import { useAuthContext } from '../../contexts/useAuthContext';
import { toast } from 'react-hot-toast';
import http from '../../api/https';
import type { SubmissionDTO } from '../../types/submission';
import type { QuestDTO, QuestStatus, QuestVisibility } from '../../types/quest';
import { CalendarDays, CheckCircle2, Image as ImageIcon, UploadCloud, Users, X } from 'lucide-react';
import { getErrorMessage } from '../../utils/errors';
import {
  Badge,
  Button,
  EmptyState,
  ErrorState,
  FieldLabel,
  LoadingState,
  PageHeader,
  PageShell,
  Panel,
  StatusBadge,
  TextArea,
} from '../../components/ui';

type QuestDetailView = QuestDTO & {
  createdBy?: { id?: string | number };
  createdById?: string | number;
  participants?: unknown[];
};

type SubmissionView = SubmissionDTO & {
  reviewStatus?: SubmissionDTO['status'];
  closed?: boolean;
};

function getBearer(auth: ReturnType<typeof useAuthContext>): string | null {
  return auth.getAuthToken() || auth.jwt || null;
}

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
    if (
      b[0] === 0x52 &&
      b[1] === 0x49 &&
      b[2] === 0x46 &&
      b[3] === 0x46 &&
      b[8] === 0x57 &&
      b[9] === 0x45 &&
      b[10] === 0x42 &&
      b[11] === 0x50
    )
      return true; // WEBP
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

/**
 * Strips EXIF/GPS/time metadata by re-encoding via Canvas.
 * - Resizes if needed
 * - Tries to preserve original format (png/webp/jpeg)
 * - Keeps GIF as-is (avoid breaking animation)
 */
async function sanitizeImageForUpload(
  file: File,
  targetBytes = 0.8 * 1024 * 1024,
  maxSide = 1800
): Promise<File> {
  try {
    // Keep GIFs as-is (canvas would flatten / break animation).
    if (/^image\/gif$/i.test(file.type)) return file;

    const bmp = await createImageBitmap(file);

    const ratio = Math.min(1, maxSide / Math.max(bmp.width, bmp.height));
    const w = Math.max(1, Math.round(bmp.width * ratio));
    const h = Math.max(1, Math.round(bmp.height * ratio));

    const canvas = document.createElement('canvas');
    canvas.width = w;
    canvas.height = h;
    const ctx = canvas.getContext('2d');
    if (!ctx) return file;
    ctx.drawImage(bmp, 0, 0, w, h);

    bmp.close();

    const inputType = (file.type || '').toLowerCase();
    const preferredType =
      inputType === 'image/png' ? 'image/png'
      : inputType === 'image/webp' ? 'image/webp'
      : 'image/jpeg';

    const makeBlob = (type: string, quality?: number) =>
      new Promise<Blob | null>((res) => canvas.toBlob(res, type, quality));

    let outType = preferredType;

    let q = 0.9;
    let blob = await makeBlob(outType, outType === 'image/jpeg' ? q : undefined);

    // Fallback if browser can't encode preferred type
    if (!blob) {
      outType = 'image/jpeg';
      blob = await makeBlob(outType, q);
    }
    if (!blob) return file;

    // Only JPEG supports quality tuning. PNG ignores it.
    if (outType === 'image/jpeg') {
      while (blob.size > targetBytes && q > 0.5) {
        q -= 0.1;
        const b2 = await makeBlob(outType, q);
        if (!b2) break;
        blob = b2;
      }
    }

    const ext =
      outType === 'image/png' ? '.png'
      : outType === 'image/webp' ? '.webp'
      : '.jpg';

    const base = file.name.replace(/\.(png|webp|gif|jpeg|jpg)$/i, '');
    return new File([blob], `${base}${ext}`, { type: outType });
  } catch {
    return file;
  }
}

async function fetchProtectedProofAsObjectURL(
  submissionId: string,
  bearer?: string
): Promise<{ src: string; revoke: () => void }> {
  const res = await http.get(`/submissions/${submissionId}/proof`, {
    responseType: 'blob',
    headers: bearer ? { Authorization: `Bearer ${bearer}` } : undefined,
  });
  const blob = res.data as Blob;
  const obj = URL.createObjectURL(blob);
  return { src: obj, revoke: () => URL.revokeObjectURL(obj) };
}

function ProofImage({
  url,
  height = 560,
  eager = false,
}: {
  url: string;
  height?: number;
  eager?: boolean;
}) {
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
    if (revokeRef.current) {
      revokeRef.current();
      revokeRef.current = null;
    }
  }, [url]);

  useEffect(() => {
    return () => {
      if (revokeRef.current) revokeRef.current();
    };
  }, []);

  const handleError = async () => {
    if (triedFallback) {
      setDebug((d) => d ?? 'Image decode failed after fallback.');
      return;
    }
    setTriedFallback(true);
    try {
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

/** Collapsed by default, expandable to show all proofs (like SubmissionDetail) */
function SubmissionPreview({ submission }: { submission: SubmissionDTO }) {
  const auth = useAuthContext();
  const token = getBearer(auth);

  const [displayUrls, setDisplayUrls] = useState<string[]>([]);
  const revokeRef = useRef<(() => void) | null>(null);

  const [expanded, setExpanded] = useState(false);

  useEffect(() => {
    setExpanded(false);
  }, [submission?.id]);

  useEffect(() => {
    let alive = true;
    (async () => {
      if (!submission?.id) return;

      if (revokeRef.current) {
        revokeRef.current();
        revokeRef.current = null;
      }
      setDisplayUrls([]);

      // 1) Multi-proof endpoint
      try {
        const { data } = await http.get<{ urls: string[]; expiresInSeconds: number }>(
          `/submissions/${submission.id}/proof-urls`,
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

      // 2) Single-proof endpoint
      try {
        const { data } = await http.get<{ url: string; expiresInSeconds: number }>(
          `/submissions/${submission.id}/proof-url`,
          { headers: token ? { Authorization: `Bearer ${token}` } : undefined }
        );
        if (!alive) return;
        const one = toSameOriginS3(data.url);
        setDisplayUrls(one ? [one] : []);
        return;
      } catch {
        // fall through
      }

      // 3) Auth fallback (primary proof only)
      try {
        const { src, revoke } = await fetchProtectedProofAsObjectURL(
          String(submission.id),
          token ?? undefined
        );
        if (!alive) return;
        revokeRef.current = revoke;
        setDisplayUrls([src]);
      } catch {
        if (!alive) return;
        setDisplayUrls([]);
      }
    })();

    return () => {
      alive = false;
      if (revokeRef.current) {
        revokeRef.current();
        revokeRef.current = null;
      }
    };
  }, [submission?.id, token]);

  if (displayUrls.length === 0) {
    return <div className="text-sm text-[rgb(var(--muted))]">Generating secure link...</div>;
  }

  const total = displayUrls.length;
  const extra = Math.max(0, total - 1);

  return (
    <div className="mt-3 space-y-2">
      <div className="flex items-center justify-between gap-3">
        <div className="font-semibold text-sm">
          Proof{!expanded && extra > 0 ? ` (+${extra} more)` : ''}
        </div>

        {total > 1 && (
          <button
            type="button"
            className="text-xs text-[rgb(var(--accent))] underline-offset-4 hover:underline"
            onClick={() => setExpanded((v) => !v)}
          >
            {expanded ? 'Hide' : `Show all (${total})`}
          </button>
        )}
      </div>

      {!expanded ? (
        <ProofImage url={displayUrls[0]} eager height={520} />
      ) : (
        <div className="space-y-4">
          {displayUrls.map((u, idx) => (
            <div key={`${u}-${idx}`}>
              {total > 1 ? (
                <div className="text-xs opacity-70 mb-2">
                  Proof {idx + 1} of {total}
                </div>
              ) : null}
              <ProofImage url={u} eager={idx === 0} height={520} />
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function InfoModal({
  open,
  onClose,
  title,
  children,
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  children: React.ReactNode;
}) {
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/50" onClick={onClose} />
      <Panel className="relative z-10 w-full max-w-md p-5">
        <div className="flex items-start justify-between">
          <h3 className="text-lg font-semibold">{title}</h3>
          <Button variant="ghost" className="h-9 px-2" onClick={onClose} aria-label="Close dialog">
            <X className="h-4 w-4" />
          </Button>
        </div>
        <div className="mt-3 text-sm">{children}</div>
        <div className="mt-5 text-right">
          <Button onClick={onClose}>
            OK
          </Button>
        </div>
      </Panel>
    </div>
  );
}

export default function QuestDetail() {
  const { id } = useParams();
  const rawId = id ?? '';
  const safeQuestId = /^\d+$/.test(rawId) ? rawId : '';

  const auth = useAuthContext();
  const { user } = auth;

  const { data: quest, isLoading, isError, error } = useQuest(safeQuestId);
  const questView = quest as QuestDetailView | undefined;
  const { data: submissions } = useSubmissionsForQuest(safeQuestId);
  const { data: myQuests } = useMyQuests();
  const create = useCreateSubmission(safeQuestId);

  const join = useJoinQuest();
  const leave = useLeaveQuest();

  const [comment, setComment] = useState('');
  const [files, setFiles] = useState<File[]>([]);
  const [batchSubmitting, setBatchSubmitting] = useState(false);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const [localJoined, setLocalJoined] = useState<boolean | null>(null);
  const [showOwnerLeaveModal, setShowOwnerLeaveModal] = useState(false);

  const ownerId =
    questView?.createdByUserId ??
    questView?.createdBy?.id ??
    questView?.createdById ??
    null;

  const isOwner = !!(user && ownerId && String(user.id) === String(ownerId));
  const joinedFromServer =
    isOwner || !!myQuests?.some((q) => String(q.id) === String(questView?.id));
  const joinedByMe = localJoined == null ? joinedFromServer : localJoined;

  const startDate = questView?.startDate ? new Date(questView.startDate) : null;
  const endDate = questView?.endDate ? new Date(questView.endDate) : null;
  const now = new Date();

  const notStartedYet = !!(startDate && now < startDate);
  const ended = !!(endDate && now >= endDate);

  const visibility: QuestVisibility = questView?.visibility ?? 'PRIVATE';
  const status: QuestStatus = questView?.status ?? 'ACTIVE';

  const participantCount =
    typeof questView?.participantsCount === 'number'
      ? questView.participantsCount
      : Array.isArray(questView?.participants)
        ? questView.participants.length
        : 0;

  const mySubs = useMemo(() => {
    const uid = user?.id != null ? String(user.id) : '';
    return (submissions ?? []).filter((s: SubmissionDTO) => {
      const subUserId = String(s.userId ?? '');
      return uid && subUserId === uid;
    });
  }, [submissions, user?.id]);

  const completed = Boolean(questView?.completedByCurrentUser);

  const canSubmit = Boolean(
    joinedByMe && !completed && status !== 'ARCHIVED' && !notStartedYet && !ended
  );

  const canSend = files.length > 0;
  const canJoin = !joinedByMe && visibility === 'PUBLIC' && status !== 'ARCHIVED' && !!safeQuestId;
  const showLeaveButton = !ended && status !== 'ARCHIVED' && !!safeQuestId && joinedByMe;

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!canSubmit) return;
    if (files.length === 0) {
      toast.error('Attach at least one image.');
      return;
    }

    setBatchSubmitting(true);
    const toastId = 'upload-many';

    try {
      toast.loading(`Preparing ${files.length} file(s)...`, { id: toastId });

      // Always sanitize images (removes EXIF/GPS/time); also resizes if needed.
      const processed: File[] = [];
      for (let i = 0; i < files.length; i++) {
        const original = files[i];
        toast.loading(`Optimizing ${i + 1}/${files.length}...`, { id: toastId });

        let f: File = original;
        if (/^image\//i.test(f.type)) {
          f = await sanitizeImageForUpload(f);
        }
        processed.push(f);
      }

      toast.loading(`Uploading ${processed.length} file(s)...`, { id: toastId });

      await create.mutateAsync({
        questId: safeQuestId,
        comment: comment || undefined,
        files: processed,
      });

      toast.success(`Uploaded ${processed.length} proof(s)!`, { id: toastId });

      setComment('');
      setFiles([]);
      if (fileInputRef.current) fileInputRef.current.value = '';
    } catch (err: unknown) {
      toast.error(getErrorMessage(err, 'Failed to create submission'), { id: toastId });
    } finally {
      setBatchSubmitting(false);
    }
  };

  const onJoin = async () => {
    try {
      await join.mutateAsync(safeQuestId);
    } finally {
      setLocalJoined(true);
    }
    toast.success('Joined quest');
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
    } catch (e: unknown) {
      toast.error(getErrorMessage(e, 'Failed to leave quest'));
    }
  };

  return (
    <PageShell>
      <div className="mx-auto max-w-6xl">
        {!safeQuestId ? (
          <EmptyState title="No quest selected." description="Open a quest from the list to view details." />
        ) : isLoading ? (
          <LoadingState label="Loading quest..." />
        ) : isError || !quest ? (
          <ErrorState message={getErrorMessage(error, 'Failed to load quest')} />
        ) : (
          <div className="space-y-5">
            <Panel className="p-5">
              <PageHeader
                title={quest.title}
                description={quest.description}
                actions={
                  <>
                    {String(user?.id) === String(questView?.createdByUserId) &&
                      !completed &&
                      status !== 'ARCHIVED' && (
                        <Link to={`/quests/${quest.id}/edit`} className="btn btn-secondary">
                          Edit
                        </Link>
                      )}

                    {canJoin && (
                      <Button onClick={onJoin} disabled={join.isPending}>
                        {join.isPending ? 'Joining...' : 'Join'}
                      </Button>
                    )}

                    {showLeaveButton && (
                      <Button
                        onClick={onLeave}
                        disabled={leave.isPending}
                        title={
                          String(user?.id) === String(questView?.createdByUserId)
                            ? "You can't leave quests you own"
                            : 'Leave this quest'
                        }
                      >
                        {leave.isPending ? 'Leaving...' : 'Leave'}
                      </Button>
                    )}
                  </>
                }
              />

              <div className="mt-5 flex flex-wrap items-center gap-2">
                <Badge tone="info">{quest.category ?? 'General'}</Badge>
                <Badge tone={visibility === 'PUBLIC' ? 'accent' : 'neutral'}>{visibility}</Badge>
                {status && !completed ? <StatusBadge status={status} /> : null}
                <Badge>
                  <Users className="mr-1 h-3.5 w-3.5" />
                  {participantCount} participants
                </Badge>
                {startDate ? (
                  <Badge title="Start date">
                    <CalendarDays className="mr-1 h-3.5 w-3.5" />
                    Starts {startDate.toLocaleDateString()}
                  </Badge>
                ) : null}
                {endDate ? (
                  <Badge title="End date">
                    <CalendarDays className="mr-1 h-3.5 w-3.5" />
                    Ends {endDate.toLocaleDateString()}
                  </Badge>
                ) : null}
                {completed ? (
                  <Badge tone="success" title="You have completed this quest">
                    <CheckCircle2 className="mr-1 h-3.5 w-3.5" />
                    Completed
                  </Badge>
                ) : null}
              </div>
            </Panel>

            <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_400px]">
              <Panel className="p-5">
                <div className="mb-4 flex items-center justify-between gap-3">
                  <h2 className="text-lg font-semibold">Your Submissions</h2>
                  <Badge>{mySubs.length}</Badge>
                </div>

                {mySubs.length === 0 ? (
                  <EmptyState
                    title="No submissions yet."
                    description="Once you upload proof for this quest, it will appear here."
                  />
                ) : (
                  <ul className="space-y-3">
                    {mySubs.map((s) => {
                      const submissionView = s as SubmissionView;
                      const st = submissionView.status ?? submissionView.reviewStatus;
                      const closed = Boolean(submissionView.closed);

                      return (
                        <li
                          key={s.id}
                          className="rounded-lg border border-[rgb(var(--border-soft))] bg-[rgba(var(--surface-2),0.42)] p-4"
                        >
                          <div className="flex items-start justify-between gap-3">
                            <div className="min-w-0 text-sm">
                              <div className="font-medium">{s.comment || 'Submission'}</div>
                              <div className="mt-1 text-xs text-[rgb(var(--faint))]">
                                {new Date(s.createdAt).toLocaleString()}
                              </div>
                            </div>
                            <div className="flex flex-wrap justify-end gap-2">
                              {st ? <StatusBadge status={st} /> : null}
                              {closed ? <Badge>Closed</Badge> : null}
                            </div>
                          </div>

                          <SubmissionPreview submission={s} />
                        </li>
                      );
                    })}
                  </ul>
                )}
              </Panel>

              <Panel className="p-5">
                <div className="mb-4 flex items-center gap-2">
                  <UploadCloud className="h-5 w-5 text-[rgb(var(--accent))]" />
                  <h2 className="text-lg font-semibold">New Submission</h2>
                </div>

                {!joinedByMe ? (
                  <EmptyState
                    title="You have not joined this quest."
                    description="Join the quest before submitting proof."
                    action={
                      canJoin ? (
                        <Button onClick={onJoin} disabled={join.isPending}>
                          {join.isPending ? 'Joining...' : 'Join this quest'}
                        </Button>
                      ) : null
                    }
                  />
                ) : completed ? (
                  <div className="rounded-lg border border-[rgba(var(--green),0.35)] bg-[rgba(var(--green),0.1)] p-4 text-sm text-green-100">
                    This quest is already completed.
                  </div>
                ) : status === 'ARCHIVED' ? (
                  <div className="rounded-lg border border-[rgb(var(--border-soft))] bg-[rgba(var(--surface-2),0.5)] p-4 text-sm text-[rgb(var(--muted))]">
                    This quest is archived and no longer accepts submissions.
                  </div>
                ) : notStartedYet ? (
                  <div className="rounded-lg border border-[rgba(var(--amber),0.4)] bg-[rgba(var(--amber),0.1)] p-4 text-sm text-amber-100">
                    Quest has not started yet. {startDate ? `Starts on ${startDate.toLocaleString()}.` : ''}
                  </div>
                ) : ended ? (
                  <div className="rounded-lg border border-[rgb(var(--border-soft))] bg-[rgba(var(--surface-2),0.5)] p-4 text-sm text-[rgb(var(--muted))]">
                    Quest has ended and new submissions are closed. {endDate ? `Ended on ${endDate.toLocaleString()}.` : ''}
                  </div>
                ) : (
                  <form className="space-y-4" onSubmit={submit}>
                    <div>
                      <FieldLabel>Comment (optional)</FieldLabel>
                      <TextArea
                        value={comment}
                        onChange={(e) => setComment(e.target.value)}
                        placeholder="Add context for your proof..."
                        disabled={batchSubmitting || create.isPending || !canSubmit}
                      />
                    </div>

                    <div className="rounded-lg border border-dashed border-[rgb(var(--border))] bg-[rgba(var(--bg-soft),0.58)] p-4">
                      <div className="mb-3 flex items-center gap-2 text-sm text-[rgb(var(--muted))]">
                        <ImageIcon className="h-4 w-4 text-[rgb(var(--accent))]" />
                        Attach one or more images
                      </div>
                      <input
                        ref={fileInputRef}
                        type="file"
                        accept="image/*"
                        multiple
                        onChange={(e) => setFiles(Array.from(e.target.files ?? []))}
                        className="block w-full text-sm text-[rgb(var(--muted))] file:mr-3 file:rounded-lg file:border-0 file:bg-[rgba(var(--accent),0.16)] file:px-3 file:py-2 file:text-sm file:font-medium file:text-teal-100 hover:file:bg-[rgba(var(--accent),0.24)]"
                        disabled={batchSubmitting || create.isPending || !canSubmit}
                      />

                      {files.length > 0 && (
                        <div className="mt-3 text-xs text-[rgb(var(--muted))]">
                          Selected: <span className="font-medium">{files.length}</span> file(s)
                          <button
                            type="button"
                            className="ml-2 text-[rgb(var(--accent))] underline-offset-4 hover:underline"
                            onClick={() => {
                              setFiles([]);
                              if (fileInputRef.current) fileInputRef.current.value = '';
                            }}
                            disabled={batchSubmitting || create.isPending}
                          >
                            clear
                          </button>

                          <div className="mt-2 space-y-1">
                            {files.slice(0, 6).map((f) => (
                              <div key={`${f.name}-${f.size}`} className="truncate" title={f.name}>
                                - {f.name}
                              </div>
                            ))}
                            {files.length > 6 ? <div>- and {files.length - 6} more</div> : null}
                          </div>
                        </div>
                      )}
                    </div>

                    <div className="flex flex-wrap items-center gap-3 pt-1">
                      <Button
                        type="submit"
                        variant="primary"
                        disabled={batchSubmitting || create.isPending || !canSend || !canSubmit}
                        title={!canSend ? 'Attach at least one image' : 'Submit'}
                      >
                        {batchSubmitting || create.isPending ? 'Submitting...' : 'Submit proof'}
                      </Button>
                      {!canSend && (
                        <span className="text-xs text-[rgb(var(--faint))]">
                          Provide at least one image to submit.
                        </span>
                      )}
                    </div>
                  </form>
                )}
              </Panel>
            </div>
          </div>
        )}
      </div>

      <InfoModal
        open={showOwnerLeaveModal}
        onClose={() => setShowOwnerLeaveModal(false)}
        title="Can't leave your own quest"
      >
        <p>
          You are the owner of this quest, so you can't leave it. You can archive or
          delete the quest instead.
        </p>
      </InfoModal>
    </PageShell>
  );
}
