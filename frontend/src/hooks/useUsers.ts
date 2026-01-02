import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  UsersApi,
  type UserDTO,
  type CreateUserInput,
  type UpdateUserInput,
  type UpdateMeInput,
  type DeleteMeRes,
} from "../api/users";

const KEY = {
  all: (q: string) => ["users", "list", q] as const,
  detail: (id: string) => ["users", "detail", id] as const,
  me: ["users", "me"] as const,
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

export function useUsers(q: string = "") {
  return useQuery<UserDTO[]>({
    queryKey: KEY.all(q ?? ""),
    queryFn: () => UsersApi.list(q ?? ""),
    placeholderData: [] as UserDTO[],
    refetchOnMount: "always",
    refetchOnWindowFocus: false,
  });
}

export function useUser(id: string) {
  return useQuery<UserDTO>({
    queryKey: KEY.detail(id),
    queryFn: () => UsersApi.get(id),
    enabled: !!id,
    refetchOnMount: "always",
    refetchOnWindowFocus: false,
  });
}

export function useMe() {
  return useQuery<UserDTO>({
    queryKey: KEY.me,
    queryFn: () => UsersApi.me(),
    refetchOnMount: "always",
    refetchOnWindowFocus: false,
  });
}

export function useUpdateMe() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: UpdateMeInput) => UsersApi.updateMe(sanitize(input) as UpdateMeInput),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEY.me });
    },
  });
}

export function useDeleteMe() {
  const qc = useQueryClient();
  return useMutation<DeleteMeRes, unknown, void>({
    mutationFn: () => UsersApi.deleteMe(),
    onSuccess: async () => {
      await qc.invalidateQueries();
    },
  });
}


export function useCreateUser() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: CreateUserInput) => UsersApi.create(input),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEY.all("") });
    },
  });
}

export function useUpdateUser(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: Partial<UpdateUserInput>) =>
      UsersApi.update(id, sanitize(input) as UpdateUserInput),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEY.all("") });
      qc.invalidateQueries({ queryKey: KEY.detail(id) });
    },
  });
}

export function useDeleteUser() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string | number) => UsersApi.remove(String(id)),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEY.all("") });
    },
  });
}
