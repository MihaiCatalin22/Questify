export type SubmissionStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface SubmissionDTO {
  id: string;
  questId: string;
  userId?: string;
  comment?: string;
  proofUrl?: string; // where the file/image/video is stored
  status: SubmissionStatus;
  createdAt: string; // ISO
}

export interface CreateSubmissionInput {
  questId: string;
  comment?: string;
  // when uploading a file we’ll send as multipart
  file?: File | null;
  proofUrl?: string; // alternative to file if backend expects URL
}
