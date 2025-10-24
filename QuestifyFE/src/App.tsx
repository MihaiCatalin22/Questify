import { Link, Outlet, Route, Routes, Navigate } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import ProtectedRoute from './components/ProtectedRoute';
import { useAuthContext } from './contexts/AuthContext';
import { useState } from 'react';

import HomePage from './pages/HomePage';

import Login from './pages/auth/LoginPage';
import RegisterPage from './pages/auth/RegisterPage';

import UsersList from './pages/users/UsersList';
import UsersForm from './pages/users/UsersForm';

import QuestsList from './pages/quests/QuestsList';
import QuestForm from './pages/quests/QuestForm';
import QuestDetail from './pages/quests/QuestDetail';

import SubmissionsList from './pages/submissions/SubmissionsList';
import SubmissionDetail from './pages/submissions/SubmissionDetail';

import logo from './assets/questify_logo_mark_compass.png';
import { ThemeToggle } from './components/ThemeToggle';

function Shell() {
  const { user, logout } = useAuthContext();
  const [logoOk, setLogoOk] = useState(true);

  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-40 border-b border-slate-200 bg-white/80 backdrop-blur dark:bg-[#0f1115]/80 dark:border-slate-800">
  <div className="px-6 py-3 flex items-center gap-4 justify-between">
    {/* Left: brand + nav */}
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
        <Link className="hover:text-indigo-600 dark:hover:text-indigo-400" to="/quests">Quests</Link>
        <Link className="hover:text-indigo-600 dark:hover:text-indigo-400" to="/users">Users</Link>
        <Link className="hover:text-indigo-600 dark:hover:text-indigo-400" to="/submissions">Submissions</Link>
        {/* your Dev link can stay here if you have it */}
      </nav>
    </div>

    {/* Right: user + toggle (toggle LAST = hard right) */}
    <div className="flex items-center gap-3">
      {user && (
        <>
          <span className="text-sm opacity-80">
            {user.displayName ?? user.username}
          </span>
          <button
            onClick={logout}
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
      {/* Global toaster for API/error messages */}
      <Toaster position="top-right" />

      <Routes>
        {/* Public landing page */}
        <Route index element={<HomePage />} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<RegisterPage />} />

        {/* Protected app routes */}
        <Route element={<ProtectedRoute />}>
          <Route element={<Shell />}>
            {/* Users */}
            <Route path="/users" element={<UsersList />} />
            <Route path="/users/new" element={<UsersForm />} />
            <Route path="/users/:id" element={<UsersForm />} />

            {/* Quests */}
            <Route path="/quests" element={<QuestsList />} />
            <Route path="/quests/new" element={<QuestForm />} />
            <Route path="/quests/:id" element={<QuestDetail />} />
            <Route path="/quests/:id/edit" element={<QuestForm />} />

            {/* Submissions */}
            <Route path="/submissions" element={<SubmissionsList />} />
            <Route path="/submissions/:id" element={<SubmissionDetail />} />

            {/* Default inside shell */}
            <Route path="app" element={<Navigate to="/quests" replace />} />
          </Route>
        </Route>

        {/* Fallbacks */}
        <Route path="app" element={<Navigate to="/quests" replace />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </>
  );
}
