import { Link, Outlet, Route, Routes, Navigate } from 'react-router-dom';
import ProtectedRoute from './components/ProtectedRoute';

import Login from './pages/auth/LoginPage';
import RegisterPage from './pages/auth/RegisterPage';

import UsersList from './pages/users/UsersList';
import UsersForm from './pages/users/UsersForm';

import QuestsList from './pages/quests/QuestsList';
import QuestForm from './pages/quests/QuestForm';
import QuestDetail from './pages/quests/QuestDetail';

import SubmissionsList from './pages/submissions/SubmissionsList';
import SubmissionDetail from './pages/submissions/SubmissionDetail';

function Shell() {
  return (
    <div className="min-h-screen">
      <header className="px-6 py-3 border-b flex items-center gap-4">
        <Link to="/quests" className="font-semibold">Questify</Link>
        <nav className="flex gap-3 text-sm">
          <Link to="/users" className="underline">Users</Link>
          <Link to="/quests" className="underline">Quests</Link>
          <Link to="/submissions" className="underline">Submissions</Link>
        </nav>
      </header>
      <main>
        <Outlet />
      </main>
    </div>
  );
}

export default function App() {
  return (
    <Routes>
      {/* Public routes */}
      <Route path="/" element={<Navigate to="/login" replace />} />
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

          {/* Default inside shell (optional) */}
          <Route index element={<Navigate to="/quests" replace />} />
        </Route>
      </Route>

      {/* Fallback */}
      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  );
}
