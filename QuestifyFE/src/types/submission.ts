export type SubmissionStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface SubmissionDTO {
  id: string;
  questId: string;
  userId?: string;
  comment?: string;
  proofUrl?: string; 
  status: SubmissionStatus;
  createdAt: string; 
}

export interface CreateSubmissionInput {
  questId: string;
  comment?: string;
  file?: File | null;
  proofUrl?: string; 
}
