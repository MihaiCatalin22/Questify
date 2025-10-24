import React from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'react-hot-toast';
import { useAuthContext } from '../../contexts/AuthContext';
import { QuestsApi } from '../../api/quests';

type CreateQuestInput = {
  title: string;
  description: string;
  category: string;          
  startDate?: string;
  endDate?: string;
  createdByUserId: string;   
};

export default function QuestForm() {
  const { user } = useAuthContext();
  const navigate = useNavigate();

  const [title, setTitle] = React.useState('');
  const [description, setDescription] = React.useState('');
  const [category, setCategory] = React.useState('OTHER'); 
  const [startDate, setStartDate] = React.useState('');
  const [endDate, setEndDate] = React.useState('');
  const [saving, setSaving] = React.useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!user) return toast.error('Please log in first.');

    const payload: CreateQuestInput = {
      title,
      description,
      category, 
      startDate: startDate || undefined,
      endDate: endDate || undefined,
      createdByUserId: String(user.id),
    };

    try {
      setSaving(true);
      await QuestsApi.create(payload as any);
      toast.success('Quest created!');
      navigate('/quests');
    } catch {

    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="max-w-2xl mx-auto p-6">
      <h1 className="text-2xl font-semibold mb-4">Create Quest</h1>

      <form onSubmit={onSubmit} className="card">
        <div className="card-body space-y-4">
          <div>
            <label className="block text-sm font-medium mb-1">Title</label>
            <input
              className="field"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              minLength={3}
              maxLength={140}
              required
            />
            <p className="text-xs text-gray-500 mt-1">3–140 chars</p>
          </div>

          <div>
            <label className="block text-sm font-medium mb-1">Description</label>
            <textarea
              className="field h-28 resize-y"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              minLength={10}
              maxLength={2000}
              required
            />
            <p className="text-xs text-gray-500 mt-1">10–2000 chars</p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            <div>
              <label className="block text-sm font-medium mb-1">Category</label>
              {/* Keep as free text (string). If you later add a BE categories endpoint, swap to a <select>. */}
              <input
                className="field"
                value={category}
                onChange={(e) => setCategory(e.target.value)}
                placeholder="e.g. STUDY, FITNESS, OTHER"
                required
              />
            </div>

            <div className="grid grid-cols-2 gap-3 md:col-span-2">
              <div>
                <label className="block text-sm font-medium mb-1">Start date</label>
                <input type="date" className="field" value={startDate} onChange={(e) => setStartDate(e.target.value)} />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">End date</label>
                <input type="date" className="field" value={endDate} onChange={(e) => setEndDate(e.target.value)} />
              </div>
            </div>
          </div>

          <div className="flex justify-end gap-3">
            <button type="button" onClick={() => navigate(-1)} className="btn btn-secondary">
              Cancel
            </button>
            <button disabled={saving} className="btn btn-primary">
              {saving ? 'Creating…' : 'Create'}
            </button>
          </div>
        </div>
      </form>
    </div>
  );
}
