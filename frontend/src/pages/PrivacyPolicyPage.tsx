import { Link } from "react-router-dom";

const OWNER_NAME = "Cătălin Mihai Popoiu";
const CONTACT_EMAIL = "privacy@questify.com";

export default function PrivacyPolicyPage() {
  return (
    <main className="min-h-screen">
      <div className="mx-auto w-full max-w-4xl px-6 py-10">
        <div className="flex items-center justify-between gap-4">
          <h1 className="text-3xl font-bold tracking-tight text-slate-900 dark:text-slate-100">
            Privacy Policy
          </h1>
          <Link
            to="/"
            className="rounded-2xl border px-4 py-2 text-sm shadow hover:shadow-md hover:bg-gray-100 dark:hover:bg-[#161b26]
                       border-slate-200 dark:border-slate-800"
          >
            Back to Home
          </Link>
        </div>

        <div className="mt-2 text-sm text-slate-600 dark:text-slate-300">
          Last updated: <span className="font-medium">December 27, 2025</span>
        </div>

        <div className="mt-8 space-y-8 rounded-3xl border bg-white/70 p-8 shadow-xl backdrop-blur dark:border-slate-800 dark:bg-[#0f1115]/70">
          <section className="space-y-3">
            <h2 className="text-lg font-semibold">1. Who we are</h2>
            <p className="text-sm text-slate-700 dark:text-slate-300">
              Questify is a gamified challenge-tracking platform. This project is operated by{" "}
              <span className="font-medium">{OWNER_NAME}</span> ("we", "us", "our").
            </p>
            <p className="text-sm text-slate-700 dark:text-slate-300">
              If you have questions about privacy, contact us at{" "}
              <a className="underline" href={`mailto:${CONTACT_EMAIL}`}>
                {CONTACT_EMAIL}
              </a>
              .
            </p>
          </section>

          <section className="space-y-3">
            <h2 className="text-lg font-semibold">2. What data we collect</h2>
            <p className="text-sm text-slate-700 dark:text-slate-300">
              We collect data that helps you use Questify and that is necessary to operate the service:
            </p>
            <ul className="list-disc pl-5 text-sm text-slate-700 dark:text-slate-300 space-y-1">
              <li>
                <span className="font-medium">Account data</span>: username, email, display name, and a unique user ID
                (managed by our identity provider).
              </li>
              <li>
                <span className="font-medium">Profile data</span>: optional bio and avatar URL (if provided).
              </li>
              <li>
                <span className="font-medium">Quest data</span>: quests you create or join, progress, streaks, and badges.
              </li>
              <li>
                <span className="font-medium">Submission data</span>: submissions you make for quests, review status, and timestamps.
              </li>
              <li>
                <span className="font-medium">Proof metadata</span>: file keys, content types, sizes, and scan results.
                We do not intentionally store personal information in file names.
              </li>
              <li>
                <span className="font-medium">Operational data</span>: logs, security events, and metrics needed to keep the platform secure and reliable.
              </li>
            </ul>
          </section>

          <section className="space-y-3">
            <h2 className="text-lg font-semibold">3. How we use your data</h2>
            <p className="text-sm text-slate-700 dark:text-slate-300">We use your data to:</p>
            <ul className="list-disc pl-5 text-sm text-slate-700 dark:text-slate-300 space-y-1">
              <li>Provide core features (quests, submissions, proofs, streaks, and dashboards).</li>
              <li>Secure the platform (authentication, authorization, fraud/malware protection, abuse prevention).</li>
              <li>Maintain and improve reliability (monitoring, incident investigation, performance).</li>
              <li>Comply with legal obligations and respond to lawful requests.</li>
            </ul>
          </section>

          <section className="space-y-3">
            <h2 className="text-lg font-semibold">4. Legal basis (GDPR)</h2>
            <p className="text-sm text-slate-700 dark:text-slate-300">
              Where GDPR applies, we process personal data based on:
            </p>
            <ul className="list-disc pl-5 text-sm text-slate-700 dark:text-slate-300 space-y-1">
              <li><span className="font-medium">Contract</span> (Art. 6(1)(b)) — to provide features you request.</li>
              <li><span className="font-medium">Legitimate interests</span> (Art. 6(1)(f)) — to secure and operate the platform.</li>
              <li><span className="font-medium">Legal obligation</span> (Art. 6(1)(c)) — if required by law.</li>
            </ul>
          </section>

          <section className="space-y-3">
            <h2 className="text-lg font-semibold">5. How we share data</h2>
            <p className="text-sm text-slate-700 dark:text-slate-300">
              We do not sell your personal data. We may share data only as needed to operate Questify, for example:
            </p>
            <ul className="list-disc pl-5 text-sm text-slate-700 dark:text-slate-300 space-y-1">
              <li>With infrastructure providers (hosting, storage) to deliver the service.</li>
              <li>With our identity provider for login and account security.</li>
              <li>When required by law, regulation, or valid legal process.</li>
            </ul>
          </section>

          <section className="space-y-3">
            <h2 className="text-lg font-semibold">6. Security</h2>
            <p className="text-sm text-slate-700 dark:text-slate-300">
              We use security measures designed to protect your data, such as short-lived signed URLs for file access,
              malware scanning for uploads, and role-based access controls.
            </p>
          </section>

          <section className="space-y-3">
            <h2 className="text-lg font-semibold">7. Data retention</h2>
            <p className="text-sm text-slate-700 dark:text-slate-300">
              We keep personal data only as long as needed for the purposes described above. In particular:
            </p>
            <ul className="list-disc pl-5 text-sm text-slate-700 dark:text-slate-300 space-y-1">
              <li><span className="font-medium">Export ZIPs</span> expire automatically (e.g., after 24 hours).</li>
              <li><span className="font-medium">Proof files</span> are retained for a limited period after review (e.g., 30 days), then deleted.</li>
              <li><span className="font-medium">Account deletion</span> triggers anonymization and disables login.</li>
            </ul>
          </section>

          <section className="space-y-3">
            <h2 className="text-lg font-semibold">8. Your rights</h2>
            <p className="text-sm text-slate-700 dark:text-slate-300">
              Depending on your location, you may have rights to access, correct, delete, export, or restrict processing.
              Questify supports:
            </p>
            <ul className="list-disc pl-5 text-sm text-slate-700 dark:text-slate-300 space-y-1">
              <li><span className="font-medium">Access & correction</span> via your Profile page.</li>
              <li><span className="font-medium">Data export</span> via an export job that produces a ZIP download.</li>
              <li><span className="font-medium">Deletion</span> via account deletion (irreversible).</li>
            </ul>
          </section>

          <section className="space-y-3">
            <h2 className="text-lg font-semibold">9. Cookies</h2>
            <p className="text-sm text-slate-700 dark:text-slate-300">
              Questify does not use advertising cookies. Some technical storage may be used for essential functionality
              (for example, to keep your theme preference).
            </p>
          </section>

          <section className="space-y-3">
            <h2 className="text-lg font-semibold">10. Changes to this policy</h2>
            <p className="text-sm text-slate-700 dark:text-slate-300">
              We may update this policy from time to time. We will update the “Last updated” date at the top of the page.
            </p>
          </section>

          <section className="space-y-3">
            <h2 className="text-lg font-semibold">11. Contact</h2>
            <p className="text-sm text-slate-700 dark:text-slate-300">
              If you have privacy questions or requests, contact{" "}
              <a className="underline" href={`mailto:${CONTACT_EMAIL}`}>
                {CONTACT_EMAIL}
              </a>
              .
            </p>
          </section>

          <div className="pt-2 text-xs text-slate-500 dark:text-slate-400">
            Note: Questify is an educational project. This policy is provided for transparency and does not constitute legal advice.
          </div>
        </div>
      </div>
    </main>
  );
}
