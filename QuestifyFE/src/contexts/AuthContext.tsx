import React, { createContext, useContext, useEffect, useMemo, useState } from "react";
import axios from "axios";
import { jwtDecode } from "jwt-decode";

import * as AuthApi from "../api/auth";
import type { LoginRequest, RegisterRequest, AuthResponse, UserDTO } from "../api/auth";
import http, { AuthStorage, setAuthHeader, type JwtClaims } from "../api/https";

interface AuthContextValue {
  isAuthenticated: boolean;
  user: UserDTO | null;
  loading: boolean;
  notifications: string[];
  setNotifications: (items: string[]) => void;
  hasRole: (roles: string | string[]) => boolean;
  getAuthToken: () => string | null;


  jwt: string | null;
  login: (req: LoginRequest) => Promise<void>;
  register: (req: RegisterRequest) => Promise<void>;
  logout: () => void;


  setUser: (u: UserDTO | null) => void;
  updateUserDetails: (u: UserDTO & { token?: string }) => Promise<void>;
}

type AuthState = {
  user: UserDTO | null;
  jwt: string | null;
  expiresAt?: string | number | null;
};

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

function isUserDTO(u: any): u is UserDTO {
  return (
    u &&
    (typeof u.id === "string" || typeof u.id === "number") &&
    typeof u.username === "string" &&
    typeof u.email === "string" &&
    typeof u.createdAt !== "undefined" &&
    typeof u.updatedAt !== "undefined"
  );
}

function putTokenEverywhere(jwt?: string | null) {
  if (jwt) (axios.defaults.headers as any).common["Authorization"] = `Bearer ${jwt}`;
  else delete (axios.defaults.headers as any).common["Authorization"];
  setAuthHeader(jwt ?? undefined);

  if (jwt) {
    try {
      const claims = jwtDecode<JwtClaims>(jwt);
      sessionStorage.setItem("claims", JSON.stringify(claims));
    } catch {
      sessionStorage.removeItem("claims");
    }
  } else {
    sessionStorage.removeItem("claims");
  }
}

function setCompatStorage(res: AuthResponse) {
  sessionStorage.setItem("accessToken", res.jwt);
  sessionStorage.setItem("user", JSON.stringify(res)); 
  AuthStorage.persistAuth({ jwt: res.jwt, user: res.user, expiresAt: res.expiresAt ?? null });
  putTokenEverywhere(res.jwt);
}

function clearCompatStorage() {
  sessionStorage.removeItem("accessToken");
  sessionStorage.removeItem("user");
  sessionStorage.removeItem("claims");
  AuthStorage.clearAuth();
  putTokenEverywhere(null);
}

function bootFromStorage(): AuthState {
  const boot = AuthStorage.loadAuth(); 
  let jwt: string | null = boot?.jwt ?? sessionStorage.getItem("accessToken");
  let expiresAt: string | number | null =
    (typeof boot?.expiresAt === "string" || typeof boot?.expiresAt === "number") ? boot!.expiresAt! : null;

  if (isUserDTO(boot?.user)) {
    return { user: boot!.user!, jwt, expiresAt };
  }

  try {
    const raw = sessionStorage.getItem("user");
    if (raw) {
      const parsed = JSON.parse(raw);
      const candidate = parsed?.user ?? parsed; 
      if (isUserDTO(candidate)) {
        return { user: candidate, jwt, expiresAt };
      }
    }
  } catch {
    // ignore
  }

  return { user: null, jwt: jwt ?? null, expiresAt };
}


export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const initial = bootFromStorage();
  const [state, setState] = useState<AuthState>(initial);
  const [loading, setLoading] = useState(true);
  const [notifications, setNotifications] = useState<string[]>([]);

  useEffect(() => {
    putTokenEverywhere(state.jwt);
    setLoading(false);
  }, []);

  const persist = (res: AuthResponse) => {
    setCompatStorage(res);
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
    clearCompatStorage();
    setState({ user: null, jwt: null, expiresAt: null });
  };

  const hasRole = (roles: string | string[]) => {
    const required = Array.isArray(roles) ? roles : [roles];
    const userRoles = state.user?.roles ?? [];
    return required.some((r) => userRoles.includes(r as any));
  };

  const getAuthToken = () =>
    sessionStorage.getItem("accessToken") || (state.jwt ? String(state.jwt) : null);

  const updateUserDetails = async (u: UserDTO & { token?: string }) => {
    const { data } = await http.put<UserDTO>(`/users/${u.id}`, u);
    const next: AuthResponse = {
      user: { ...state.user, ...data } as UserDTO,
      jwt: u.token ?? state.jwt ?? "",
      expiresAt: state.expiresAt ?? null,
    };
    persist(next);
  };

  const setUser = (u: UserDTO | null) => {
    if (!u) return logout();
    const next: AuthResponse = {
      user: u,
      jwt: state.jwt ?? sessionStorage.getItem("accessToken") ?? "",
      expiresAt: state.expiresAt ?? null,
    };
    persist(next);
  };

  const value: AuthContextValue = useMemo(
    () => ({
      isAuthenticated: !!state.jwt,
      user: state.user,
      loading,
      notifications,
      setNotifications,
      hasRole,
      getAuthToken,

      jwt: state.jwt,
      login,
      register,
      logout,

      setUser,
      updateUserDetails,
    }),
    [state.jwt, state.user, loading, notifications]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuthContext = () => {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuthContext must be used within AuthProvider");
  return ctx;
};
