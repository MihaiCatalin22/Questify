import { Link } from 'react-router-dom';

export default function HomePage() {
  return (
    <main className="min-h-screen flex items-center justify-center bg-gray-50">
      <div className="max-w-2xl mx-auto p-8">
        <h1 className="text-4xl font-bold mb-4">Questify</h1>
        <p className="text-lg mb-6">
          Track personal quests, submit proofs, earn streaks and badges.
        </p>
        <div className="flex gap-3">
          <Link to="/login" className="px-4 py-2 rounded-2xl shadow border hover:bg-gray-100">
            Log in
          </Link>
          <Link to="/register" className="px-4 py-2 rounded-2xl shadow border hover:bg-gray-100">
            Register
          </Link>
          <Link to="/quests" className="px-4 py-2 rounded-2xl shadow border hover:bg-gray-100">
            Go to app
          </Link>
        </div>
      </div>
    </main>
  );
}
