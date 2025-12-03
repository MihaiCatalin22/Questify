import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { SubmissionsApi } from '../api/submissions';
import type { SubmissionDTO, CreateSubmissionInput, SubmissionStatus } from '../types/submission';

export const KEY = {
  byStatus: (status: SubmissionStatus | "ALL") => ['submissions', 'status', status] as const,
  listForQuest: (questId: string) => ['submissions', 'quest', questId] as const,
  detail: (id: string) => ['submissions', id] as const,
};

export function useSubmissions(status: SubmissionStatus | "ALL" = "ALL") {
  return useQuery<SubmissionDTO[]>({
    queryKey: KEY.byStatus(status),
    queryFn: () => SubmissionsApi.list(status),
    placeholderData: [] as SubmissionDTO[],
    refetchOnMount: 'always',
    refetchOnWindowFocus: false,
  });
}

export function useSubmission(id: string) {
  return useQuery<SubmissionDTO>({
    queryKey: KEY.detail(id),
    queryFn: () => SubmissionsApi.get(id),
    enabled: !!id,
    refetchOnMount: 'always',
    refetchOnWindowFocus: false,
  });
}

export function useSubmissionsForQuest(questId: string) {
  return useQuery<SubmissionDTO[]>({
    queryKey: KEY.listForQuest(questId),
    queryFn: () => SubmissionsApi.listByQuest(questId),
    enabled: !!questId,
    placeholderData: [] as SubmissionDTO[],
    refetchOnMount: 'always',
    refetchOnWindowFocus: false,
  });
}

export function useCreateSubmission(questId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: CreateSubmissionInput) => SubmissionsApi.create(input),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEY.listForQuest(questId) });
      qc.invalidateQueries({ queryKey: KEY.byStatus("ALL") });
      qc.invalidateQueries({ queryKey: KEY.byStatus("PENDING") });
      qc.invalidateQueries({ queryKey: KEY.byStatus("APPROVED") });
      qc.invalidateQueries({ queryKey: KEY.byStatus("REJECTED") });
    },
  });
}

export function useReviewSubmission(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { reviewStatus: "APPROVED" | "REJECTED"; reviewNote?: string }) =>
      SubmissionsApi.review(id, input),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEY.detail(id) });
      qc.invalidateQueries({ queryKey: KEY.byStatus("ALL") });
      qc.invalidateQueries({ queryKey: KEY.byStatus("PENDING") });
      qc.invalidateQueries({ queryKey: KEY.byStatus("APPROVED") });
      qc.invalidateQueries({ queryKey: KEY.byStatus("REJECTED") });
    },
  });
}
