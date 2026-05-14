import React from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { toast } from 'react-hot-toast';
import { useAuthContext } from '../../contexts/useAuthContext';
import { useQuest, useUpdateQuest } from '../../hooks/useQuests';
import { QuestsApi } from '../../api/quests';
import type {
  CreateQuestInput,
  QuestCategory,
  QuestVisibility,
  UpdateQuestInput,
  VerificationPolicyDTO,
} from '../../types/quest';
import { getErrorMessage } from '../../utils/errors';
import {
  Button,
  ErrorState,
  FieldLabel,
  LoadingState,
  PageHeader,
  PageShell,
  Panel,
  SelectInput,
  TextArea,
  TextInput,
} from '../../components/ui';

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

function parseSignalLines(value: string): string[] {
  return value
    .split(/\r?\n|,/)
    .map((line) => line.trim())
    .filter((line) => line.length >= 2)
    .slice(0, 20);
}

function fieldValueFromSignals(values?: string[] | null): string {
  if (!Array.isArray(values) || values.length === 0) return '';
  return values.join('\n');
}

function isoDateOnly(value?: string | null): string {
  if (!value) return '';
  const raw = String(value).trim();
  const match = raw.match(/^(\d{4}-\d{2}-\d{2})/);
  if (match) return match[1];
  const d = new Date(raw);
  if (Number.isNaN(d.getTime())) return '';
  return d.toISOString().slice(0, 10);
}

export default function QuestForm() {
  const { user } = useAuthContext();
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const editQuestId = id && /^\d+$/.test(id) ? id : '';
  const isEditing = Boolean(editQuestId);

  const { data: existingQuest, isLoading: isLoadingQuest, isError: isQuestError, error: questError } =
    useQuest(editQuestId);
  const updateQuest = useUpdateQuest(editQuestId);

  const [title, setTitle] = React.useState('');
  const [description, setDescription] = React.useState('');
  const [category, setCategory] = React.useState<QuestCategory>('OTHER');
  const [visibility, setVisibility] = React.useState<QuestVisibility>('PRIVATE');
  const [taskType, setTaskType] = React.useState('generic');
  const [minSupportScore, setMinSupportScore] = React.useState('0.75');
  const [requiredEvidence, setRequiredEvidence] = React.useState('');
  const [optionalEvidence, setOptionalEvidence] = React.useState('');
  const [disqualifiers, setDisqualifiers] = React.useState('');
  const [startDate, setStartDate] = React.useState(''); // yyyy-MM-dd
  const [endDate, setEndDate] = React.useState('');     // yyyy-MM-dd
  const [saving, setSaving] = React.useState(false);
  const [errors, setErrors] = React.useState<FieldErrors>({});
  const [initializedFromExisting, setInitializedFromExisting] = React.useState(false);

  React.useEffect(() => {
    setInitializedFromExisting(false);
  }, [editQuestId]);

  React.useEffect(() => {
    if (!isEditing || !existingQuest || initializedFromExisting) return;

    setTitle(existingQuest.title ?? '');
    setDescription(existingQuest.description ?? '');
    setCategory((existingQuest.category ?? 'OTHER') as QuestCategory);
    setVisibility((existingQuest.visibility ?? 'PRIVATE') as QuestVisibility);

    const policy = existingQuest.verificationPolicy ?? null;
    setTaskType((policy?.taskType ?? 'generic').trim() || 'generic');
    setMinSupportScore(String(policy?.minSupportScore ?? 0.75));
    setRequiredEvidence(fieldValueFromSignals(policy?.requiredEvidence));
    setOptionalEvidence(fieldValueFromSignals(policy?.optionalEvidence));
    setDisqualifiers(fieldValueFromSignals(policy?.disqualifiers));

    setStartDate(isoDateOnly(existingQuest.startDate));
    setEndDate(isoDateOnly(existingQuest.endDate));
    setInitializedFromExisting(true);
  }, [isEditing, existingQuest, initializedFromExisting]);

  const validate = (): boolean => {
    const next: FieldErrors = {};

    if (!title || title.trim().length < 3) next.title = 'Title must be at least 3 characters.';
    if (title.trim().length > 140) next.title = 'Title cannot exceed 140 characters.';

    if (!description || description.trim().length < 10) next.description = 'Description must be at least 10 characters.';
    if (description.trim().length > 2000) next.description = 'Description cannot exceed 2000 characters.';

    if (!CATEGORIES.includes(category)) next.category = 'Pick a category from the list.';
    if (!startDate) next.startDate = 'Start date is required.';
    if (!endDate) next.endDate = 'End date is required.';
    const parsedRequired = parseSignalLines(requiredEvidence);
    const parsedDisqualifiers = parseSignalLines(disqualifiers);
    const minSupport = Number(minSupportScore);
    if (!Number.isFinite(minSupport) || minSupport < 0 || minSupport > 1) {
      next.verificationPolicy = 'Min support score must be between 0 and 1.';
    }
    if (parsedRequired.length < 2) {
      next.verificationPolicy = 'Add at least two required evidence signals.';
    }
    if (parsedDisqualifiers.length < 2) {
      next.verificationPolicy = 'Add at least two disqualifier signals.';
    }

    const today = todayISODate();
    if (!isEditing && startDate && startDate < today) next.startDate = 'Start date cannot be earlier than today.';
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
    if (isEditing && (!existingQuest || String(existingQuest.createdByUserId) !== String(user.id))) {
      toast.error('Only the quest owner can edit this quest.');
      return;
    }
    if (!validate()) {
      toast.error('Please fix the errors and try again.');
      return;
    }

    const verificationPolicy: VerificationPolicyDTO = {
      requiredEvidence: parseSignalLines(requiredEvidence),
      optionalEvidence: parseSignalLines(optionalEvidence),
      disqualifiers: parseSignalLines(disqualifiers),
      minSupportScore: Number(minSupportScore),
      taskType: taskType.trim() || 'generic',
    };

    try {
      setSaving(true);
      if (isEditing) {
        const payload: UpdateQuestInput = {
          title: title.trim(),
          description: description.trim(),
          category,
          startDate: dayStartUtcISO(startDate),
          endDate: dayEndUtcISO(endDate),
          visibility,
          verificationPolicy,
        };
        await updateQuest.mutateAsync(payload);
        toast.success('Quest updated!');
        navigate(`/quests/${editQuestId}`);
      } else {
        const payload: CreateQuestInput = {
          title: title.trim(),
          description: description.trim(),
          category,
          startDate: dayStartUtcISO(startDate),
          endDate: dayEndUtcISO(endDate),
          createdByUserId: String(user.id),
          visibility,
          verificationPolicy,
        };
        await QuestsApi.create(payload);
        toast.success('Quest created!');
        navigate('/quests');
      }
    } catch (err: unknown) {
      toast.error(getErrorMessage(err, isEditing ? 'Failed to update quest' : 'Failed to create quest'));
    } finally {
      setSaving(false);
    }
  }

  const minStart = isEditing ? undefined : todayISODate();
  const minEnd = startDate || undefined;
  const isOwner = !!(user && existingQuest && String(existingQuest.createdByUserId) === String(user.id));
  const submitBusy = saving || updateQuest.isPending;

  if (isEditing && isLoadingQuest) {
    return (
      <PageShell className="mx-auto max-w-3xl">
        <LoadingState label="Loading quest..." />
      </PageShell>
    );
  }

  if (isEditing && (isQuestError || !existingQuest)) {
    return (
      <PageShell className="mx-auto max-w-3xl">
        <ErrorState message={getErrorMessage(questError, 'Failed to load quest for editing')} />
      </PageShell>
    );
  }

  if (isEditing && !isOwner) {
    return (
      <PageShell className="mx-auto max-w-3xl">
        <ErrorState message="Only the quest owner can edit this quest." />
      </PageShell>
    );
  }

  return (
    <PageShell className="mx-auto max-w-3xl">
      <PageHeader
        title={isEditing ? 'Edit Quest' : 'Create Quest'}
        description={
          isEditing
            ? 'Update quest details, timing, visibility, and verification signals.'
            : 'Define the goal, timing, category, and visibility before people start submitting proof.'
        }
      />

      <form onSubmit={onSubmit}>
        <Panel className="p-5">
        <div className="card-body space-y-4">
          <div>
            <FieldLabel>Title</FieldLabel>
            <TextInput
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              minLength={3}
              maxLength={140}
              required
              aria-invalid={!!errors.title}
              aria-describedby={errors.title ? 'title-err' : undefined}
            />
            <p className="mt-1 text-xs text-[rgb(var(--faint))]">3-140 characters</p>
            {errors.title && <p id="title-err" className="mt-1 text-xs text-red-300">{errors.title}</p>}
          </div>

          <div>
            <FieldLabel>Description</FieldLabel>
            <TextArea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              minLength={10}
              maxLength={2000}
              required
              aria-invalid={!!errors.description}
              aria-describedby={errors.description ? 'desc-err' : undefined}
            />
            <p className="mt-1 text-xs text-[rgb(var(--faint))]">10-2000 characters</p>
            {errors.description && <p id="desc-err" className="mt-1 text-xs text-red-300">{errors.description}</p>}
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            <div>
              <FieldLabel>Category</FieldLabel>
              <SelectInput
                value={category}
                onChange={(e) => setCategory(e.target.value as QuestCategory)}
                required
                aria-invalid={!!errors.category}
                aria-describedby={errors.category ? 'cat-err' : undefined}
              >
                {CATEGORIES.map((c) => (
                  <option key={c} value={c}>{c}</option>
                ))}
              </SelectInput>
              {errors.category && <p id="cat-err" className="mt-1 text-xs text-red-300">{errors.category}</p>}
            </div>

            <div className="grid grid-cols-2 gap-3 md:col-span-2">
              <div>
                <FieldLabel>Start date</FieldLabel>
                <TextInput
                  type="date"
                  value={startDate}
                  min={minStart}
                  onChange={(e) => setStartDate(e.target.value)}
                  required
                  aria-invalid={!!errors.startDate}
                  aria-describedby={errors.startDate ? 'start-err' : undefined}
                />
                {errors.startDate && <p id="start-err" className="mt-1 text-xs text-red-300">{errors.startDate}</p>}
              </div>
              <div>
                <FieldLabel>End date</FieldLabel>
                <TextInput
                  type="date"
                  value={endDate}
                  min={minEnd}
                  onChange={(e) => setEndDate(e.target.value)}
                  required
                  aria-invalid={!!errors.endDate}
                  aria-describedby={errors.endDate ? 'end-err' : undefined}
                />
                {errors.endDate && <p id="end-err" className="mt-1 text-xs text-red-300">{errors.endDate}</p>}
              </div>
            </div>
          </div>

          <div>
            <FieldLabel>Visibility</FieldLabel>
            <SelectInput
              value={visibility}
              onChange={(e) => setVisibility(e.target.value as QuestVisibility)}
            >
              <option value="PRIVATE">Private (only you)</option>
              <option value="PUBLIC">Public (others can discover & join)</option>
            </SelectInput>
          </div>

          <div className="space-y-3 border border-[rgb(var(--border-soft))] rounded-lg p-4">
            <div className="text-sm font-medium">Verification policy</div>
            <p className="text-xs text-[rgb(var(--faint))]">
              Signals used by AI review to compare proof evidence against this quest. Required/disqualifiers need at least two items each.
            </p>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <div>
                <FieldLabel>Task type</FieldLabel>
                <TextInput
                  value={taskType}
                  onChange={(e) => setTaskType(e.target.value)}
                  placeholder="generic"
                  maxLength={80}
                />
              </div>
              <div>
                <FieldLabel>Min support score (0-1)</FieldLabel>
                <TextInput
                  value={minSupportScore}
                  onChange={(e) => setMinSupportScore(e.target.value)}
                  placeholder="0.75"
                />
              </div>
            </div>

            <div>
              <FieldLabel>Required evidence (one per line)</FieldLabel>
              <TextArea
                rows={3}
                value={requiredEvidence}
                onChange={(e) => setRequiredEvidence(e.target.value)}
                placeholder={'worksheet\nalgebra equations\nworked steps'}
              />
            </div>

            <div>
              <FieldLabel>Optional evidence (one per line)</FieldLabel>
              <TextArea
                rows={3}
                value={optionalEvidence}
                onChange={(e) => setOptionalEvidence(e.target.value)}
                placeholder={'student note\ntimestamp\ntopic keywords'}
              />
            </div>

            <div>
              <FieldLabel>Disqualifiers (one per line)</FieldLabel>
              <TextArea
                rows={3}
                value={disqualifiers}
                onChange={(e) => setDisqualifiers(e.target.value)}
                placeholder={'game hud\nmeme\nunrelated object'}
              />
            </div>

            {errors.verificationPolicy && (
              <p className="text-xs text-red-300">{errors.verificationPolicy}</p>
            )}
          </div>

          <div className="flex justify-end gap-3">
            <Button type="button" onClick={() => navigate(-1)}>
              Cancel
            </Button>
            <Button disabled={submitBusy} variant="primary">
              {submitBusy ? (isEditing ? 'Saving...' : 'Creating...') : isEditing ? 'Save changes' : 'Create'}
            </Button>
          </div>
        </div>
        </Panel>
      </form>
    </PageShell>
  );
}
