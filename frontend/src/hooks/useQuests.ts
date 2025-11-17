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
    onMutate: async (id) => {
      const qid = String(id);
      await Promise.all([
        qc.cancelQueries({ queryKey: KEY.mine }),
        qc.cancelQueries({ queryKey: KEY.discover }),
        qc.cancelQueries({ queryKey: KEY.detail(qid) }),
      ]);

      const prevMine = (qc.getQueryData(KEY.mine) as QuestDTO[] | undefined) ?? [];
      const prevDiscover = (qc.getQueryData(KEY.discover) as QuestDTO[] | undefined) ?? [];
      const prevDetail = qc.getQueryData(KEY.detail(qid)) as QuestDTO | undefined;

      const candidate =
        prevDiscover.find(q => String(q.id) === qid) ?? prevDetail;

      if (candidate && !prevMine.some(q => String(q.id) === qid)) {
        qc.setQueryData<QuestDTO[]>(KEY.mine, [...prevMine, candidate]);
      }

      if (prevDetail) {
        qc.setQueryData<QuestDTO>(KEY.detail(qid), {
          ...prevDetail,
          participantsCount: (prevDetail.participantsCount ?? 0) + 1,
        });
      }

      if (candidate) {
        const nextDiscover = prevDiscover.map(q =>
          String(q.id) === qid
            ? { ...q, participantsCount: (q.participantsCount ?? 0) + 1 }
            : q
        );
        qc.setQueryData(KEY.discover, nextDiscover);
      }

      return { prevMine, prevDiscover, prevDetail };
    },
    onError: (_e, id, ctx) => {
      const qid = String(id);
      if (!ctx) return;
      qc.setQueryData(KEY.mine, ctx.prevMine);
      qc.setQueryData(KEY.discover, ctx.prevDiscover);
      if (ctx.prevDetail) qc.setQueryData(KEY.detail(qid), ctx.prevDetail);
    },
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
    onMutate: async (id) => {
      const qid = String(id);
      await Promise.all([
        qc.cancelQueries({ queryKey: KEY.mine }),
        qc.cancelQueries({ queryKey: KEY.detail(qid) }),
        qc.cancelQueries({ queryKey: KEY.discover }),
      ]);

      const prevMine = (qc.getQueryData(KEY.mine) as QuestDTO[] | undefined) ?? [];
      const prevDetail = qc.getQueryData(KEY.detail(qid)) as QuestDTO | undefined;
      const prevDiscover = (qc.getQueryData(KEY.discover) as QuestDTO[] | undefined) ?? [];

      qc.setQueryData<QuestDTO[]>(KEY.mine, prevMine.filter(q => String(q.id) !== qid));

      if (prevDetail) {
        qc.setQueryData<QuestDTO>(KEY.detail(qid), {
          ...prevDetail,
          participantsCount: Math.max(0, (prevDetail.participantsCount ?? 0) - 1),
        });
      }

      const nextDiscover = prevDiscover.map(q =>
        String(q.id) === qid
          ? { ...q, participantsCount: Math.max(0, (q.participantsCount ?? 0) - 1) }
          : q
      );
      qc.setQueryData(KEY.discover, nextDiscover);

      return { prevMine, prevDetail, prevDiscover };
    },
    onError: (_e, id, ctx) => {
      const qid = String(id);
      if (!ctx) return;
      qc.setQueryData(KEY.mine, ctx.prevMine);
      if (ctx.prevDetail) qc.setQueryData(KEY.detail(qid), ctx.prevDetail);
      qc.setQueryData(KEY.discover, ctx.prevDiscover);
    },
    onSuccess: (_d, id) => {
      const qid = String(id);
      qc.invalidateQueries({ queryKey: KEY.mine });
      qc.invalidateQueries({ queryKey: KEY.detail(qid) });
      qc.invalidateQueries({ queryKey: KEY.discover }); 
    },
  });
}
