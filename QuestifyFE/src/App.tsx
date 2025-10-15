import { Link, Outlet, Route, Routes } from 'react-router-dom';
import ProtectedRoute from './components/ProtectedRoute';
import UsersList from './pages/users/UsersList';
import UsersForm from './pages/users/UsersForm';
import Login from './pages/auth/Login';

function Shell() {
  return (
    <div className="min-h-screen">
      <header className="px-6 py-3 border-b flex items-center gap-4">
        <Link to="/" className="font-semibold">Questify</Link>
        <nav className="flex gap-3 text-sm">
          <Link to="/users" className="underline">Users</Link>
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
      <Route element={<Shell />}>
        <Route path="/login" element={<Login />} />
        <Route element={<ProtectedRoute />}>
          <Route path="/users" element={<UsersList />} />
          <Route path="/users/:id" element={<UsersForm />} />
          <Route path="/users/new" element={<UsersForm />} />
          <Route index element={<div className="p-6">Welcome to Questify</div>} />
        </Route>
      </Route>
    </Routes>
  );
}
