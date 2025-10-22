
import { http } from './https' 
import { setAuthHeader } from './https';

export type Role = 'USER' | 'REVIEWER' | 'ADMIN';

export type UserDTO = {
  id: number;
  username: string;
  email: string;
  displayName: string;
  roles: Role[];
  createdAt: string;
  updatedAt: string;
};

export type LoginRequest = { usernameOrEmail: string; password: string };
export type RegisterRequest = { username: string; email: string; password: string; displayName: string };

export type AuthResponse = {
  user: UserDTO;
  jwt: string;
  expiresAt: string;
};

const STORAGE_KEY = 'user';

export async function login(input: LoginRequest): Promise<AuthResponse> {
  const { data } = await http.post<AuthResponse>('/login', input);
  return data;
}

export async function register(input: RegisterRequest): Promise<AuthResponse> {
  const { data } = await http.post<AuthResponse>('/register', input);
  return data;
}

export function persistAuth(res: AuthResponse): void {
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(res));
  setAuthHeader(res.jwt);
}

export function loadAuth(): AuthResponse | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as AuthResponse;
    // optional: expire check
    if (parsed?.expiresAt && Date.now() > Date.parse(parsed.expiresAt)) {
      clearAuth();
      return null;
    }
    setAuthHeader(parsed.jwt);
    return parsed;
  } catch {
    clearAuth();
    return null;
  }
}

export function clearAuth(): void {
  sessionStorage.removeItem(STORAGE_KEY);
  setAuthHeader(undefined);
}