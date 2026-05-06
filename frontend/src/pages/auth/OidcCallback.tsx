import { useEffect } from "react";
import { useAuth } from "react-oidc-context";
import { useNavigate } from "react-router-dom";
import { LoadingState, PageShell, Panel } from "../../components/ui";

export default function OidcCallback() {
  const auth = useAuth();
  const nav = useNavigate();


  useEffect(() => {
    if (auth.activeNavigator || auth.isLoading) return; 
    if (auth.isAuthenticated) nav("/quests", { replace: true });
    else nav("/login", { replace: true });
  }, [auth.activeNavigator, auth.isLoading, auth.isAuthenticated, nav]);

  return (
    <PageShell className="flex min-h-screen items-center justify-center px-4">
      <Panel className="w-full max-w-md p-6">
        <LoadingState label="Signing you in..." />
      </Panel>
    </PageShell>
  );
}
