import http from "./https";
import type { QuestDTO, CreateQuestInput, UpdateQuestInput } from "../types/quest";

export const QuestsApi = {
  async listMine(): Promise<QuestDTO[]> {
    const { data } = await http.get<{ content?: QuestDTO[] } | QuestDTO[]>("/quests/mine");
    return Array.isArray(data) ? data : (data?.content ?? []);
  },

  async listDiscover(): Promise<QuestDTO[]> {
    const { data } = await http.get<{ content?: QuestDTO[] } | QuestDTO[]>("/quests/discover");
    return Array.isArray(data) ? data : (data?.content ?? []);
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

  async remove(id: string): Promise<void> {
    await http.delete(`/quests/${id}`);
  },

  async join(id: string | number): Promise<{ ok: true }> {
    const { data } = await http.post<{ ok: true }>(`/quests/${id}/join`, {});
    return data;
  },

  async leave(id: string | number): Promise<{ ok: true }> {
    await http.delete(`/quests/${id}/join`);
    return { ok: true };
  }
};
