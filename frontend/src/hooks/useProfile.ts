import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { UsersApi, type UpsertMeReq, type UserProfileDTO, type DeleteMeRes } from "../api/users";

const KEY = {
  me: ["me"] as const,
};

export function useMe() {
  return useQuery<UserProfileDTO>({
    queryKey: KEY.me,
    queryFn: () => UsersApi.me(),
    refetchOnWindowFocus: false,
  });
}

export function useUpsertMe() {
  const qc = useQueryClient();
  return useMutation<UserProfileDTO, unknown, UpsertMeReq>({
    mutationFn: (input: UpsertMeReq) => UsersApi.updateMe(input),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: KEY.me });
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
