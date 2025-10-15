import { useNavigate, useParams } from 'react-router-dom';
import { useCreateUser, useUpdateUser, useUser } from '../../hooks/useUsers';
import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';

const createSchema = z.object({
  username: z.string().min(3),
  email: z.string().email(),
  password: z.string().min(6),
  displayName: z.string().optional(),
});

const updateSchema = z.object({
  email: z.string().email().optional(),
  password: z.string().min(6).optional(),
  displayName: z.string().optional(),
});

type CreateForm = z.infer<typeof createSchema>;
type UpdateForm = z.infer<typeof updateSchema>;

export default function UsersForm() {
  const { id } = useParams();
  const isEdit = !!id && id !== 'new';
  return isEdit ? <EditUserForm id={id!} /> : <CreateUserForm />;
}

function CreateUserForm() {
  const create = useCreateUser();
  const navigate = useNavigate();

  const form = useForm<CreateForm>({
    resolver: zodResolver(createSchema),
    defaultValues: { username: '', email: '', password: '', displayName: '' },
  });

  const onSubmit = async (values: CreateForm) => {
    await create.mutateAsync(values);
    navigate('/users');
  };

  const { register, handleSubmit, formState } = form;

  return (
    <div className="p-6 max-w-2xl mx-auto">
      <h1 className="text-2xl font-semibold mb-4">Create User</h1>
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <div>
          <label className="block text-sm mb-1">Username</label>
          <input {...register('username')} className="w-full border rounded-xl px-3 py-2" />
          <FormError msg={formState.errors.username?.message} />
        </div>
        <div>
          <label className="block text-sm mb-1">Email</label>
          <input {...register('email')} className="w-full border rounded-xl px-3 py-2" />
          <FormError msg={formState.errors.email?.message} />
        </div>
        <div>
          <label className="block text-sm mb-1">Password</label>
          <input type="password" {...register('password')} className="w-full border rounded-xl px-3 py-2" />
          <FormError msg={formState.errors.password?.message} />
        </div>
        <div>
          <label className="block text-sm mb-1">Display name</label>
          <input {...register('displayName')} className="w-full border rounded-xl px-3 py-2" />
          <FormError msg={formState.errors.displayName?.message} />
        </div>
        <div className="flex gap-2">
          <button className="rounded-2xl border px-4 py-2">Save</button>
        </div>
      </form>
    </div>
  );
}

function EditUserForm({ id }: { id: string }) {
  const { data } = useUser(id);
  const update = useUpdateUser(id);
  const navigate = useNavigate();

  const form = useForm<UpdateForm>({
    resolver: zodResolver(updateSchema),
    defaultValues: {},
  });

  useEffect(() => {
    if (data) {
      form.reset({ email: data.email, displayName: data.displayName });
    }
  }, [data, form]);

  const onSubmit = async (values: UpdateForm) => {
    await update.mutateAsync(values);
    navigate('/users');
  };

  const { register, handleSubmit, formState } = form;

  return (
    <div className="p-6 max-w-2xl mx-auto">
      <h1 className="text-2xl font-semibold mb-4">Edit User</h1>
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <div>
          <label className="block text-sm mb-1">Email</label>
          <input {...register('email')} className="w-full border rounded-xl px-3 py-2" />
          <FormError msg={formState.errors.email?.message} />
        </div>
        <div>
          <label className="block text-sm mb-1">
            Password <span className="text-xs text-gray-500">(leave blank to keep)</span>
          </label>
          <input type="password" {...register('password')} className="w-full border rounded-xl px-3 py-2" />
          <FormError msg={formState.errors.password?.message} />
        </div>
        <div>
          <label className="block text-sm mb-1">Display name</label>
          <input {...register('displayName')} className="w-full border rounded-xl px-3 py-2" />
          <FormError msg={formState.errors.displayName?.message} />
        </div>
        <div className="flex gap-2">
          <button className="rounded-2xl border px-4 py-2">Save</button>
          <button type="button" onClick={() => navigate(-1)} className="rounded-2xl border px-4 py-2">Cancel</button>
        </div>
      </form>
    </div>
  );
}

function FormError({ msg }: { msg?: string }) {
  if (!msg) return null;
  return <p className="text-xs text-red-600 mt-1">{msg}</p>;
}
