import { Link } from 'react-router-dom';
import { useAuthContext } from '../contexts/AuthContext';
import logoMark from '../assets/questify_logo_mark_compass.png';
import { Target, Camera, Flame, Shield } from 'lucide-react';

export default function HomePage() {
  const { user } = useAuthContext();
  const loggedIn = !!user;
  const displayName = user?.displayName ?? user?.username ?? 'Adventurer';

  return (
    <main className="relative min-h-screen flex items-center justify-center">
      <div className="absolute inset-0 -z-10 bg-gradient-to-b from-indigo-50 via-white to-white dark:from-[#0b0d12] dark:via-[#0b0d12] dark:to-[#0b0d12]" />

      <section className="w-full max-w-6xl px-6">
        <div className="mx-auto flex w-full max-w-4xl flex-col items-center">
          <div className="w-full max-w-2xl rounded-3xl border bg-white/70 p-10 text-center shadow-xl backdrop-blur dark:border-slate-800 dark:bg-[#0f1115]/70">
            <div className="mb-6 flex justify-center">
              <img
                src={logoMark}
                alt="Questify logo"
                className="h-20 w-20"
                width={90}
                height={90}
                loading="eager"
              />
            </div>

            <h1 className="text-4xl font-bold tracking-tight"></h1>
            <p className="mt-3 text-base text-slate-600 dark:text-slate-300">
              Create quests, submit proofs, earn streaks and badges. Stay consistent—and make it fun.
            </p>

            {!loggedIn ? (
              <div className="mt-7 flex flex-wrap items-center justify-center gap-3">
                <Link
                  to="/login"
                  className="rounded-2xl border px-4 py-2 text-sm shadow hover:shadow-md hover:bg-gray-100 dark:hover:bg-[#161b26]"
                >
                  Log in
                </Link>
                <Link
                  to="/register"
                  className="rounded-2xl border px-4 py-2 text-sm shadow hover:shadow-md hover:bg-gray-100 dark:hover:bg-[#161b26]"
                >
                  Register
                </Link>
              </div>
            ) : (
              <div className="mt-7 space-y-3">
                <div className="text-sm opacity-80">
                  Welcome back, <span className="font-semibold">{displayName}</span> 👋
                </div>
                <div className="flex flex-wrap items-center justify-center gap-3">
                  <Link
                    to="/quests"
                    className="rounded-2xl bg-black px-4 py-2 text-sm text-white shadow hover:shadow-md dark:bg-white dark:text-black"
                    title="See quests you own or joined"
                  >
                    Open My Quests
                  </Link>
                  <Link
                    to="/quests/discover"
                    className="rounded-2xl border px-4 py-2 text-sm shadow hover:shadow-md hover:bg-gray-100 dark:hover:bg-[#161b26]"
                  >
                    Discover
                  </Link>
                  <Link
                    to="/quests/new"
                    className="rounded-2xl border px-4 py-2 text-sm shadow hover:shadow-md hover:bg-gray-100 dark:hover:bg-[#161b26]"
                  >
                    New Quest
                  </Link>
                  <Link
                    to="/submissions"
                    className="rounded-2xl border px-4 py-2 text-sm shadow hover:shadow-md hover:bg-gray-100 dark:hover:bg-[#161b26]"
                  >
                    Submissions
                  </Link>
                </div>
              </div>
            )}

            <div className="mt-4 flex flex-wrap items-center justify-center gap-3">
              <Link
                to="/privacy"
                className="rounded-2xl border px-4 py-2 text-sm shadow hover:shadow-md hover:bg-gray-100 dark:hover:bg-[#161b26]"
              >
                Privacy Policy
              </Link>
            </div>
          </div>

          <div className="mx-auto mt-8 w-full max-w-3xl grid grid-cols-1 md:grid-cols-2 gap-4">
            <FeatureCard
              Icon={Target}
              title="Set Quests"
              text="Define goals with start/end dates, visibility, and categories."
            />
            <FeatureCard
              Icon={Camera}
              title="Prove It"
              text="Upload images or videos, or link external proofs—securely via signed URLs."
            />
            <FeatureCard
              Icon={Flame}
              title="Keep Streaks"
              text="Stay on track and earn badges as you progress."
            />
            <FeatureCard
              Icon={Shield}
              title="Privacy-First"
              text="Short-lived links, no PII in filenames, GDPR-minded by design."
            />
          </div>

          <div className="mx-auto mt-6 max-w-2xl text-center text-sm text-slate-600 dark:text-slate-300">
            {!loggedIn ? (
              <>
                Join us today,{' '}
                <Link to="/register" className="underline">create an account</Link> and start building your streak.
              </>
            ) : (
              <>
                Need ideas? Check{' '}
                <Link to="/quests/discover" className="underline">Discover</Link> or spin up a{' '}
                <Link to="/quests/new" className="underline">New Quest</Link> to kickstart your next streak.
              </>
            )}
          </div>
        </div>
      </section>
    </main>
  );
}

function FeatureCard({
  Icon,
  title,
  text,
}: {
  Icon: React.ComponentType<React.SVGProps<SVGSVGElement>>;
  title: string;
  text: string;
}) {
  return (
    <div className="rounded-2xl border bg-white/70 p-4 shadow-sm backdrop-blur dark:border-slate-800 dark:bg-[#0f1115]/70">
      <div className="flex items-center gap-3">
        <Icon className="h-6 w-6 flex-shrink-0 opacity-90" />
        <div className="flex-1">
          <div className="font-semibold">{title}</div>
          <div className="mt-1 text-sm text-slate-600 dark:text-slate-300">{text}</div>
        </div>
      </div>
    </div>
  );
}
