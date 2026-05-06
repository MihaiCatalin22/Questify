import { createContext } from "react";

export type UserDTO = {
  id?: string | number;
  username?: string;
  email?: string;
  displayName?: string;
  roles?: string[];
};

export interface AuthContextValue {
  isAuthenticated: boolean;
  user: UserDTO | null;
  loading: boolean;
  notifications: string[];
  setNotifications: (items: string[]) => void;
  hasRole: (roles: string | string[]) => boolean;
  getAuthToken: () => string | null;

  jwt: string | null;
  login: (opts?: { register?: boolean }) => Promise<void>;
  register: () => Promise<void>;
  logout: () => Promise<void>;

  setUser: (u: UserDTO | null) => void;
  updateUserDetails: (u: UserDTO & { token?: string }) => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined);
