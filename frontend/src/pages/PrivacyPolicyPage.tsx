import { Link } from "react-router-dom";
import { PageHeader, PageShell, Panel } from "../components/ui";

const OWNER_NAME = "Cătălin Mihai Popoiu";
const CONTACT_EMAIL = "privacy@questify.com";
const CONTROLLER_LOCATION = "Eindhoven, Netherlands";

export default function PrivacyPolicyPage() {
  return (
    <main className="min-h-screen px-4 py-8 sm:px-6">
      <PageShell className="mx-auto max-w-4xl">
        <PageHeader
          title="Privacy Policy"
          description={<span>Last updated: <span className="font-medium">December 28, 2025</span></span>}
          actions={<Link to="/" className="btn btn-secondary">Back to Home</Link>}
        />

        <Panel className="space-y-8 p-6 sm:p-8">
          <section className="space-y-3">
            <h2 className="text-lg font-semibold">1. Who we are</h2>
            <p className="text-sm leading-6 text-[rgb(var(--muted))]">
              Questify is a gamified challenge-tracking platform. This project is operated by{" "}
              <span className="font-medium">{OWNER_NAME}</span> ("we", "us", "our").
            </p>
            <p className="text-sm leading-6 text-[rgb(var(--muted))]">
              <span className="font-medium">Data controller</span>: {OWNER_NAME} ({CONTROLLER_LOCATION})
            </p>
            <p className="text-sm leading-6 text-[rgb(var(--muted))]">
              If you have questions about privacy, contact us at{" "}
              <a className="link" href={`mailto:${CONTACT_EMAIL}`}>
                {CONTACT_EMAIL}
              </a>
              .
            </p>
          </section>

          <section className="space-y-3">
            <h2 className="text-lg font-semibold">2. What data we collect</h2>
            <p className="text-sm leading-6 text-[rgb(var(--muted))]">
              We collect data that helps you use Questify and that is necessary to operate the service:
            </p>
            <ul className="list-disc pl-5 text-sm leading-6 text-[rgb(var(--muted))] space-y-1">
              <li>
                <span className="font-medium">Account data</span>: username, email, display name, and a unique user ID
                (managed by our identity provider).
              </li>
              <li>
                <span className="font-medium">Profile data</span>: optional bio and avatar URL (if provided).
              </li>
              <li>
                <span className="font-medium">Quest data</span>: quests you create or join, progress, categories, and deadlines.
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
            <p className="text-sm leading-6 text-[rgb(var(--muted))]">We use your data to:</p>
            <ul className="list-disc pl-5 text-sm leading-6 text-[rgb(var(--muted))] space-y-1">
              <li>Provide core features (quests, submissions, proof uploads, reviews, and dashboards).</li>
              <li>Secure the platform (authentication, authorization, fraud/malware protection, abuse prevention).</li>
              <li>Maintain and improve reliability (monitoring, incident investigation, performance).</li>
              <li>Comply with legal obligations and respond to lawful requests.</li>
            </ul>
          </section>

          <section className="space-y-3">
            <h2 className="text-lg font-semibold">4. Legal basis (GDPR)</h2>
            <p className="text-sm leading-6 text-[rgb(var(--muted))]">
              Where GDPR applies, we process personal data based on:
            </p>
            <ul className="list-disc pl-5 text-sm leading-6 text-[rgb(var(--muted))] space-y-1">
              <li><span className="font-medium">Contract</span> (Art. 6(1)(b)) — to provide features you request.</li>
              <li><span className="font-medium">Legitimate interests</span> (Art. 6(1)(f)) — to secure and operate the platform.</li>
              <li><span className="font-medium">Legal obligation</span> (Art. 6(1)(c)) — if required by law.</li>
            </ul>
          </section>

          <section className="space-y-3">
            <h2 className="text-lg font-semibold">5. How we share data</h2>
            <p className="text-sm leading-6 text-[rgb(var(--muted))]">
              We do not sell your personal data. We may share data only as needed to operate Questify, for example:
            </p>
            <ul className="list-disc pl-5 text-sm leading-6 text-[rgb(var(--muted))] space-y-1">
              <li>With infrastructure providers (hosting, storage) to deliver the service.</li>
              <li>With our identity provider for login and account security.</li>
              <li>When required by law, regulation, or valid legal process.</li>
            </ul>
          </section>

          <section className="space-y-3">
            <h2 className="text-lg font-semibold">6. International transfers</h2>
            <p className="text-sm leading-6 text-[rgb(var(--muted))]">
              Depending on provider configuration, data may be processed in the EU and potentially other regions.
              Where applicable, we rely on provider safeguards for international transfers (for example, standard contractual clauses).
            </p>
          </section>

          <section className="space-y-3">
            <h2 className="text-lg font-semibold">7. Security</h2>
            <p className="text-sm leading-6 text-[rgb(var(--muted))]">
              We use security measures designed to protect your data, such as short-lived signed URLs for file access,
              malware scanning for uploads, and role-based access controls.
            </p>
          </section>

          <section className="space-y-3">
            <h2 className="text-lg font-semibold">8. Data retention</h2>
            <p className="text-sm leading-6 text-[rgb(var(--muted))]">
              We keep personal data only as long as needed for the purposes described above. In particular:
            </p>
            <ul className="list-disc pl-5 text-sm leading-6 text-[rgb(var(--muted))] space-y-1">
              <li><span className="font-medium">Export ZIPs</span> are automatically deleted after <span className="font-medium">24 hours</span>.</li>
              <li>
                <span className="font-medium">Proof files</span> are retained for <span className="font-medium">30 days after review</span>, then deleted
                (subject to short technical delays).
              </li>
              <li>
                <span className="font-medium">Account deletion</span> disables login and triggers anonymization/deletion of associated data where applicable.
              </li>
            </ul>
          </section>

          <section className="space-y-3">
            <h2 className="text-lg font-semibold">9. Your rights</h2>
            <p className="text-sm leading-6 text-[rgb(var(--muted))]">
              Depending on your location, you may have rights to access, correct, delete, export, or restrict processing.
              Questify supports:
            </p>
            <ul className="list-disc pl-5 text-sm leading-6 text-[rgb(var(--muted))] space-y-1">
              <li><span className="font-medium">Access & correction</span> via your Profile page.</li>
              <li><span className="font-medium">Data export</span> via an export job that produces a ZIP download.</li>
              <li><span className="font-medium">Deletion</span> via account deletion (irreversible).</li>
              <li>
                <span className="font-medium">Complaint</span>: you may have the right to lodge a complaint with your local supervisory authority.
              </li>
            </ul>
          </section>

          <section className="space-y-3">
            <h2 className="text-lg font-semibold">10. Cookies</h2>
            <p className="text-sm leading-6 text-[rgb(var(--muted))]">
              Questify does not use advertising cookies. We may use essential technical storage (for example, local storage)
              for functionality like keeping you signed in and preserving coach preferences on this device.
            </p>
          </section>

          <section className="space-y-3">
            <h2 className="text-lg font-semibold">11. Changes to this policy</h2>
            <p className="text-sm leading-6 text-[rgb(var(--muted))]">
              We may update this policy from time to time. We will update the “Last updated” date at the top of the page.
            </p>
          </section>

          <section className="space-y-3">
            <h2 className="text-lg font-semibold">12. Contact</h2>
            <p className="text-sm leading-6 text-[rgb(var(--muted))]">
              If you have privacy questions or requests, contact{" "}
              <a className="link" href={`mailto:${CONTACT_EMAIL}`}>
                {CONTACT_EMAIL}
              </a>
              .
            </p>
          </section>

          <div className="pt-2 text-xs text-[rgb(var(--faint))]">
            Note: Questify is an educational project. This policy is provided for transparency and does not constitute legal advice.
          </div>
        </Panel>
      </PageShell>
    </main>
  );
}
