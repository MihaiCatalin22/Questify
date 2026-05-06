import { Link } from 'react-router-dom';
import { useAuthContext } from '../contexts/useAuthContext';
import logoMark from '../assets/questify_logo_mark_compass.png';
import { Bot, Camera, FileCheck2, Shield, Target } from 'lucide-react';
import { Panel } from '../components/ui';

export default function HomePage() {
  const { user } = useAuthContext();
  const loggedIn = !!user;

  return (
    <main className="min-h-screen px-4 py-6 sm:px-6">
      <section className="mx-auto grid min-h-[calc(100vh-3rem)] w-full max-w-6xl items-center gap-8 lg:grid-cols-[minmax(0,0.95fr)_minmax(360px,1fr)]">
        <div className="space-y-7">
          <div className="flex items-center gap-3">
            <img src={logoMark} alt="" className="h-10 w-10" width={40} height={40} />
            <span className="text-lg font-semibold">Questify</span>
          </div>

          <div>
            <h1 className="max-w-2xl text-4xl font-semibold leading-tight sm:text-5xl">
              Quests, proof, and feedback in one workspace.
            </h1>
            <p className="mt-4 max-w-xl text-base leading-7 text-[rgb(var(--muted))]">
              Create focused quests, submit proof files, review submissions, and ask the AI Coach for realistic next
              quest ideas when you get stuck.
            </p>
          </div>

          {!loggedIn ? (
            <div className="flex flex-wrap gap-3">
              <Link to="/login" className="btn btn-primary">Log in</Link>
              <Link to="/register" className="btn btn-secondary">Register</Link>
              <Link to="/privacy" className="btn btn-ghost">Privacy Policy</Link>
            </div>
          ) : (
            <div className="flex flex-wrap gap-3">
              <Link to="/quests" className="btn btn-primary">Open quests</Link>
              <Link to="/quests/discover" className="btn btn-secondary">Discover</Link>
              <Link to="/coach" className="btn btn-ghost">AI Coach</Link>
            </div>
          )}
        </div>

        <Panel className="overflow-hidden">
          <div className="border-b border-[rgb(var(--border-soft))] p-6">
            <div className="flex items-center justify-between gap-4">
              <div>
                <h2 className="text-xl font-semibold">Quest workspace</h2>
                <p className="mt-1 text-sm text-[rgb(var(--muted))]">A quick view of how the product is organized.</p>
              </div>
              <img
                src={logoMark}
                alt=""
                className="h-12 w-12"
                width={48}
                height={48}
                loading="eager"
              />
            </div>
          </div>

          <div className="grid gap-3 p-6">
            <FeatureCard
              Icon={Target}
              title="Create quests"
              text="Set the goal, category, visibility, and deadline."
            />
            <FeatureCard
              Icon={Camera}
              title="Upload proof"
              text="Submit one or more proof files through the existing proof flow."
            />
            <FeatureCard
              Icon={FileCheck2}
              title="Track review"
              text="See pending, approved, rejected, and scanning states clearly."
            />
            <FeatureCard
              Icon={Bot}
              title="Use AI Coach"
              text="Generate quest suggestions from your saved coach goal."
            />
            <div className="mt-2 rounded-lg border border-[rgb(var(--border-soft))] bg-[rgba(var(--surface-2),0.45)] p-4">
              <div className="flex items-start gap-3">
                <Shield className="mt-0.5 h-5 w-5 text-[rgb(var(--accent))]" />
                <p className="text-sm leading-6 text-[rgb(var(--muted))]">
                  Proof links are short-lived and uploads avoid using personal information in file names.
                </p>
              </div>
            </div>
          </div>
        </Panel>
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
    <div className="rounded-lg border border-[rgb(var(--border-soft))] bg-[rgba(var(--surface-2),0.45)] p-4">
      <div className="flex items-center gap-3">
        <Icon className="h-5 w-5 flex-shrink-0 text-[rgb(var(--accent))]" />
        <div className="flex-1">
          <div className="font-semibold">{title}</div>
          <div className="mt-1 text-sm leading-6 text-[rgb(var(--muted))]">{text}</div>
        </div>
      </div>
    </div>
  );
}
