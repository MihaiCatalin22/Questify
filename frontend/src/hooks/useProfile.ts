import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { UsersApi, type UpsertMeReq, type UserExportDTO, type UserProfileDTO, type DeleteMeRes } from "../api/users";

const KEY = {
  me: ["me"] as const,
  export: ["me", "export"] as const,
};

export function useMe() {
  return useQuery<UserProfileDTO>({
    queryKey: KEY.me,
    queryFn: () => UsersApi.me(),
    refetchOnWindowFocus: false,
  });
}

export function useMeExport(enabled: boolean = true) {
  return useQuery<UserExportDTO>({
    queryKey: KEY.export,
    queryFn: () => UsersApi.exportMe(),
    enabled,
    refetchOnWindowFocus: false,
  });
}

export function useUpdateMe() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: UpsertMeReq) => UsersApi.updateMe(input),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: KEY.me });
      await qc.invalidateQueries({ queryKey: KEY.export });
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
