import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { QuestsApi } from "../api/quests";
import type { QuestDTO, CreateQuestInput, UpdateQuestInput } from "../types/quest";

export const KEY = {
  mine: ["quests", "mine"] as const,
  discover: ["quests", "discover"] as const,
  detail: (id: string) => ["quests", id] as const,
};

function normalizeList<T>(payload: any): T[] {
  if (Array.isArray(payload)) return payload;
  if (!payload || typeof payload !== "object") return [];
  const candidates = [payload.content, payload.items, payload.data, payload.results, payload.list];
  const found = candidates.find(Array.isArray);
  return found ?? [];
}

/** Quests I own or have joined */
export function useMyQuests() {
  return useQuery<QuestDTO[]>({
    queryKey: KEY.mine,
    queryFn: async () => normalizeList<QuestDTO>(await QuestsApi.listMine()),
    placeholderData: [] as QuestDTO[],
    refetchOnMount: "always",
    refetchOnWindowFocus: false,
  });
}

/** Public quests I can join (I’m not owner/participant) */
export function useDiscoverQuests() {
  return useQuery<QuestDTO[]>({
    queryKey: KEY.discover,
    queryFn: async () => normalizeList<QuestDTO>(await QuestsApi.listDiscover()),
    placeholderData: [] as QuestDTO[],
    refetchOnMount: "always",
    refetchOnWindowFocus: false,
  });
}

export function useQuest(id: string) {
  return useQuery<QuestDTO>({
    queryKey: KEY.detail(id),
    queryFn: () => QuestsApi.get(id),
    enabled: !!id,
    refetchOnMount: "always",
    refetchOnWindowFocus: false,
  });
}

export function useCreateQuest() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: CreateQuestInput) => QuestsApi.create(input),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEY.mine });
      qc.invalidateQueries({ queryKey: KEY.discover });
    },
  });
}

export function useUpdateQuest(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: UpdateQuestInput) => QuestsApi.update(id, input),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEY.mine });
      qc.invalidateQueries({ queryKey: KEY.discover });
      qc.invalidateQueries({ queryKey: KEY.detail(id) });
    },
  });
}

export function useDeleteQuest() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string | number) => QuestsApi.remove(String(id)),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEY.mine });
      qc.invalidateQueries({ queryKey: KEY.discover });
    },
  });
}

export function useJoinQuest() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string | number) => QuestsApi.join(id),
    onSuccess: (_d, id) => {
      qc.invalidateQueries({ queryKey: KEY.mine });
      qc.invalidateQueries({ queryKey: KEY.discover });
      qc.invalidateQueries({ queryKey: KEY.detail(String(id)) });
    },
  });
}

export function useLeaveQuest() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string | number) => QuestsApi.leave(id),
    onSuccess: (_d, id) => {
      qc.invalidateQueries({ queryKey: KEY.mine });
      qc.invalidateQueries({ queryKey: KEY.discover });
      qc.invalidateQueries({ queryKey: KEY.detail(String(id)) });
    },
  });
}
