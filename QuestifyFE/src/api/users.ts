import http from "./https";

export type Role = "USER" | "REVIEWER" | "ADMIN";

/** Canonical DTO for the app (id is normalized to string) */
export interface UserDTO {
  id: string;
  username: string;
  email: string;
  displayName?: string;
  roles: Role[];
  createdAt: string;
  updatedAt: string;
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

function mapUser(raw: any): UserDTO {
  return {
    id: String(raw.id),
    username: String(raw.username ?? ""),
    email: String(raw.email ?? ""),
    displayName: raw.displayName ?? undefined,
    roles: Array.isArray(raw.roles) ? (raw.roles as Role[]) : [],
    createdAt: String(raw.createdAt ?? ""),
    updatedAt: String(raw.updatedAt ?? ""),
  };
}

export const UsersApi = {
  async list(): Promise<UserDTO[]> {
    const { data } = await http.get<any>("/users");
    if (Array.isArray(data)) return data.map(mapUser);
    const content = (data as any)?.content;
    return Array.isArray(content) ? content.map(mapUser) : [];
  },

  async get(id: string): Promise<UserDTO> {
    const { data } = await http.get<any>(`/users/${id}`);
    return mapUser(data);
  },

  async create(input: CreateUserInput): Promise<UserDTO> {
    const { data } = await http.post<any>("/users", input);
    return mapUser(data);
  },

  async update(id: string, input: UpdateUserInput): Promise<UserDTO> {
    const { data } = await http.put<any>(`/users/${id}`, input);
    return mapUser(data);
  },

  async remove(id: string): Promise<void> {
    await http.delete(`/users/${id}`);
  },
};
