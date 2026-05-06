import React, {
  useCallback,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { useAuth } from "react-oidc-context";
import http from "../api/https";
import { AuthContext, type AuthContextValue, type UserDTO } from "./authContextCore";

function asRecord(value: unknown): Record<string, unknown> | null {
  return typeof value === "object" && value !== null ? (value as Record<string, unknown>) : null;
}

function readString(value: unknown): string | undefined {
  return typeof value === "string" ? value : undefined;
}

function readRoles(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string") : [];
}

function claimsToUser(profile: Record<string, unknown> | undefined): UserDTO | null {
  if (!profile) return null;
  const realmAccess = asRecord(profile.realm_access);
  const resourceAccess = asRecord(profile.resource_access);
  const accountAccess = asRecord(resourceAccess?.account);
  const roles =
    readRoles(profile.roles).length > 0
      ? readRoles(profile.roles)
      : readRoles(realmAccess?.roles).length > 0
        ? readRoles(realmAccess?.roles)
        : readRoles(accountAccess?.roles);

  return {
    id: readString(profile.sub),
    username: readString(profile.preferred_username) ?? readString(profile.username) ?? readString(profile.email),
    email: readString(profile.email),
    displayName: readString(profile.name) ?? readString(profile.given_name) ?? readString(profile.preferred_username),
    roles,
  };
}

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const auth = useAuth();

  const [notifications, setNotifications] = useState<string[]>([]);

  const login = useCallback(async (opts?: { register?: boolean }) => {
    const extraQueryParams = opts?.register ? { kc_action: "register" } : undefined;
    await auth.signinRedirect({ extraQueryParams });
  }, [auth]);

  const register = useCallback(async () => login({ register: true }), [login]);

  const logout = useCallback(async () => {
    try {
      await auth.signoutRedirect();
    } catch {
      await auth.removeUser();
    }
  }, [auth]);

  const setUser = useCallback((u: UserDTO | null) => {
    console.debug("setUser called, ignoring (OIDC is source of truth).", u);
  }, []);

  const updateUserDetails = useCallback(async (u: UserDTO & { token?: string }) => {
    if (!u?.id) return;
    const { data } = await http.put<UserDTO>(`/users/${u.id}`, u);
    console.debug("Updated user details:", data);
  }, []);

  const me = useMemo(
    () => claimsToUser(auth.user?.profile as Record<string, unknown> | undefined),
    [auth.user]
  );

  const hasRole = useCallback((roles: string | string[]) => {
    const required = Array.isArray(roles) ? roles : [roles];
    const userRoles = me?.roles ?? [];
    return required.some((r) => userRoles.includes(r));
  }, [me?.roles]);

  const getAuthToken = useCallback(() => auth.user?.access_token ?? null, [auth.user?.access_token]);

  const value: AuthContextValue = useMemo(
    () => ({
      isAuthenticated: !!auth.isAuthenticated,
      user: me,
      loading: !!auth.isLoading,
      notifications,
      setNotifications,
      hasRole,
      getAuthToken,

      jwt: auth.user?.access_token ?? null,
      login,
      register,
      logout,

      setUser,
      updateUserDetails,
    }),
    [
      auth.isAuthenticated,
      auth.isLoading,
      auth.user?.access_token,
      getAuthToken,
      hasRole,
      login,
      logout,
      me,
      notifications,
      register,
      setUser,
      updateUserDetails,
    ]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export type { AuthContextValue, UserDTO };
