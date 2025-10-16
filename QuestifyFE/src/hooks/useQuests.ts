import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { QuestsApi } from '../api/quests';
import type { QuestDTO, CreateQuestInput, UpdateQuestInput } from '../types/quest';

const KEY = {
  all: ['quests'] as const,
  detail: (id: string) => [...KEY.all, id] as const,
};

export function useQuests() {
  return useQuery<QuestDTO[]>({
    queryKey: KEY.all,
    queryFn: QuestsApi.list,
    staleTime: 30_000,
  });
}

export function useQuest(id: string) {
  return useQuery<QuestDTO>({
    queryKey: KEY.detail(id),
    queryFn: () => QuestsApi.get(id),
    enabled: !!id,
  });
}

export function useCreateQuest() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: CreateQuestInput) => QuestsApi.create(input),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY.all }),
  });
}

export function useUpdateQuest(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: UpdateQuestInput) => QuestsApi.update(id, input),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEY.all });
      qc.invalidateQueries({ queryKey: KEY.detail(id) });
    },
  });
}

export function useDeleteQuest() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => QuestsApi.remove(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY.all }),
  });
}
