import { useEffect } from "react";
import { useAuth } from "react-oidc-context";
import { LoadingState, PageShell, Panel } from "../../components/ui";

export default function RegisterRedirect() {
  const { signinRedirect } = useAuth();
  useEffect(() => {
    void signinRedirect({ extraQueryParams: { kc_action: "register" } });
  }, [signinRedirect]);
  return (
    <PageShell className="flex min-h-screen items-center justify-center px-4">
      <Panel className="w-full max-w-md p-6">
        <LoadingState label="Redirecting to register..." />
      </Panel>
    </PageShell>
  );
}
