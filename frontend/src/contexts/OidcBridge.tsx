import { useEffect, type ReactNode } from "react";
import { useAuth } from "react-oidc-context";
import { setAccessTokenGetter } from "../api/token";

declare global {
  interface Window {
    __questifyLoggingOut?: boolean;
  }
}

const FORCE_LOGOUT_EVENT = "questify:force-logout";
const LOGOUT_REASON_KEY = "questify:logout-reason";

type ForceLogoutDetail = {
  reason?: "deleted" | "unauthorized";
};

function readForceLogoutReason(event: Event): "deleted" | "unauthorized" {
  const detail = event instanceof CustomEvent ? (event.detail as ForceLogoutDetail | undefined) : undefined;
  return detail?.reason === "deleted" ? "deleted" : "unauthorized";
}

export default function OidcBridge({ children }: { children: ReactNode }) {
  const auth = useAuth();
  setAccessTokenGetter(() => auth.user?.access_token ?? null);

  useEffect(() => {
    if (auth.isAuthenticated) {
      sessionStorage.removeItem(LOGOUT_REASON_KEY);
      window.__questifyLoggingOut = false;
    }
  }, [auth.isAuthenticated]);

  useEffect(() => {
    const handler = async (event: Event) => {
      if (window.__questifyLoggingOut) return;
      window.__questifyLoggingOut = true;

      const reason = readForceLogoutReason(event);
      sessionStorage.setItem(LOGOUT_REASON_KEY, reason);

      try {
        await auth.signoutRedirect();
      } catch {
        try {
          await auth.removeUser();
        } finally {
          window.location.href = "/";
        }
      }
    };

    window.addEventListener(FORCE_LOGOUT_EVENT, handler);
    return () => window.removeEventListener(FORCE_LOGOUT_EVENT, handler);
  }, [auth]);

  return <>{children}</>;
}
