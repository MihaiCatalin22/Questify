import http from "./https";

export type Role = "USER" | "REVIEWER" | "ADMIN";

export interface UserDTO {
  id: string;
  username: string;
  email: string;
  displayName?: string;
  roles: Role[];
  createdAt: string;
  updatedAt: string;

  bio?: string | null;

  deletedAt?: string | null;
  deletionRequestedAt?: string | null;
}

export interface CreateUserInput {
  username: string;
  email: string;
  password: string;
  displayName?: string;
}

export interface UpdateUserInput {
  username?: string;
  email?: string;
  password?: string;
  displayName?: string;
  roles?: Role[];
}

export interface DeleteMeRes {
  userId: string;
  deletionRequestedAt?: string | null;
  deletedAt?: string | null;
}

export interface UpdateMeInput {
  displayName?: string | null;
  bio?: string | null;
}

export interface CoachSettingsDTO {
  aiCoachEnabled: boolean;
  coachGoal?: string | null;
}

export interface UpdateCoachSettingsInput {
  aiCoachEnabled: boolean;
  coachGoal?: string | null;
}

export type UserProfileDTO = UserDTO;
export type UpsertMeReq = UpdateMeInput;

export type ExportJobStatus = "PENDING" | "RUNNING" | "COMPLETED" | "FAILED" | "EXPIRED";

export interface ExportJobCreatedDTO {
  jobId: string;
  status: ExportJobStatus;
  expiresAt?: string;
}

export interface ExportJobDTO {
  jobId: string;
  status: ExportJobStatus;
  createdAt?: string;
  expiresAt?: string;
  errorMessage?: string;

  lastProgressAt?: string;
  failureReason?: string | null;
  missingParts?: string[];
}

export interface ExportJobDownloadDTO {
  url: string;
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return typeof value === "object" && value !== null ? (value as Record<string, unknown>) : null;
}

function readString(value: unknown, fallback: string = "") {
  if (typeof value === "string") return value;
  if (value == null) return fallback;
  return String(value);
}

function readOptionalString(value: unknown) {
  return typeof value === "string" ? value : undefined;
}

function readNullableString(value: unknown) {
  return typeof value === "string" ? value : null;
}

function readRoles(value: unknown): Role[] {
  if (!Array.isArray(value)) return [];
  return value.filter((item): item is Role => item === "USER" || item === "REVIEWER" || item === "ADMIN");
}

function mapUser(rawInput: unknown): UserDTO {
  const raw = asRecord(rawInput);
  const id = readString(raw?.id ?? raw?.userId, "");
  return {
    id,
    username: readString(raw?.username, ""),
    email: readString(raw?.email, ""),
    displayName: readOptionalString(raw?.displayName),
    roles: readRoles(raw?.roles),

    createdAt: readString(raw?.createdAt, ""),
    updatedAt: readString(raw?.updatedAt, ""),

    bio: readNullableString(raw?.bio),

    deletedAt: readNullableString(raw?.deletedAt),
    deletionRequestedAt: readNullableString(raw?.deletionRequestedAt),
  };
}

export const UsersApi = {
  async list(username: string = ""): Promise<UserDTO[]> {
    const { data } = await http.get<unknown>("/users", { params: { username } });
    if (Array.isArray(data)) return data.map(mapUser);
    const content = asRecord(data)?.content;
    return Array.isArray(content) ? content.map(mapUser) : [];
  },

  async get(id: string): Promise<UserDTO> {
    const { data } = await http.get<unknown>(`/users/${encodeURIComponent(id)}`);
    return mapUser(data);
  },

  async me(): Promise<UserProfileDTO> {
    const { data } = await http.get<unknown>("/users/me", {
      headers: { "Cache-Control": "no-store" },
    });
    return mapUser(data);
  },

  async updateMe(input: UpsertMeReq): Promise<UserProfileDTO> {
    const { data } = await http.put<unknown>("/users/me", input, {
      headers: { "Cache-Control": "no-store" },
    });
    return mapUser(data);
  },

  async deleteMe(): Promise<DeleteMeRes> {
    const { data } = await http.delete<DeleteMeRes>("/users/me", {
      headers: { "Cache-Control": "no-store" },
    });
    return data;
  },

  async getCoachSettings(): Promise<CoachSettingsDTO> {
    const { data } = await http.get<CoachSettingsDTO>("/users/me/coach-settings", {
      headers: { "Cache-Control": "no-store" },
    });
    return {
      aiCoachEnabled: Boolean(data?.aiCoachEnabled),
      coachGoal: data?.coachGoal ?? null,
    };
  },

  async updateCoachSettings(input: UpdateCoachSettingsInput): Promise<CoachSettingsDTO> {
    const { data } = await http.put<CoachSettingsDTO>("/users/me/coach-settings", input, {
      headers: { "Cache-Control": "no-store" },
    });
    return {
      aiCoachEnabled: Boolean(data?.aiCoachEnabled),
      coachGoal: data?.coachGoal ?? null,
    };
  },

  async requestExportJob(): Promise<ExportJobCreatedDTO> {
    const { data } = await http.post<ExportJobCreatedDTO>("/users/me/export-jobs");
    return data;
  },

  async getExportJob(jobId: string): Promise<ExportJobDTO> {
    const { data } = await http.get<ExportJobDTO>(
      `/users/me/export-jobs/${encodeURIComponent(jobId)}`,
      { headers: { "Cache-Control": "no-store" } }
    );
    return data;
  },

  async getExportDownloadUrl(jobId: string): Promise<string> {
    const { data } = await http.get<ExportJobDownloadDTO>(
      `/users/me/export-jobs/${encodeURIComponent(jobId)}/download`,
      { headers: { "Cache-Control": "no-store" } }
    );
    return data.url;
  },

  async create(_input: CreateUserInput): Promise<UserDTO> {
    void _input;
    throw new Error("User creation is handled by Keycloak/OIDC.");
  },

  async update(_id: string, _input: UpdateUserInput): Promise<UserDTO> {
    void _id;
    void _input;
    throw new Error("User updates are handled by Keycloak/OIDC.");
  },

  async remove(_id: string): Promise<void> {
    void _id;
    throw new Error("User deletion is handled by Keycloak/OIDC.");
  },
};
