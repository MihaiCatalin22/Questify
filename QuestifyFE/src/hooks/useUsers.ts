import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  UsersApi,
  type UserDTO,
  type CreateUserInput,
  type UpdateUserInput,
} from "../api/users";


const KEY = {
  all: ["users"] as const,
  detail: (id: string) => ["users", id] as const,
};

function sanitize<T extends object>(obj: T): Partial<T> {
  const out = {} as Partial<T>;
  for (const key of Object.keys(obj) as Array<keyof T>) {
    const v = (obj as any)[key];
    if (v === "" || v === undefined) continue;
    (out as any)[key] = v;
  }
  return out;
}

export function useUsers() {
  return useQuery<UserDTO[]>({
    queryKey: KEY.all,
    queryFn: UsersApi.list, 
    initialData: [],
    staleTime: 30_000,
  });
}

export function useUser(id: string) {
  return useQuery<UserDTO>({
    queryKey: KEY.detail(id),
    queryFn: () => UsersApi.get(id),
    enabled: !!id,
  });
}

export function useCreateUser() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: CreateUserInput) => UsersApi.create(input),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEY.all });
    },
  });
}

export function useUpdateUser(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: Partial<UpdateUserInput>) =>
      UsersApi.update(id, sanitize(input) as UpdateUserInput),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEY.all });
      qc.invalidateQueries({ queryKey: KEY.detail(id) });
    },
  });
}

export function useDeleteUser() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => UsersApi.remove(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEY.all });
    },
  });
}
