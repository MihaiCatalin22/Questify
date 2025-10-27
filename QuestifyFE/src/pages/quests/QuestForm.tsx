import React from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'react-hot-toast';
import { useAuthContext } from '../../contexts/AuthContext';
import { QuestsApi } from '../../api/quests';

type CreateQuestInput = {
  title: string;
  description: string;
  category: string;
  startDate: string;   // ISO instant
  endDate: string;     // ISO instant
  createdByUserId: string;
  visibility: 'PUBLIC' | 'PRIVATE';
};

const CATEGORIES = [
  'COMMUNITY','FITNESS','HABIT','HOBBY','OTHER','STUDY','WORK',
] as const;

type FieldErrors = Partial<Record<keyof CreateQuestInput | 'startDate' | 'endDate', string>>;

function todayISODate(): string {
  const d = new Date();
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${yyyy}-${mm}-${dd}`;
}

function dayStartUtcISO(dateOnly: string): string {
  return new Date(`${dateOnly}T00:00:00.000Z`).toISOString();
}
function dayEndUtcISO(dateOnly: string): string {
  return new Date(`${dateOnly}T23:59:59.999Z`).toISOString();
}

export default function QuestForm() {
  const { user } = useAuthContext();
  const navigate = useNavigate();

  const [title, setTitle] = React.useState('');
  const [description, setDescription] = React.useState('');
  const [category, setCategory] = React.useState<(typeof CATEGORIES)[number]>('OTHER');
  const [visibility, setVisibility] = React.useState<'PUBLIC'|'PRIVATE'>('PRIVATE'); // NEW
  const [startDate, setStartDate] = React.useState(''); // yyyy-MM-dd
  const [endDate, setEndDate] = React.useState('');     // yyyy-MM-dd
  const [saving, setSaving] = React.useState(false);
  const [errors, setErrors] = React.useState<FieldErrors>({});

  const validate = (): boolean => {
    const next: FieldErrors = {};

    if (!title || title.trim().length < 3) next.title = 'Title must be at least 3 characters.';
    if (title.trim().length > 140) next.title = 'Title cannot exceed 140 characters.';

    if (!description || description.trim().length < 10) next.description = 'Description must be at least 10 characters.';
    if (description.trim().length > 2000) next.description = 'Description cannot exceed 2000 characters.';

    if (!CATEGORIES.includes(category)) next.category = 'Pick a category from the list.';
    if (!startDate) next.startDate = 'Start date is required.';
    if (!endDate) next.endDate = 'End date is required.';

    const today = todayISODate();
    if (startDate && startDate < today) next.startDate = 'Start date cannot be earlier than today.';
    if (startDate && endDate && endDate < startDate) next.endDate = 'End date cannot be before start date.';

    setErrors(next);
    return Object.keys(next).length === 0;
  };

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!user) {
      toast.error('Please log in first.');
      return;
    }
    if (!validate()) {
      toast.error('Please fix the errors and try again.');
      return;
    }

    const payload: CreateQuestInput = {
      title: title.trim(),
      description: description.trim(),
      category,
      startDate: dayStartUtcISO(startDate),
      endDate: dayEndUtcISO(endDate),
      createdByUserId: String(user.id),
      visibility, // NEW
    };

    try {
      setSaving(true);
      await QuestsApi.create(payload as any);
      toast.success('Quest created!');
      navigate('/quests');
    } catch (err: any) {
      const msg =
        err?.response?.data?.message ||
        err?.response?.data?.error ||
        err?.message ||
        'Failed to create quest';
      toast.error(String(msg));
    } finally {
      setSaving(false);
    }
  }

  const minStart = todayISODate();
  const minEnd = startDate || undefined;

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
              aria-invalid={!!errors.title}
              aria-describedby={errors.title ? 'title-err' : undefined}
            />
            <p className="text-xs text-gray-500 mt-1">3–140 chars</p>
            {errors.title && <p id="title-err" className="text-xs text-red-600 mt-1">{errors.title}</p>}
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
              aria-invalid={!!errors.description}
              aria-describedby={errors.description ? 'desc-err' : undefined}
            />
            <p className="text-xs text-gray-500 mt-1">10–2000 chars</p>
            {errors.description && <p id="desc-err" className="text-xs text-red-600 mt-1">{errors.description}</p>}
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            <div>
              <label className="block text-sm font-medium mb-1">Category</label>
              <select
                className="field"
                value={category}
                onChange={(e) => setCategory(e.target.value as (typeof CATEGORIES)[number])}
                required
                aria-invalid={!!errors.category}
                aria-describedby={errors.category ? 'cat-err' : undefined}
              >
                {CATEGORIES.map((c) => (
                  <option key={c} value={c}>{c}</option>
                ))}
              </select>
              {errors.category && <p id="cat-err" className="text-xs text-red-600 mt-1">{errors.category}</p>}
            </div>

            <div className="grid grid-cols-2 gap-3 md:col-span-2">
              <div>
                <label className="block text-sm font-medium mb-1">Start date</label>
                <input
                  type="date"
                  className="field"
                  value={startDate}
                  min={minStart}
                  onChange={(e) => setStartDate(e.target.value)}
                  required
                  aria-invalid={!!errors.startDate}
                  aria-describedby={errors.startDate ? 'start-err' : undefined}
                />
                {errors.startDate && <p id="start-err" className="text-xs text-red-600 mt-1">{errors.startDate}</p>}
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">End date</label>
                <input
                  type="date"
                  className="field"
                  value={endDate}
                  min={minEnd}
                  onChange={(e) => setEndDate(e.target.value)}
                  required
                  aria-invalid={!!errors.endDate}
                  aria-describedby={errors.endDate ? 'end-err' : undefined}
                />
                {errors.endDate && <p id="end-err" className="text-xs text-red-600 mt-1">{errors.endDate}</p>}
              </div>
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium mb-1">Visibility</label>
            <select
              className="field"
              value={visibility}
              onChange={(e) => setVisibility(e.target.value as 'PUBLIC'|'PRIVATE')}
            >
              <option value="PRIVATE">Private (only you)</option>
              <option value="PUBLIC">Public (others can discover & join)</option>
            </select>
          </div>

          <div className="flex justify-end gap-3">
            <button type="button" onClick={() => navigate(-1)} className="rounded-2xl border px-4 py-2">
              Cancel
            </button>
            <button disabled={saving} className="btn-primary disabled:opacity-60">
              {saving ? 'Creating…' : 'Create'}
            </button>
          </div>
        </div>
      </form>
    </div>
  );
}
