import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseQueryResult,
} from "@tanstack/react-query";
import { QuestsApi } from "../api/quests";
import type { QuestDTO, CreateQuestInput, UpdateQuestInput } from "../types/quest";
import type { PageResp } from "../api/quests";

export const KEY = {
  mine: ["quests", "mine"] as const,
  mineServerPage: (archived: boolean, page: number, size: number) =>
    [...KEY.mine, "serverPage", archived ? "archived" : "active", page, size] as const,
  discover: ["quests", "discover"] as const,
  detail: (id: string) => ["quests", id] as const,
};

function normalizeList<T>(payload: any): T[] {
  if (Array.isArray(payload)) return payload;
  if (!payload || typeof payload !== "object") return [];
  const candidates = [
    payload.content,
    payload.items,
    payload.data,
    payload.results,
    payload.list,
  ];
  const found = candidates.find(Array.isArray);
  return found ?? [];
}

export function useMyQuests() {
  return useQuery<QuestDTO[]>({
    queryKey: KEY.mine,
    queryFn: async () => normalizeList<QuestDTO>(await QuestsApi.listMine()),
    placeholderData: [] as QuestDTO[],
    refetchOnMount: "always",
    refetchOnWindowFocus: false,
  });
}

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
      // nuke caches derived from "mine" so the current page is refetched
      qc.invalidateQueries({ queryKey: KEY.mine });
    },
  });
}

export function useUpdateQuest(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: UpdateQuestInput) => QuestsApi.update(id, input),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEY.mine });
      qc.invalidateQueries({ queryKey: KEY.detail(id) });
      qc.invalidateQueries({ queryKey: KEY.discover });
    },
  });
}

export function useDeleteQuest() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string | number) => QuestsApi.remove(String(id)),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEY.mine });
    },
  });
}

export function useJoinQuest() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string | number) => QuestsApi.join(id),
    onSuccess: (_d, id) => {
      const qid = String(id);
      qc.invalidateQueries({ queryKey: KEY.mine });
      qc.invalidateQueries({ queryKey: KEY.discover });
      qc.invalidateQueries({ queryKey: KEY.detail(qid) });
    },
  });
}

export function useLeaveQuest() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string | number) => QuestsApi.leave(id),
    onSuccess: (_d, id) => {
      const qid = String(id);
      qc.invalidateQueries({ queryKey: KEY.mine });
      qc.invalidateQueries({ queryKey: KEY.detail(qid) });
      qc.invalidateQueries({ queryKey: KEY.discover });
    },
  });
}

export function useMyQuestsServerPage(
  tab: "ACTIVE" | "ARCHIVED",
  page: number,
  size = 12
): UseQueryResult<PageResp<QuestDTO>> {
  const archived = tab === "ARCHIVED";
  return useQuery<PageResp<QuestDTO>>({
    queryKey: KEY.mineServerPage(archived, page, size),
    queryFn: () => QuestsApi.listMinePageFiltered(archived, page, size),
    refetchOnMount: "always",
    refetchOnWindowFocus: false,
  });
}
