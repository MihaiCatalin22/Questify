import { useEffect } from "react";
import { useAuth } from "react-oidc-context";

export default function LoginRedirect() {
  const auth = useAuth();
  useEffect(() => { void auth.signinRedirect(); }, []);
  return <div className="p-6">Redirecting to sign in…</div>;
}