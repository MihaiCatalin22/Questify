import { useEffect } from "react";
import { useAuth } from "react-oidc-context";
import { useNavigate } from "react-router-dom";

export default function OidcCallback() {
  const auth = useAuth();
  const nav = useNavigate();


  useEffect(() => {
    if (auth.activeNavigator || auth.isLoading) return; 
    if (auth.isAuthenticated) nav("/quests", { replace: true });
    else nav("/login", { replace: true });
  }, [auth.activeNavigator, auth.isLoading, auth.isAuthenticated, nav]);

  return <div className="p-6 text-sm opacity-70">Signing you in…</div>;
}
