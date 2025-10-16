import { useParams } from 'react-router-dom';
import { useQuest } from '../../hooks/useQuests';
import { useCreateSubmission, useSubmissionsForQuest } from '../../hooks/useSubmissions';
import { useState } from 'react';

export default function QuestDetail() {
  const { id } = useParams();
  const questId = id ?? '';
  const { data: quest, isLoading, isError } = useQuest(questId);
  const { data: submissions } = useSubmissionsForQuest(questId);
  const create = useCreateSubmission(questId);

  const [comment, setComment] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [proofUrl, setProofUrl] = useState('');

  if (isLoading) return <div className="p-6">Loading quest…</div>;
  if (isError || !quest) return <div className="p-6 text-red-600">Failed to load quest</div>;

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    await create.mutateAsync({ questId, comment, file, proofUrl: file ? undefined : proofUrl || undefined });
    setComment('');
    setFile(null);
    setProofUrl('');
  };

  return (
    <div className="p-6 space-y-6">
      <div className="rounded-2xl border p-4">
        <h1 className="text-2xl font-semibold">{quest.title}</h1>
        {quest.description && <p className="mt-2">{quest.description}</p>}
        <div className="text-sm opacity-70 mt-2">
          {quest.category ?? 'General'}
        </div>
      </div>

      <div className="grid md:grid-cols-2 gap-6">
        <div className="rounded-2xl border p-4">
          <h2 className="font-semibold mb-3">Submissions</h2>
          <ul className="space-y-3">
            {(submissions ?? []).map(s => (
              <li key={s.id} className="border rounded-xl p-3">
                <div className="flex items-center justify-between">
                  <div className="text-sm">Status: <span className="font-medium">{s.status}</span></div>
                  <div className="text-xs opacity-70">{new Date(s.createdAt).toLocaleString()}</div>
                </div>
                {s.comment && <p className="text-sm mt-2">{s.comment}</p>}
                {s.proofUrl && <a href={s.proofUrl} target="_blank" rel="noreferrer" className="text-sm underline mt-1 inline-block">View proof</a>}
              </li>
            ))}
            {(!submissions || submissions.length === 0) && <li className="text-sm opacity-70">No submissions yet.</li>}
          </ul>
        </div>

        <div className="rounded-2xl border p-4">
          <h2 className="font-semibold mb-3">New Submission</h2>
          <form className="space-y-3" onSubmit={submit}>
            <div>
              <label className="block text-sm mb-1">Comment (optional)</label>
              <textarea
                className="w-full border rounded-xl px-3 py-2"
                rows={3}
                value={comment}
                onChange={(e) => setComment(e.target.value)}
              />
            </div>
            <div>
              <label className="block text-sm mb-1">Upload proof file (image/video) OR paste a URL</label>
              <input type="file" onChange={(e) => setFile(e.target.files?.[0] ?? null)} className="block" />
              <div className="my-2 text-center text-xs opacity-60">— or —</div>
              <input
                placeholder="https://example.com/my-proof"
                className="w-full border rounded-xl px-3 py-2"
                value={proofUrl}
                onChange={(e) => setProofUrl(e.target.value)}
              />
            </div>
            <button className="rounded-2xl border px-4 py-2" disabled={create.isPending}>
              {create.isPending ? 'Submitting…' : 'Submit'}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}
