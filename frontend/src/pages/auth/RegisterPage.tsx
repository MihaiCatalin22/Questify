import { useEffect } from "react";
import { useAuth } from "react-oidc-context";

export default function RegisterRedirect() {
  const auth = useAuth();
  useEffect(() => {
    void auth.signinRedirect({ extraQueryParams: { kc_action: "register" } });
  }, []);
  return <div className="p-6">Redirecting to register…</div>;
}