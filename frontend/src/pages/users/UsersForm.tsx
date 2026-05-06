import { useNavigate, useParams } from 'react-router-dom';
import { useCreateUser, useUpdateUser, useUser } from '../../hooks/useUsers';
import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Button,
  FieldLabel,
  PageHeader,
  PageShell,
  Panel,
  TextInput,
} from '../../components/ui';

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
    <PageShell className="mx-auto max-w-2xl">
      <PageHeader title="Create User" description="This form is retained for compatibility; Keycloak handles user creation." />
      <Panel className="p-5">
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <div>
          <FieldLabel>Username</FieldLabel>
          <TextInput {...register('username')} />
          <FormError msg={formState.errors.username?.message} />
        </div>
        <div>
          <FieldLabel>Email</FieldLabel>
          <TextInput {...register('email')} />
          <FormError msg={formState.errors.email?.message} />
        </div>
        <div>
          <FieldLabel>Password</FieldLabel>
          <TextInput type="password" {...register('password')} />
          <FormError msg={formState.errors.password?.message} />
        </div>
        <div>
          <FieldLabel>Display name</FieldLabel>
          <TextInput {...register('displayName')} />
          <FormError msg={formState.errors.displayName?.message} />
        </div>
        <div className="flex gap-2">
          <Button variant="primary">Save</Button>
        </div>
      </form>
      </Panel>
    </PageShell>
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
    <PageShell className="mx-auto max-w-2xl">
      <PageHeader title="Edit User" description="This form is retained for compatibility; Keycloak is the source of truth." />
      <Panel className="p-5">
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <div>
          <FieldLabel>Email</FieldLabel>
          <TextInput {...register('email')} />
          <FormError msg={formState.errors.email?.message} />
        </div>
        <div>
          <FieldLabel>
            Password <span className="text-xs text-[rgb(var(--faint))]">(leave blank to keep)</span>
          </FieldLabel>
          <TextInput type="password" {...register('password')} />
          <FormError msg={formState.errors.password?.message} />
        </div>
        <div>
          <FieldLabel>Display name</FieldLabel>
          <TextInput {...register('displayName')} />
          <FormError msg={formState.errors.displayName?.message} />
        </div>
        <div className="flex gap-2">
          <Button variant="primary">Save</Button>
          <Button type="button" onClick={() => navigate(-1)}>Cancel</Button>
        </div>
      </form>
      </Panel>
    </PageShell>
  );
}

function FormError({ msg }: { msg?: string }) {
  if (!msg) return null;
  return <p className="text-xs text-red-300 mt-1">{msg}</p>;
}
