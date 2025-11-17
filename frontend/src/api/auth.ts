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


export async function login(_input: LoginRequest): Promise<AuthResponse> {
  throw new Error("Use OIDC login (AuthContext.login) instead of /api/login.");
}
export async function register(_input: RegisterRequest): Promise<AuthResponse> {
  throw new Error("Use OIDC register (AuthContext.login({ register: true })) instead of /api/register.");
}
