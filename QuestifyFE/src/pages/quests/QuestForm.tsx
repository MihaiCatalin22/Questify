import { useNavigate, useParams } from 'react-router-dom';
import { useCreateQuest, useQuest, useUpdateQuest } from '../../hooks/useQuests';
import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';

const schemaCreate = z.object({
  title: z.string().min(3),
  description: z.string().optional(),
  category: z.string().optional(),
  startDate: z.string().optional(),
  endDate: z.string().optional(),
});

const schemaUpdate = schemaCreate.partial();

type CreateForm = z.infer<typeof schemaCreate>;
type UpdateForm = z.infer<typeof schemaUpdate>;

export default function QuestForm() {
  const { id } = useParams();
  const isEdit = !!id && id !== 'new';
  return isEdit ? <EditQuestForm id={id!} /> : <CreateQuestForm />;
}

function CreateQuestForm() {
  const create = useCreateQuest();
  const navigate = useNavigate();
  const { register, handleSubmit, formState } = useForm<CreateForm>({
    resolver: zodResolver(schemaCreate),
    defaultValues: { title: '', description: '', category: '', startDate: '', endDate: '' },
  });

  const onSubmit = async (values: CreateForm) => {
    await create.mutateAsync(values);
    navigate('/quests');
  };

  return (
    <div className="p-6 max-w-2xl mx-auto">
      <h1 className="text-2xl font-semibold mb-4">Create Quest</h1>
      <form className="space-y-4" onSubmit={handleSubmit(onSubmit)}>
        <Field label="Title" error={formState.errors.title?.message}>
          <input {...register('title')} className="w-full border rounded-xl px-3 py-2" />
        </Field>
        <Field label="Description" error={formState.errors.description?.message}>
          <textarea {...register('description')} className="w-full border rounded-xl px-3 py-2" rows={4} />
        </Field>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          <Field label="Category" error={formState.errors.category?.message}>
            <input {...register('category')} className="w-full border rounded-xl px-3 py-2" />
          </Field>
          <div className="grid grid-cols-2 gap-3 md:col-span-2">
            <Field label="Start date" error={formState.errors.startDate?.message}>
              <input type="date" {...register('startDate')} className="w-full border rounded-xl px-3 py-2" />
            </Field>
            <Field label="End date" error={formState.errors.endDate?.message}>
              <input type="date" {...register('endDate')} className="w-full border rounded-xl px-3 py-2" />
            </Field>
          </div>
        </div>
        <div className="flex gap-2">
          <button className="rounded-2xl border px-4 py-2">Save</button>
        </div>
      </form>
    </div>
  );
}

function EditQuestForm({ id }: { id: string }) {
  const { data } = useQuest(id);
  const update = useUpdateQuest(id);
  const navigate = useNavigate();

  const { register, handleSubmit, formState, reset } = useForm<UpdateForm>({
    resolver: zodResolver(schemaUpdate),
  });

  useEffect(() => {
    if (data) {
      reset({
        title: data.title,
        description: data.description,
        category: data.category,
        startDate: data.startDate?.slice(0, 10),
        endDate: data.endDate?.slice(0, 10),
      });
    }
  }, [data, reset]);

  const onSubmit = async (values: UpdateForm) => {
    await update.mutateAsync(values);
    navigate('/quests');
  };

  return (
    <div className="p-6 max-w-2xl mx-auto">
      <h1 className="text-2xl font-semibold mb-4">Edit Quest</h1>
      <form className="space-y-4" onSubmit={handleSubmit(onSubmit)}>
        <Field label="Title" error={formState.errors.title?.message}>
          <input {...register('title')} className="w-full border rounded-xl px-3 py-2" />
        </Field>
        <Field label="Description" error={formState.errors.description?.message}>
          <textarea {...register('description')} className="w-full border rounded-xl px-3 py-2" rows={4} />
        </Field>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          <Field label="Category" error={formState.errors.category?.message}>
            <input {...register('category')} className="w-full border rounded-xl px-3 py-2" />
          </Field>
          <div className="grid grid-cols-2 gap-3 md:col-span-2">
            <Field label="Start date" error={formState.errors.startDate?.message}>
              <input type="date" {...register('startDate')} className="w-full border rounded-xl px-3 py-2" />
            </Field>
            <Field label="End date" error={formState.errors.endDate?.message}>
              <input type="date" {...register('endDate')} className="w-full border rounded-xl px-3 py-2" />
            </Field>
          </div>
        </div>
        <div className="flex gap-2">
          <button className="rounded-2xl border px-4 py-2">Save</button>
        </div>
      </form>
    </div>
  );
}

function Field({ label, error, children }: { label: string; error?: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="block text-sm mb-1">{label}</label>
      {children}
      {error && <p className="text-xs text-red-600 mt-1">{error}</p>}
    </div>
  );
}
