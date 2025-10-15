import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { UsersApi } from '../api/users';
import type { CreateUserInput, UpdateUserInput, UserDTO } from '../types/user';


const KEY = {
all: ['users'] as const,
detail: (id: string) => [...KEY.all, id] as const,
};


export function useUsers() {
return useQuery<UserDTO[]>({
queryKey: KEY.all,
queryFn: UsersApi.list,
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
onSuccess: () => qc.invalidateQueries({ queryKey: KEY.all }),
});
}


export function useUpdateUser(id: string) {
const qc = useQueryClient();
return useMutation({
mutationFn: (input: UpdateUserInput) => UsersApi.update(id, input),
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
onSuccess: () => qc.invalidateQueries({ queryKey: KEY.all }),
});
}