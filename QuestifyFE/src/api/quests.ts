import { http } from './https';
import type { QuestDTO, CreateQuestInput, UpdateQuestInput } from '../types/quest';

export const QuestsApi = {
  async list(): Promise<QuestDTO[]> {
    const { data } = await http.get<QuestDTO[]>('/quests');
    return data;
  },
  async get(id: string): Promise<QuestDTO> {
    const { data } = await http.get<QuestDTO>(`/quests/${id}`);
    return data;
  },
  async create(input: CreateQuestInput): Promise<QuestDTO> {
    const { data } = await http.post<QuestDTO>('/quests', input);
    return data;
  },
  async update(id: string, input: UpdateQuestInput): Promise<QuestDTO> {
    const { data } = await http.put<QuestDTO>(`/quests/${id}`, input);
    return data;
  },
  async remove(id: string): Promise<void> {
    await http.delete(`/quests/${id}`);
  },
};
