import { useState } from 'react';
import { useAuthContext } from '../../contexts/AuthContext';
import { useNavigate, Link } from 'react-router-dom';

export default function LoginPage() {
  const { login } = useAuthContext();
  const nav = useNavigate();

  const [form, setForm] = useState({ usernameOrEmail: '', password: '' });
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErr(null);
    setLoading(true);
    try {
      await login(form);
      nav('/quests', { replace: true });
    } catch (e: any) {
      const msg =
        e?.response?.data?.message ??
        e?.message ??
        'Invalid credentials';
      setErr(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-50 p-4">
      <form
        onSubmit={onSubmit}
        className="w-full max-w-sm bg-white p-6 rounded-2xl shadow"
      >
        <h1 className="text-2xl font-semibold mb-1">Sign in</h1>
        <p className="text-sm text-slate-500 mb-6">
          Use your username or email to sign in.
        </p>

        <label className="block text-sm mb-1">Username or email</label>
        <input
          className="w-full border rounded-xl px-3 py-2 mb-3 outline-none focus:ring focus:ring-slate-200"
          value={form.usernameOrEmail}
          onChange={(e) => setForm({ ...form, usernameOrEmail: e.target.value })}
          autoComplete="username"
          required
        />

        <label className="block text-sm mb-1">Password</label>
        <input
          type="password"
          className="w-full border rounded-xl px-3 py-2 mb-3 outline-none focus:ring focus:ring-slate-200"
          value={form.password}
          onChange={(e) => setForm({ ...form, password: e.target.value })}
          autoComplete="current-password"
          required
        />

        {err && <p className="text-red-600 text-sm mb-3">{err}</p>}

        <button
          type="submit"
          disabled={loading}
          className="w-full rounded-xl px-3 py-2 bg-black text-white disabled:opacity-60"
        >
          {loading ? 'Signing in…' : 'Sign in'}
        </button>

        <div className="text-sm mt-4 text-center">
          No account?{' '}
          <Link to="/register" className="text-blue-600 hover:underline">
            Create one
          </Link>
        </div>
      </form>
    </div>
  );
}
