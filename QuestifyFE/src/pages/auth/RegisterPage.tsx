import { useState } from 'react';
import { useAuthContext } from '../../contexts/AuthContext';
import { useNavigate, Link } from 'react-router-dom';

export default function RegisterPage() {
  const { register } = useAuthContext();
  const nav = useNavigate();

  const [form, setForm] = useState({
    username: '',
    email: '',
    displayName: '',
    password: '',
  });
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErr(null);
    setLoading(true);
    try {
      await register({
        username: form.username.trim(),
        email: form.email.trim(),
        displayName: form.displayName.trim(),
        password: form.password,
      });
      nav('/quests', { replace: true }); 
    } catch (e: any) {
      const msg =
        e?.response?.data?.message ??
        e?.message ??
        'Could not register';
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
        <h1 className="text-2xl font-semibold mb-1">Create account</h1>
        <p className="text-sm text-slate-500 mb-6">
          You’ll be signed in automatically after creating your account.
        </p>

        <label className="block text-sm mb-1">Username</label>
        <input
          className="w-full border rounded-xl px-3 py-2 mb-3 outline-none focus:ring focus:ring-slate-200"
          value={form.username}
          onChange={(e) => setForm({ ...form, username: e.target.value })}
          autoComplete="username"
          required
          minLength={3}
          maxLength={32}
        />

        <label className="block text-sm mb-1">Email</label>
        <input
          type="email"
          className="w-full border rounded-xl px-3 py-2 mb-3 outline-none focus:ring focus:ring-slate-200"
          value={form.email}
          onChange={(e) => setForm({ ...form, email: e.target.value })}
          autoComplete="email"
          required
        />

        <label className="block text-sm mb-1">Display name</label>
        <input
          className="w-full border rounded-xl px-3 py-2 mb-3 outline-none focus:ring focus:ring-slate-200"
          value={form.displayName}
          onChange={(e) => setForm({ ...form, displayName: e.target.value })}
          required
          maxLength={64}
        />

        <label className="block text-sm mb-1">Password</label>
        <input
          type="password"
          className="w-full border rounded-xl px-3 py-2 mb-1 outline-none focus:ring focus:ring-slate-200"
          value={form.password}
          onChange={(e) => setForm({ ...form, password: e.target.value })}
          autoComplete="new-password"
          required
          minLength={8}
        />
        <p className="text-xs text-slate-500 mb-3">
          Must include at least one uppercase letter, one number, and one special character.
        </p>

        {err && <p className="text-red-600 text-sm mb-3">{err}</p>}

        <button
          type="submit"
          disabled={loading}
          className="w-full rounded-xl px-3 py-2 bg-black text-white disabled:opacity-60"
        >
          {loading ? 'Creating…' : 'Create account'}
        </button>

        <div className="text-sm mt-4 text-center">
          Already have an account?{' '}
          <Link to="/login" className="text-blue-600 hover:underline">
            Sign in
          </Link>
        </div>
      </form>
    </div>
  );
}
