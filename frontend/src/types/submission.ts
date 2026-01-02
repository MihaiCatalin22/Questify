export type SubmissionStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'SCANNING';

export interface SubmissionDTO {
  id: string;
  questId: string;
  userId?: string;

  comment?: string;

  proofUrl?: string;
  proofKey?: string;

  proofUrls?: string[];
  proofKeys?: string[];

  status: SubmissionStatus;
  createdAt: string;
}

export interface CreateSubmissionInput {
  questId: string;

  comment?: string;

  file?: File | null;

  files?: File[];

  proofUrl?: string;
  proofUrls?: string[];

  proofKey?: string;
  proofKeys?: string[];
}
