import { useState } from "react";
import { Link, NavLink, Outlet } from "react-router-dom";
import { useAuth } from "react-oidc-context";
import {
  Bot,
  Compass,
  FileCheck2,
  LogIn,
  LogOut,
  Menu,
  Shield,
  Target,
  User,
  X,
} from "lucide-react";

import logo from "../assets/questify_logo_mark_compass.png";
import { cn } from "./cn";
import { IconButton } from "./ui";
import { useAuthContext } from "../contexts/useAuthContext";

const navItems = [
  { to: "/quests", label: "Quests", icon: Target },
  { to: "/quests/discover", label: "Discover", icon: Compass },
  { to: "/submissions", label: "Submissions", icon: FileCheck2 },
  { to: "/coach", label: "AI Coach", icon: Bot },
  { to: "/profile", label: "Profile", icon: User },
];

function SidebarNav({ onNavigate }: { onNavigate?: () => void }) {
  const { hasRole } = useAuthContext();
  const showUsers = hasRole(["ADMIN", "ROLE_ADMIN"]);

  return (
    <nav className="flex flex-1 flex-col gap-1.5">
      {navItems.map((item) => {
        const Icon = item.icon;
        return (
          <NavLink
            key={item.to}
            to={item.to}
            onClick={onNavigate}
            className={({ isActive }) =>
              cn("side-link", isActive && "side-link-active")
            }
          >
            <Icon className="h-4 w-4" />
            <span>{item.label}</span>
          </NavLink>
        );
      })}

      {showUsers ? (
        <NavLink
          to="/users"
          onClick={onNavigate}
          className={({ isActive }) => cn("side-link", isActive && "side-link-active")}
        >
          <Shield className="h-4 w-4" />
          <span>Users</span>
        </NavLink>
      ) : null}
    </nav>
  );
}

export default function AppShell() {
  const auth = useAuth();
  const [logoOk, setLogoOk] = useState(true);
  const [menuOpen, setMenuOpen] = useState(false);

  return (
    <div className="app-shell">
      <aside className="app-sidebar">
        <Link to="/quests" className="brand-lockup">
          {logoOk ? (
            <img
              src={logo}
              alt=""
              className="h-8 w-8"
              width={32}
              height={32}
              onError={() => setLogoOk(false)}
            />
          ) : (
            <span className="brand-fallback">Q</span>
          )}
          <span>Questify</span>
        </Link>

        <SidebarNav />

        <div className="sidebar-note">
          <div className="text-sm font-semibold text-[rgb(var(--text))]">Stay focused</div>
          <p>Track quests, upload proof, and use the coach when you need a next step.</p>
        </div>
      </aside>

      {menuOpen ? (
        <div className="mobile-menu">
          <div className="mobile-menu-backdrop" onClick={() => setMenuOpen(false)} />
          <div className="mobile-menu-panel">
            <div className="flex items-center justify-between">
              <Link to="/quests" className="brand-lockup" onClick={() => setMenuOpen(false)}>
                {logoOk ? <img src={logo} alt="" className="h-8 w-8" /> : <span className="brand-fallback">Q</span>}
                <span>Questify</span>
              </Link>
              <IconButton label="Close navigation" onClick={() => setMenuOpen(false)}>
                <X className="h-4 w-4" />
              </IconButton>
            </div>
            <div className="mt-6 flex min-h-0 flex-1 flex-col">
              <SidebarNav onNavigate={() => setMenuOpen(false)} />
            </div>
          </div>
        </div>
      ) : null}

      <div className="app-main">
        <header className="app-topbar">
          <IconButton label="Open navigation" className="lg:hidden" onClick={() => setMenuOpen(true)}>
            <Menu className="h-4 w-4" />
          </IconButton>

          <div className="topbar-title">
            <span>Questify</span>
          </div>

          <div className="ml-auto flex items-center gap-2">
            <Link to="/profile" className="icon-btn" aria-label="Profile" title="Profile">
              <User className="h-4 w-4" />
            </Link>
            {auth.isAuthenticated ? (
              <IconButton label="Log out" onClick={() => auth.signoutRedirect()}>
                <LogOut className="h-4 w-4" />
              </IconButton>
            ) : (
              <Link to="/login" className="icon-btn" aria-label="Log in" title="Log in">
                <LogIn className="h-4 w-4" />
              </Link>
            )}
          </div>
        </header>

        <main className="app-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
