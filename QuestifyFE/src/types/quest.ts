export type QuestCategory =
  | 'STUDY'
  | 'FITNESS'
  | 'HABIT'
  | 'HOBBY'
  | 'WORK'
  | 'COMMUNITY'
  | 'OTHER';

export interface QuestDTO {
  id: string;
  title: string;
  description?: string;
  category?: QuestCategory;
  startDate?: string;   // ISO
  endDate?: string;     // ISO
  createdAt: string;    // ISO
  updatedAt: string;    // ISO
  createdByUserId?: string;
}

export interface CreateQuestInput {
  title: string;
  description?: string;
  category?: QuestCategory;
  startDate?: string;
  endDate?: string;
  createdByUserId: string;
}

export interface UpdateQuestInput {
  title?: string;
  description?: string;
  category?: QuestCategory;
  startDate?: string;
  endDate?: string;
}
