import { Link } from 'react-router-dom';
import { useDeleteQuest, useQuests } from '../../hooks/useQuests';

export default function QuestsList() {
  const { data: quests, isLoading, isError, error } = useQuests();
  const del = useDeleteQuest();

  if (isLoading) return <div className="p-6">Loading quests…</div>;
  if (isError) return <div className="p-6 text-red-600">{(error as any)?.message || 'Failed to load quests'}</div>;

  if (!quests || quests.length === 0) {
    return (
      <div className="p-6 space-y-4">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-semibold">Quests</h1>
          <Link to="/quests/new" className="rounded-2xl border px-3 py-1.5 hover:shadow">
            New Quest
          </Link>
        </div>
        <p className="text-sm text-gray-600">No quests yet. Create one to get started.</p>
      </div>
    );
  }

  return (
    <div className="p-6 space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Quests</h1>
        <Link to="/quests/new" className="rounded-2xl border px-3 py-1.5 hover:shadow">
          New Quest
        </Link>
      </div>

      <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-4">
        {quests.map((q) => (
          <div key={q.id} className="rounded-2xl border p-4 space-y-2 bg-white shadow-sm">
            <div className="flex items-start justify-between">
              <h3 className="font-semibold text-lg">
                <Link to={`/quests/${q.id}`} className="underline">{q.title}</Link>
              </h3>
              <span className="inline-flex items-center px-2 py-0.5 text-xs font-medium rounded-full bg-indigo-50 text-indigo-700 border border-indigo-200">
                {q.category ?? 'General'}
              </span>
            </div>

            {q.description && (
              <p className="text-sm text-gray-700 line-clamp-3">{q.description}</p>
            )}

            <div className="flex gap-3 text-sm">
              <Link to={`/quests/${q.id}/edit`} className="underline">Edit</Link>
              <button
                onClick={() => del.mutate(q.id)}
                className="underline"
                disabled={del.isPending}
              >
                {del.isPending ? 'Deleting…' : 'Delete'}
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
