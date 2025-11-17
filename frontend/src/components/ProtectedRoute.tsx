import { Outlet } from "react-router-dom";
import { useAuth } from "react-oidc-context";

export default function ProtectedRoute() {
  const auth = useAuth();

  if (auth.activeNavigator || auth.isLoading) {
    return <div className="p-6 text-sm opacity-70">Loading…</div>;
  }

  if (!auth.isAuthenticated) {
    void auth.signinRedirect();
    return null;
  }

  return <Outlet />;
}
