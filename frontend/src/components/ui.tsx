import type { ButtonHTMLAttributes, HTMLAttributes, InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from "react";
import { AlertCircle, Loader2 } from "lucide-react";
import { cn } from "./cn";

type Tone = "neutral" | "success" | "warning" | "danger" | "accent" | "info";

export function PageShell({ children, className }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("quest-page", className)}>{children}</div>;
}

export function PageHeader({
  title,
  description,
  actions,
  eyebrow,
  className,
}: {
  title: string;
  description?: ReactNode;
  actions?: ReactNode;
  eyebrow?: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("page-heading", className)}>
      <div className="min-w-0">
        {eyebrow ? <div className="page-kicker">{eyebrow}</div> : null}
        <h1>{title}</h1>
        {description ? <p>{description}</p> : null}
      </div>
      {actions ? <div className="page-actions">{actions}</div> : null}
    </div>
  );
}

export function Panel({ children, className }: HTMLAttributes<HTMLDivElement>) {
  return <section className={cn("panel", className)}>{children}</section>;
}

export function PanelHeader({
  title,
  description,
  actions,
}: {
  title: string;
  description?: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <div className="panel-header">
      <div>
        <h2>{title}</h2>
        {description ? <p>{description}</p> : null}
      </div>
      {actions ? <div className="panel-actions">{actions}</div> : null}
    </div>
  );
}

export function Button({
  className,
  variant = "secondary",
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "primary" | "secondary" | "ghost" | "danger";
}) {
  return <button className={cn("btn", `btn-${variant}`, className)} {...props} />;
}

export function IconButton({
  className,
  label,
  children,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { label: string }) {
  return (
    <button className={cn("icon-btn", className)} aria-label={label} title={label} {...props}>
      {children}
    </button>
  );
}

export function TextInput({ className, ...props }: InputHTMLAttributes<HTMLInputElement>) {
  return <input className={cn("field", className)} {...props} />;
}

export function TextArea({ className, ...props }: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea className={cn("field min-h-28 resize-y", className)} {...props} />;
}

export function SelectInput({ className, ...props }: SelectHTMLAttributes<HTMLSelectElement>) {
  return <select className={cn("field", className)} {...props} />;
}

export function FieldLabel({ children, className }: HTMLAttributes<HTMLLabelElement>) {
  return <label className={cn("field-label", className)}>{children}</label>;
}

export function Badge({
  children,
  tone = "neutral",
  className,
  ...props
}: {
  children: ReactNode;
  tone?: Tone;
  className?: string;
} & HTMLAttributes<HTMLSpanElement>) {
  return <span className={cn("badge", `badge-${tone}`, className)} {...props}>{children}</span>;
}

export function EmptyState({
  title,
  description,
  action,
  className,
}: {
  title: string;
  description?: ReactNode;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("empty-state", className)}>
      <AlertCircle className="h-5 w-5 text-[rgb(var(--accent))]" />
      <div>
        <div className="font-semibold text-[rgb(var(--text))]">{title}</div>
        {description ? <div className="mt-1 text-sm text-[rgb(var(--muted))]">{description}</div> : null}
      </div>
      {action ? <div className="mt-3">{action}</div> : null}
    </div>
  );
}

export function LoadingState({ label = "Loading..." }: { label?: string }) {
  return (
    <div className="state-line">
      <Loader2 className="h-4 w-4 animate-spin" />
      {label}
    </div>
  );
}

export function ErrorState({ message }: { message: ReactNode }) {
  return (
    <div className="state-line state-error">
      <AlertCircle className="h-4 w-4" />
      {message}
    </div>
  );
}

export function StatusBadge({ status }: { status?: string | null }) {
  const value = (status ?? "Unknown").toUpperCase();
  const tone: Tone =
    value === "APPROVED" || value === "COMPLETED" || value === "SUCCESS"
      ? "success"
      : value === "REJECTED" || value === "FAILED"
        ? "danger"
        : value === "PENDING" || value === "SCANNING" || value === "RUNNING"
          ? "warning"
          : value === "ACTIVE"
            ? "accent"
            : "neutral";
  return <Badge tone={tone}>{value}</Badge>;
}
