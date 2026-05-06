import { useEffect } from "react";
import { useAuth } from "react-oidc-context";
import { LoadingState, PageShell, Panel } from "../../components/ui";

export default function LoginRedirect() {
  const { signinRedirect } = useAuth();
  useEffect(() => { void signinRedirect(); }, [signinRedirect]);
  return (
    <PageShell className="flex min-h-screen items-center justify-center px-4">
      <Panel className="w-full max-w-md p-6">
        <LoadingState label="Redirecting to sign in..." />
      </Panel>
    </PageShell>
  );
}
