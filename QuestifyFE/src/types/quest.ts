export interface QuestDTO {
  id: string;
  title: string;
  description?: string;
  category?: string;
  startDate?: string;   // ISO
  endDate?: string;     // ISO
  createdAt: string;    // ISO
  updatedAt: string;    // ISO
  createdByUserId?: string;
}

export interface CreateQuestInput {
  title: string;
  description?: string;
  category?: string;
  startDate?: string;
  endDate?: string;
}

export interface UpdateQuestInput {
  title?: string;
  description?: string;
  category?: string;
  startDate?: string;
  endDate?: string;
}
