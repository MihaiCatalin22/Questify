import { Link, Outlet, Route, Routes, Navigate } from "react-router-dom";
import { Toaster } from "react-hot-toast";
import ProtectedRoute from "./components/ProtectedRoute";
import { useState } from "react";
import { useAuth } from "react-oidc-context";

import HomePage from "./pages/HomePage";
import PrivacyPolicyPage from "./pages/PrivacyPolicyPage";
import Login from "./pages/auth/LoginPage";
import RegisterPage from "./pages/auth/RegisterPage";
import OidcCallback from "./pages/auth/OidcCallback";

import UsersList from "./pages/users/UsersList";
import UserDetail from "./pages/users/UserDetail";

import ProfilePage from "./pages/profile/ProfilePage";

import QuestsList from "./pages/quests/QuestsList";
import QuestForm from "./pages/quests/QuestForm";
import QuestDetail from "./pages/quests/QuestDetail";
import DiscoverQuests from "./pages/quests/DiscoverQuests";

import SubmissionsList from "./pages/submissions/SubmissionsList";
import SubmissionDetail from "./pages/submissions/SubmissionDetail";

import logo from "./assets/questify_logo_mark_compass.png";
import { ThemeToggle } from "./components/ThemeToggle";

function Shell() {
  const auth = useAuth();
  const [logoOk, setLogoOk] = useState(true);

  const displayName =
    (auth.user?.profile as any)?.name ||
    (auth.user?.profile as any)?.preferred_username ||
    (auth.user?.profile as any)?.email ||
    "Account";

  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-40 border-b border-slate-200 bg-white/80 backdrop-blur dark:bg-[#0f1115]/80 dark:border-slate-800">
        <div className="px-6 py-3 flex items-center gap-4 justify-between">
          <div className="flex items-center gap-4">
            <Link to="/" className="inline-flex items-center gap-2">
              {logoOk ? (
                <img
                  src={logo}
                  alt="Questify"
                  className="h-6 w-6"
                  width={24}
                  height={24}
                  onError={() => setLogoOk(false)}
                />
              ) : (
                <span className="text-sm font-semibold">Questify</span>
              )}
              <span className="sr-only">Questify</span>
            </Link>

            <nav className="flex gap-4 text-sm">
              <Link className="hover:text-indigo-600 dark:hover:text-indigo-400" to="/quests">
                Quests
              </Link>
              <Link className="hover:text-indigo-600 dark:hover:text-indigo-400" to="/quests/discover">
                Discover
              </Link>
              <Link className="hover:text-indigo-600 dark:hover:text-indigo-400" to="/submissions">
                Submissions
              </Link>
              <Link className="hover:text-indigo-600 dark:hover:text-indigo-400" to="/users">
                Users
              </Link>
              <Link className="hover:text-indigo-600 dark:hover:text-indigo-400" to="/profile">
                Profile
              </Link>
            </nav>
          </div>

          <div className="flex items-center gap-3">
            {auth.isAuthenticated && (
              <>
                <Link to="/profile" className="text-sm opacity-80 hover:opacity-100">
                  {displayName}
                </Link>
                <button
                  onClick={() => auth.signoutRedirect()}
                  className="px-3 py-1.5 rounded-lg border shadow text-sm hover:bg-gray-100 dark:hover:bg-[#161b26]"
                >
                  Log out
                </button>
              </>
            )}
            <ThemeToggle />
          </div>
        </div>
      </header>

      <main className="p-6 dark:bg-[#0b0d12] min-h-[calc(100vh-56px)]">
        <Outlet />
      </main>
    </div>
  );
}

export default function App() {
  return (
    <>
      <Toaster position="top-right" />

      <Routes>
        <Route index element={<HomePage />} />

        <Route path="/privacy" element={<PrivacyPolicyPage />} />

        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/oidc/callback" element={<OidcCallback />} />

        <Route element={<ProtectedRoute />}>
          <Route element={<Shell />}>
            <Route path="/profile" element={<ProfilePage />} />

            <Route path="/users" element={<UsersList />} />
            <Route path="/users/:id" element={<UserDetail />} />

            <Route path="/quests" element={<QuestsList />} />
            <Route path="/quests/new" element={<QuestForm />} />
            <Route path="/quests/:id" element={<QuestDetail />} />
            <Route path="/quests/:id/edit" element={<QuestForm />} />
            <Route path="/quests/discover" element={<DiscoverQuests />} />

            <Route path="/submissions" element={<SubmissionsList />} />
            <Route path="/submissions/:id" element={<SubmissionDetail />} />

            <Route path="app" element={<Navigate to="/quests" replace />} />
          </Route>
        </Route>

        <Route path="app" element={<Navigate to="/quests" replace />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </>
  );
}
