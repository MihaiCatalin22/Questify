import { Link } from 'react-router-dom';
import { useDeleteQuest, useQuests } from '../../hooks/useQuests';

export default function QuestsList() {
  const { data, isLoading, isError, error } = useQuests();
  const del = useDeleteQuest();

  if (isLoading) return <div className="p-6">Loading quests…</div>;
  if (isError) return <div className="p-6 text-red-600">{(error as any)?.message || 'Failed to load quests'}</div>;

  return (
    <div className="p-6 space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Quests</h1>
        <Link to="/quests/new" className="rounded-2xl border px-3 py-1.5 hover:shadow">New Quest</Link>
      </div>

      <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-4">
        {(data ?? []).map(q => (
          <div key={q.id} className="rounded-2xl border p-4 space-y-2">
            <h3 className="font-semibold text-lg">
              <Link to={`/quests/${q.id}`} className="underline">{q.title}</Link>
            </h3>
            {q.description && <p className="text-sm opacity-80 line-clamp-3">{q.description}</p>}
            <div className="text-xs opacity-70">
              {q.category ?? 'General'}
            </div>
            <div className="flex gap-3 text-sm">
              <Link to={`/quests/${q.id}/edit`} className="underline">Edit</Link>
              <button onClick={() => del.mutate(q.id)} className="underline" disabled={del.isPending}>
                {del.isPending ? 'Deleting…' : 'Delete'}
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
