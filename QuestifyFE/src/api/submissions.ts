import http from "./https";
import type { SubmissionDTO, CreateSubmissionInput } from "../types/submission";

export const SubmissionsApi = {
  async list(): Promise<SubmissionDTO[]> {
    const { data } = await http.get<SubmissionDTO[]>("/submissions");
    return Array.isArray(data) ? data : ((data as any)?.content ?? []);
  },

  async get(id: string): Promise<SubmissionDTO> {
    const { data } = await http.get<SubmissionDTO>(`/submissions/${id}`);
    return data;
  },

  async listByQuest(questId: string): Promise<SubmissionDTO[]> {
    const { data } = await http.get<SubmissionDTO[]>(`/quests/${questId}/submissions`);
    return Array.isArray(data) ? data : ((data as any)?.content ?? []);
  },

  async create(input: CreateSubmissionInput): Promise<SubmissionDTO> {
    if (input.file) {
      const form = new FormData();
      form.set("questId", input.questId);
      if (input.comment) form.set("comment", input.comment);
      form.set("file", input.file);
      const { data } = await http.post<SubmissionDTO>("/submissions", form, {
        headers: { "Content-Type": "multipart/form-data" },
      });
      return data;
    } else {
      const { data } = await http.post<SubmissionDTO>("/submissions", {
        questId: input.questId,
        comment: input.comment,
        proofUrl: input.proofUrl,
      });
      return data;
    }
  },
};
