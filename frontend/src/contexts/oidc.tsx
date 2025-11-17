import { AuthProvider, type AuthProviderProps } from "react-oidc-context";

const issuer =
  import.meta.env.VITE_OIDC_ISSUER ?? "https://localhost:5443/auth/realms/questify";
const clientId = import.meta.env.VITE_OIDC_CLIENT_ID ?? "questify-frontend";
const redirectUri = `${window.location.origin}/oidc/callback`;
const postLogoutRedirectUri = `${window.location.origin}/`;

const cfg: AuthProviderProps = {
  authority: issuer,
  client_id: clientId,
  redirect_uri: redirectUri,
  post_logout_redirect_uri: postLogoutRedirectUri,
  response_type: "code",
  scope: "openid profile email",
  automaticSilentRenew: true,
  loadUserInfo: true,
  monitorSession: false,
  onSigninCallback: () => {
    window.history.replaceState({}, document.title, window.location.pathname);
  },
};

export default function OidcProvider({ children }: { children: React.ReactNode }) {
  return <AuthProvider {...cfg}>{children}</AuthProvider>;
}