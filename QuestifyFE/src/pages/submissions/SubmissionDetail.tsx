import { useParams, Link } from 'react-router-dom';
import { useSubmission } from '../../hooks/useSubmissions';

export default function SubmissionDetail() {
  const { id } = useParams();
  const { data: s, isLoading, isError } = useSubmission(id ?? '');

  if (isLoading) return <div className="p-6">Loading submission…</div>;
  if (isError || !s) return <div className="p-6 text-red-600">Failed to load submission</div>;

  const isImage = s.proofUrl ? /\.(png|jpe?g|gif|webp)$/i.test(s.proofUrl) : false;
  const isVideo = s.proofUrl ? /\.(mp4|webm|ogg)$/i.test(s.proofUrl) : false;

  return (
    <div className="p-6 space-y-4 max-w-3xl">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Submission {s.id}</h1>
        <Link to={`/quests/${s.questId}`} className="underline text-sm">View Quest</Link>
      </div>

      <div className="rounded-2xl border p-4 space-y-2">
        <div className="text-sm opacity-70">Quest ID: {s.questId}</div>
        <div className="text-sm opacity-70">User ID: {s.userId ?? '—'}</div>
        <div><span className="font-medium">Status:</span> {s.status}</div>
        <div className="text-sm opacity-70">Created: {new Date(s.createdAt).toLocaleString()}</div>
        {s.comment && <p className="mt-2">{s.comment}</p>}
      </div>

      {s.proofUrl && (
        <div className="rounded-2xl border p-4 space-y-2">
          <div className="font-semibold">Proof</div>
          {isImage && (
            <img src={s.proofUrl} alt="proof" className="max-w-full rounded-xl" />
          )}
          {isVideo && (
            <video controls className="max-w-full rounded-xl">
              <source src={s.proofUrl} />
            </video>
          )}
          {!isImage && !isVideo && (
            <a className="underline" href={s.proofUrl} target="_blank" rel="noreferrer">
              Open proof
            </a>
          )}
        </div>
      )}
    </div>
  );
}
