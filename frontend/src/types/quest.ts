export type QuestCategory =
  | 'STUDY'
  | 'FITNESS'
  | 'HABIT'
  | 'HOBBY'
  | 'WORK'
  | 'COMMUNITY'
  | 'OTHER';

export type QuestVisibility = 'PUBLIC' | 'PRIVATE';

export type QuestStatus = 'DRAFT' | 'ACTIVE' | 'COMPLETED' | 'ARCHIVED';

export interface VerificationPolicyDTO {
  requiredEvidence: string[];
  optionalEvidence: string[];
  disqualifiers: string[];
  minSupportScore: number;
  taskType?: string | null;
}

export interface QuestDTO {
  id: number;                    // Long in service
  title: string;
  description: string;
  category: QuestCategory;
  status: QuestStatus;
  startDate: string;             // ISO instant
  endDate: string;               // ISO instant
  createdAt?: string;             // ISO instant
  updatedAt?: string;             // ISO instant
  createdByUserId: string;
  participantsCount?: number;     // server-calculated
  visibility: QuestVisibility;
  completedByCurrentUser?: boolean;
  verificationPolicy?: VerificationPolicyDTO | null;
}


export interface CreateQuestInput {
  title: string;
  description: string;
  category: QuestCategory;
  startDate: string;             // ISO instant
  endDate: string;               // ISO instant
  createdByUserId: string;
  visibility: QuestVisibility;
  verificationPolicy?: VerificationPolicyDTO | null;
}


export interface UpdateQuestInput {
  title: string;
  description: string;
  category: QuestCategory;
  startDate?: string;            // ISO instant
  endDate?: string;              // ISO instant
  visibility: QuestVisibility;
  verificationPolicy?: VerificationPolicyDTO | null;
}
