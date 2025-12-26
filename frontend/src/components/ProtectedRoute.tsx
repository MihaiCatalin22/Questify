import { useEffect, useState } from "react";
import { Outlet } from "react-router-dom";
import { useAuth } from "react-oidc-context";

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
    return <div className="p-6 text-sm opacity-70">Loading…</div>;
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
        <div className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#0f1115] p-5 space-y-3">
          <div className="text-lg font-semibold">{title}</div>
          <div className="text-sm opacity-80">{desc}</div>

          <div className="flex flex-wrap gap-2 pt-2">
            <button
              onClick={() => {
                sessionStorage.removeItem(LOGOUT_REASON_KEY);
                auth.signinRedirect();
              }}
              className="rounded-2xl border px-4 py-2 text-sm shadow hover:shadow-md
                         bg-white dark:bg-[#0f1115] border-slate-200 dark:border-slate-800"
            >
              Sign in
            </button>

            <button
              onClick={async () => {
                sessionStorage.removeItem(LOGOUT_REASON_KEY);
                try {
                  await auth.removeUser();
                } finally {
                  window.location.href = "/";
                }
              }}
              className="rounded-2xl border px-4 py-2 text-sm shadow hover:shadow-md
                         border-slate-200 dark:border-slate-800 opacity-80"
            >
              Go home
            </button>
          </div>
        </div>
      </div>
    );
  }

  return <Outlet />;
}
