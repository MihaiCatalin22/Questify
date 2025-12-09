import http from "./https";
import type { QuestDTO, CreateQuestInput, UpdateQuestInput } from "../types/quest";

export type PageResp<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number; // 0-based page index
  size: number;
  first: boolean;
  last: boolean;
};

type StatusFilter = "ACTIVE" | "ARCHIVED";

export const QuestsApi = {
  async listMine(): Promise<QuestDTO[]> {
    const { data } = await http.get<{ content?: QuestDTO[] } | QuestDTO[]>(
      "/quests/mine-or-participating"
    );
    return Array.isArray(data) ? data : data?.content ?? [];
  },

  async listMinePage(
    page = 0,
    size = 10,
    status?: StatusFilter
  ): Promise<PageResp<QuestDTO>> {
    const params: Record<string, any> = { page, size, sort: "createdAt,desc" };
    if (status) params.status = status;

    const { data } = await http.get("/quests/mine-or-participating", { params });

    if (Array.isArray(data)) {
      return {
        content: data,
        totalElements: data.length,
        totalPages: 1,
        number: 0,
        size: data.length,
        first: true,
        last: true,
      };
    }
    return data as PageResp<QuestDTO>;
  },

  async listDiscover(): Promise<QuestDTO[]> {
    const { data } = await http.get<{ content?: QuestDTO[] } | QuestDTO[]>(
      "/quests/discover"
    );
    return Array.isArray(data) ? data : data?.content ?? [];
  },

  async get(id: string): Promise<QuestDTO> {
    const { data } = await http.get<QuestDTO>(`/quests/${id}`);
    return data;
  },

  async create(input: CreateQuestInput): Promise<QuestDTO> {
    const { data } = await http.post<QuestDTO>("/quests", input);
    return data;
  },

  async update(id: string, input: UpdateQuestInput): Promise<QuestDTO> {
    const { data } = await http.put<QuestDTO>(`/quests/${id}`, input);
    return data;
  },

  async archive(id: string | number): Promise<QuestDTO> {
    const { data } = await http.post<QuestDTO>(`/quests/${id}/archive`, {});
    return data;
  },

  async remove(id: string): Promise<void> {
    await http.post(`/quests/${id}/archive`, {});
  },

  async join(id: string | number): Promise<{ ok: true }> {
    await http.post(`/quests/${id}/join`, {});
    return { ok: true };
  },

  async leave(id: string | number): Promise<{ ok: true }> {
    await http.delete(`/quests/${id}/join`);
    return { ok: true };
  },
};
