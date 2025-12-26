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

export interface UserExportDTO {
  userId: string;
  username?: string | null;
  displayName?: string | null;
  email?: string | null;
  bio?: string | null;

  createdAt: string;
  updatedAt: string;
  deletionRequestedAt?: string | null;
  deletedAt?: string | null;
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

export type UserProfileDTO = UserDTO;
export type UpsertMeReq = UpdateMeInput;

function mapUser(raw: any): UserDTO {
  const id = String(raw?.id ?? raw?.userId ?? "");
  return {
    id,
    username: String(raw?.username ?? ""),
    email: String(raw?.email ?? ""),
    displayName: raw?.displayName ?? undefined,
    roles: Array.isArray(raw?.roles) ? (raw.roles as Role[]) : [],

    createdAt: String(raw?.createdAt ?? ""),
    updatedAt: String(raw?.updatedAt ?? ""),

    bio: raw?.bio ?? null,

    deletedAt: raw?.deletedAt ?? null,
    deletionRequestedAt: raw?.deletionRequestedAt ?? null,
  };
}

export const UsersApi = {
  async list(username: string = ""): Promise<UserDTO[]> {
    const { data } = await http.get<any>("/users", { params: { username } });
    if (Array.isArray(data)) return data.map(mapUser);
    const content = (data as any)?.content;
    return Array.isArray(content) ? content.map(mapUser) : [];
  },

  async get(id: string): Promise<UserDTO> {
    const { data } = await http.get<any>(`/users/${encodeURIComponent(id)}`);
    return mapUser(data);
  },

  async me(): Promise<UserProfileDTO> {
    const { data } = await http.get<any>("/users/me", {
      headers: { "Cache-Control": "no-store" },
    });
    return mapUser(data);
  },

  async updateMe(input: UpsertMeReq): Promise<UserProfileDTO> {
    const { data } = await http.put<any>("/users/me", input, {
      headers: { "Cache-Control": "no-store" },
    });
    return mapUser(data);
  },

  async exportMe(): Promise<UserExportDTO> {
    const { data } = await http.get<UserExportDTO>("/users/me/export", {
      headers: { "Cache-Control": "no-store" },
    });
    return data;
  },

  async deleteMe(): Promise<DeleteMeRes> {
    const { data } = await http.delete<DeleteMeRes>("/users/me", {
      headers: { "Cache-Control": "no-store" },
    });
    return data;
  },

  async create(_input: CreateUserInput): Promise<UserDTO> {
    throw new Error("User creation is handled by Keycloak/OIDC.");
  },

  async update(_id: string, _input: UpdateUserInput): Promise<UserDTO> {
    throw new Error("User updates are handled by Keycloak/OIDC.");
  },

  async remove(_id: string): Promise<void> {
    throw new Error("User deletion is handled by Keycloak/OIDC.");
  },
};
