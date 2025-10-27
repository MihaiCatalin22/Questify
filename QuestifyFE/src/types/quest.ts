export type QuestCategory =
  | 'STUDY'
  | 'FITNESS'
  | 'HABIT'
  | 'HOBBY'
  | 'WORK'
  | 'COMMUNITY'
  | 'OTHER';

export type QuestVisibility = 'PUBLIC' | 'PRIVATE';

export interface QuestDTO {
  id: string;
  title: string;
  description?: string;
  category?: QuestCategory;
  startDate: string;   // ISO
  endDate: string;     // ISO
  createdAt: string;    // ISO
  updatedAt: string;    // ISO
  createdByUserId?: string;
  participantsCount?: number;
  completedByCurrentUser?: boolean;
  visibility?: QuestVisibility;
}

export interface CreateQuestInput {
  title: string;
  description?: string;
  category?: QuestCategory;
  startDate?: string;
  endDate?: string;
  createdByUserId: string;
  visibility: QuestVisibility;
}

export interface UpdateQuestInput {
  title?: string;
  description?: string;
  category?: QuestCategory;
  startDate?: string;
  endDate?: string;
  visibility: QuestVisibility;
}
