import { useEffect } from "react";
import ReactDOM from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter } from "react-router-dom";
import App from "./App";
import "./tw.css";

import OidcProvider from "./contexts/oidc";
import { useAuth } from "react-oidc-context";
import { setAccessTokenGetter } from "./api/token";
import { AuthProvider } from "./contexts/AuthContext";

import icon192 from "./assets/questify_192.png";
import apple180 from "./assets/questify_192.png";
import faviconIco from "./assets/favicon.ico";

const FORCE_LOGOUT_EVENT = "questify:force-logout";
const LOGOUT_REASON_KEY = "questify:logout-reason";

function applyInitialTheme() {
  const stored = localStorage.getItem("theme");
  const prefersDark = window.matchMedia("(prefers-color-scheme: dark)").matches;
  const theme = stored ?? (prefersDark ? "dark" : "light");
  const root = document.documentElement;
  if (theme === "dark") root.classList.add("dark");
  else root.classList.remove("dark");
}
applyInitialTheme();

function setFavicons() {
  const head = document.head;
  head.querySelectorAll<HTMLLinkElement>('link[rel="icon"]').forEach((l) => l.remove());
  if (faviconIco) {
    const ico = document.createElement("link");
    ico.rel = "icon";
    ico.href = faviconIco;
    head.appendChild(ico);
  }
  const links: Array<{ rel: string; sizes?: string; type?: string; href: string }> = [
    { rel: "icon", type: "image/png", sizes: "192x192", href: icon192 },
    { rel: "apple-touch-icon", sizes: "180x180", href: apple180 },
  ];
  for (const cfg of links) {
    const l = document.createElement("link");
    l.rel = cfg.rel;
    if (cfg.type) l.type = cfg.type;
    if (cfg.sizes) l.sizes = cfg.sizes;
    l.href = cfg.href;
    head.appendChild(l);
  }
}
setFavicons();

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false } },
});

function OidcBridge({ children }: { children: React.ReactNode }) {
  const auth = useAuth();
  setAccessTokenGetter(() => auth.user?.access_token ?? null);

  useEffect(() => {
    if (auth.isAuthenticated) {
      sessionStorage.removeItem(LOGOUT_REASON_KEY);
      (window as any).__questifyLoggingOut = false;
    }
  }, [auth.isAuthenticated]);

  useEffect(() => {
    const handler = async (e: any) => {
      if ((window as any).__questifyLoggingOut) return;
      (window as any).__questifyLoggingOut = true;

      const reason = e?.detail?.reason === "deleted" ? "deleted" : "unauthorized";
      sessionStorage.setItem(LOGOUT_REASON_KEY, reason);

      try {
        await auth.signoutRedirect();
      } catch {
        try {
          await auth.removeUser();
        } finally {
          window.location.href = "/";
        }
      }
    };

    window.addEventListener(FORCE_LOGOUT_EVENT, handler as EventListener);
    return () => window.removeEventListener(FORCE_LOGOUT_EVENT, handler as EventListener);
  }, [auth]);

  return <>{children}</>;
}

ReactDOM.createRoot(document.getElementById("root")!).render(
  <QueryClientProvider client={queryClient}>
    <OidcProvider>
      <OidcBridge>
        <AuthProvider>
          <BrowserRouter>
            <App />
          </BrowserRouter>
        </AuthProvider>
      </OidcBridge>
    </OidcProvider>
  </QueryClientProvider>
);
