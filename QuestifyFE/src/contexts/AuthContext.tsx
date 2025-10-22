import React, { createContext, useContext, useMemo, useState } from 'react';
import type {
  LoginRequest,
  RegisterRequest,
  AuthResponse,
  UserDTO,
} from '../api/auth';
import * as AuthApi from '../api/auth';

type AuthState = {
  user: UserDTO | null;
  jwt: string | null;
  expiresAt?: string | null;
};

interface AuthContextValue {
  user: UserDTO | null;
  jwt: string | null;
  login: (req: LoginRequest) => Promise<void>;
  register: (req: RegisterRequest) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const boot = AuthApi.loadAuth();
  const [state, setState] = useState<AuthState>({
    user: boot?.user ?? null,
    jwt: boot?.jwt ?? null,
    expiresAt: boot?.expiresAt ?? null,
  });


  const persist = (res: AuthResponse) => {
    AuthApi.persistAuth(res);
    setState({ user: res.user, jwt: res.jwt, expiresAt: res.expiresAt ?? null });
  };

  const login = async (req: LoginRequest) => {
    const res = await AuthApi.login(req);
    persist(res);
  };

  const register = async (req: RegisterRequest) => {
    const res = await AuthApi.register(req);
    persist(res);
  };

  const logout = () => {
    AuthApi.clearAuth();
    setState({ user: null, jwt: null, expiresAt: null });
  };

  const value = useMemo(
    () => ({ user: state.user, jwt: state.jwt, login, register, logout }),
    [state.user, state.jwt]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuthContext = () => {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuthContext must be used within AuthProvider');
  return ctx;
};