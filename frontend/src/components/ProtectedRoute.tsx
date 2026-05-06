import { useEffect, useState } from "react";
import { Outlet } from "react-router-dom";
import { useAuth } from "react-oidc-context";
import { Button, LoadingState, Panel } from "./ui";

const LOGOUT_REASON_KEY = "questify:logout-reason";

export default function ProtectedRoute() {
  const auth = useAuth();
  const [redirecting, setRedirecting] = useState(false);

  const reason = sessionStorage.getItem(LOGOUT_REASON_KEY); 

  useEffect(() => {
    if (auth.activeNavigator || auth.isLoading) return;
    if (auth.isAuthenticated) return;

    if (reason) return;

    setRedirecting(true);
    auth.signinRedirect().catch(() => setRedirecting(false));
  }, [auth, reason]);

  if (auth.activeNavigator || auth.isLoading || redirecting) {
    return <div className="p-6"><LoadingState label="Loading..." /></div>;
  }

  if (!auth.isAuthenticated) {
    const title =
      reason === "deleted" ? "Account deleted" : "Signed out";

    const desc =
      reason === "deleted"
        ? "This account was deleted/disabled. You can’t log in again with it. Create a new account if you want to use Questify."
        : "Your session expired or you were signed out. Please sign in again.";

    return (
      <div className="p-6 max-w-xl">
        <Panel className="p-5 space-y-3">
          <div className="text-lg font-semibold">{title}</div>
          <div className="text-sm opacity-80">{desc}</div>

          <div className="flex flex-wrap gap-2 pt-2">
            <Button
              onClick={() => {
                sessionStorage.removeItem(LOGOUT_REASON_KEY);
                auth.signinRedirect();
              }}
              variant="primary"
            >
              Sign in
            </Button>

            <Button
              onClick={async () => {
                sessionStorage.removeItem(LOGOUT_REASON_KEY);
                try {
                  await auth.removeUser();
                } finally {
                  window.location.href = "/";
                }
              }}
            >
              Go home
            </Button>
          </div>
        </Panel>
      </div>
    );
  }

  return <Outlet />;
}
