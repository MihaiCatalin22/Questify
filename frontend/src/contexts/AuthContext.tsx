import React, {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { useAuth } from "react-oidc-context";
import http from "../api/https";

export type UserDTO = {
  id?: string | number;
  username?: string;
  email?: string;
  displayName?: string;
  roles?: string[];
};

interface AuthContextValue {
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

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

function claimsToUser(profile: Record<string, any> | undefined): UserDTO | null {
  if (!profile) return null;
  const roles: string[] =
    profile?.roles ??
    profile?.realm_access?.roles ??
    profile?.resource_access?.account?.roles ??
    [];
  return {
    id: profile.sub,
    username: profile.preferred_username ?? profile.username ?? profile.email,
    email: profile.email,
    displayName: profile.name ?? profile.given_name ?? profile.preferred_username,
    roles,
  };
}

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const auth = useAuth();

  const [notifications, setNotifications] = useState<string[]>([]);

  useEffect(() => {
  }, [auth.user, auth.isAuthenticated]);

  const login = async (opts?: { register?: boolean }) => {
    const extraQueryParams = opts?.register ? { kc_action: "register" } : undefined;
    await auth.signinRedirect({ extraQueryParams });
  };

  const register = async () => login({ register: true });

  const logout = async () => {
    try {
      await auth.signoutRedirect();
    } catch {
      await auth.removeUser();
    }
  };

  const setUser = (u: UserDTO | null) => {

    console.debug("setUser called, ignoring (OIDC is source of truth).", u);
  };

  const updateUserDetails = async (u: UserDTO & { token?: string }) => {
    if (!u?.id) return;
    const { data } = await http.put<UserDTO>(`/users/${u.id}`, u);
    console.debug("Updated user details:", data);
  };

  const me = useMemo(() => claimsToUser(auth.user?.profile), [auth.user]);

  const hasRole = (roles: string | string[]) => {
    const required = Array.isArray(roles) ? roles : [roles];
    const userRoles = me?.roles ?? [];
    return required.some((r) => userRoles.includes(r));
  };

  const getAuthToken = () => auth.user?.access_token ?? null;

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
    [auth.isAuthenticated, auth.isLoading, auth.user, me, notifications]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuthContext = () => {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuthContext must be used within AuthProvider");
  return ctx;
};
