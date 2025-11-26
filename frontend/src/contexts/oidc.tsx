// src/auth/OidcProvider.tsx
import { AuthProvider, type AuthProviderProps } from "react-oidc-context";

const trim = (s?: string | null) => (s ?? "").trim();
const has = (s?: string | null) => !!trim(s);

const origin = window.location.origin.replace(/\/+$/, "");
const envIssuer = trim(import.meta.env.VITE_OIDC_ISSUER as string | undefined);

// Default to current origin; only use env if it's non-empty.
const authority = has(envIssuer)
  ? envIssuer!.replace(/\/+$/, "")
  : `${origin}/auth/realms/questify`;

const clientId =
  trim(import.meta.env.VITE_OIDC_CLIENT_ID as string | undefined) ||
  "questify-frontend";

const redirectUri =
  trim(import.meta.env.VITE_OIDC_REDIRECT_URI as string | undefined) ||
  `${origin}/oidc/callback`;

const cfg: AuthProviderProps = {
  authority,
  client_id: clientId,
  redirect_uri: redirectUri,
  post_logout_redirect_uri: `${origin}/`,
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
