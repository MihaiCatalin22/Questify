import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { QuestsApi } from "../api/quests";
import type { QuestDTO, CreateQuestInput, UpdateQuestInput } from "../types/quest";

export const KEY = {
  all: ["quests"] as const,
  detail: (id: string) => ["quests", id] as const,
};

// Normalize any backend list shape to a plain array
function normalizeList<T>(payload: any): T[] {
  if (Array.isArray(payload)) return payload;
  if (!payload || typeof payload !== "object") return [];
  const candidates = [payload.content, payload.items, payload.data, payload.results, payload.list];
  const found = candidates.find(Array.isArray);
  return found ?? [];
}

export function useQuests() {
  return useQuery<QuestDTO[]>({
    queryKey: KEY.all,
    queryFn: async () => normalizeList<QuestDTO>(await QuestsApi.list()),
    initialData: [],
    staleTime: 30_000,
    // optional: if you don’t want refetch on tab focus
    refetchOnWindowFocus: false,
  });
}

export function useQuest(id: string) {
  return useQuery<QuestDTO>({
    queryKey: KEY.detail(id),
    queryFn: () => QuestsApi.get(id),
    enabled: !!id,
    // 👇 ensure a fresh fetch each time you navigate to the detail page
    refetchOnMount: "always",
    refetchOnWindowFocus: false,
    staleTime: 0,
  });
}

export function useCreateQuest() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: CreateQuestInput) => QuestsApi.create(input),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEY.all });
    },
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
    mutationFn: (id: string | number) => QuestsApi.remove(String(id)),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEY.all });
    },
  });
}
