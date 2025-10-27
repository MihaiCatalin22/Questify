import http, { AuthStorage } from "./https";

export type Role = "USER" | "REVIEWER" | "ADMIN";

export type UserDTO = {
  id: number | string;
  username: string;
  email: string;
  displayName?: string;
  roles: Role[];
  createdAt: string;
  updatedAt: string;
};

export type AuthResponse = {
  user: UserDTO;
  jwt: string;
  expiresAt?: string | number | null;
};

export type LoginRequest = { usernameOrEmail: string; password: string };
export type RegisterRequest = {
  username: string;
  email: string;
  password: string;
  displayName?: string;
};

export async function login(input: LoginRequest): Promise<AuthResponse> {
  const res = await http.post<AuthResponse>("/login", input);

  let jwt = res.data?.jwt;
  const hdr: string | undefined = (res as any)?.headers?.authorization ?? (res as any)?.headers?.Authorization;
  if ((!jwt || jwt.length === 0) && typeof hdr === "string") {
    const parts = hdr.split(" ");
    if (parts.length === 2 && /^Bearer$/i.test(parts[0])) jwt = parts[1];
  }
  if (!jwt) throw new Error("Login failed: JWT not present in response");

  const auth: AuthResponse = {
    user: res.data.user,
    jwt,
    expiresAt: res.data?.expiresAt ?? null,
  };

  AuthStorage.persistAuth(auth);
  return auth;
}

export async function register(input: RegisterRequest): Promise<AuthResponse> {
  const { data } = await http.post<AuthResponse>("/register", input);
  if (!data?.jwt) throw new Error("Register failed: JWT not present");
  AuthStorage.persistAuth(data);
  return data;
}
