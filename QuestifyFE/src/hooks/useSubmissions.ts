import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { SubmissionsApi } from '../api/submissions';
import type { SubmissionDTO, CreateSubmissionInput } from '../types/submission';

const KEY = {
  all: ['submissions'] as const,
  listForQuest: (questId: string) => ['submissions', 'quest', questId] as const,
  detail: (id: string) => ['submissions', id] as const,
};

export function useSubmissions() {
  return useQuery<SubmissionDTO[]>({
    queryKey: KEY.all,
    queryFn: SubmissionsApi.list,
    staleTime: 30_000,
  });
}

export function useSubmission(id: string) {
  return useQuery<SubmissionDTO>({
    queryKey: KEY.detail(id),
    queryFn: () => SubmissionsApi.get(id),
    enabled: !!id,
  });
}

export function useSubmissionsForQuest(questId: string) {
  return useQuery<SubmissionDTO[]>({
    queryKey: KEY.listForQuest(questId),
    queryFn: () => SubmissionsApi.listByQuest(questId),
    enabled: !!questId,
  });
}

export function useCreateSubmission(questId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: CreateSubmissionInput) => SubmissionsApi.create(input),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEY.listForQuest(questId) });
      qc.invalidateQueries({ queryKey: KEY.all });
    },
  });
}
