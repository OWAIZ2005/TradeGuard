"use client";

import { AlertTriangle, ChevronLeft, ChevronRight, Download, Inbox, Loader2, RefreshCw } from "lucide-react";
import type { ReactNode } from "react";
import type { ApiClientError } from "@/lib/api";
import { useDateRange } from "@/lib/filters";

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: ReactNode }) {
  return (
    <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 className="text-xl font-semibold tracking-tight text-navy-950">{title}</h1>
        {subtitle && <p className="mt-0.5 text-sm text-slate-500">{subtitle}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  );
}

export function Kpi({
  label,
  value,
  hint,
  tone = "default",
  testId,
}: {
  label: string;
  value: ReactNode;
  hint?: ReactNode;
  tone?: "default" | "good" | "bad" | "warn";
  testId?: string;
}) {
  const color = { default: "text-navy-950", good: "text-emerald-700", bad: "text-red-700", warn: "text-amber-700" }[tone];
  return (
    <div className="card px-4 py-3" data-testid={testId}>
      <div className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</div>
      <div className={`mt-1 text-2xl font-semibold tabular-nums ${color}`} data-testid={testId ? `${testId}-value` : undefined}>
        {value}
      </div>
      {hint && <div className="mt-0.5 text-xs text-slate-500">{hint}</div>}
    </div>
  );
}

const STATUS_STYLES: Record<string, string> = {
  VALID: "bg-emerald-50 text-emerald-700 ring-emerald-600/20",
  COMPLETED: "bg-emerald-50 text-emerald-700 ring-emerald-600/20",
  INVALID: "bg-red-50 text-red-700 ring-red-600/20",
  FAILED: "bg-red-50 text-red-700 ring-red-600/20",
  PROCESSING: "bg-amber-50 text-amber-700 ring-amber-600/20",
  BUY: "bg-blue-50 text-blue-700 ring-blue-600/20",
  SELL: "bg-orange-50 text-orange-700 ring-orange-600/20",
};

export function Badge({ value }: { value: string | null }) {
  if (!value) return <span className="text-slate-400">—</span>;
  return (
    <span
      className={`inline-flex items-center rounded px-1.5 py-0.5 text-xs font-medium ring-1 ring-inset ${
        STATUS_STYLES[value] ?? "bg-slate-50 text-slate-700 ring-slate-500/20"
      }`}
    >
      {value}
    </span>
  );
}

export function Loading({ label = "Loading…" }: { label?: string }) {
  return (
    <div role="status" aria-live="polite" className="flex items-center justify-center gap-2 py-10 text-slate-500">
      <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> {label}
    </div>
  );
}

export function Empty({ title, hint }: { title: string; hint?: ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-1 py-10 text-center" data-testid="empty-state">
      <Inbox className="h-6 w-6 text-slate-400" aria-hidden />
      <div className="font-medium text-navy-900">{title}</div>
      {hint && <div className="max-w-md text-sm text-slate-500">{hint}</div>}
    </div>
  );
}

export function ErrorState({ error, onRetry }: { error: ApiClientError; onRetry?: () => void }) {
  return (
    <div role="alert" className="flex flex-col items-center gap-2 py-8 text-center" data-testid="error-state">
      <AlertTriangle className="h-6 w-6 text-red-600" aria-hidden />
      <div className="font-medium text-red-800">Something went wrong</div>
      <div className="max-w-md text-sm text-slate-600">{error.message}</div>
      {onRetry && (
        <button className="btn mt-1" onClick={onRetry}>
          <RefreshCw className="h-3.5 w-3.5" aria-hidden /> Retry
        </button>
      )}
    </div>
  );
}

/** Renders loading / error / empty / content for an API-backed card. */
export function Async<T>({
  state,
  isEmpty,
  empty,
  children,
}: {
  state: { data: T | null; loading: boolean; error: ApiClientError | null; reload: () => void };
  isEmpty?: (d: T) => boolean;
  empty?: ReactNode;
  children: (d: T) => ReactNode;
}) {
  if (state.error) return <ErrorState error={state.error} onRetry={state.reload} />;
  if (state.loading && !state.data) return <Loading />;
  if (!state.data) return <Loading />;
  if (isEmpty?.(state.data)) return <>{empty ?? <Empty title="No data" />}</>;
  return <div className={state.loading ? "opacity-60 transition-opacity" : undefined}>{children(state.data)}</div>;
}

export function Card({
  title,
  subtitle,
  actions,
  children,
  className = "",
  testId,
}: {
  title?: string;
  subtitle?: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
  testId?: string;
}) {
  return (
    <section className={`card ${className}`} data-testid={testId}>
      {(title || actions) && (
        <header className="flex items-start justify-between gap-3 border-b border-slate-100 px-4 py-3">
          <div>
            {title && <h2 className="text-sm font-semibold text-navy-950">{title}</h2>}
            {subtitle && <p className="mt-0.5 text-xs text-slate-500">{subtitle}</p>}
          </div>
          {actions}
        </header>
      )}
      <div className="p-4">{children}</div>
    </section>
  );
}

export function Pagination({
  page,
  totalPages,
  totalElements,
  size,
  onPage,
}: {
  page: number;
  totalPages: number;
  totalElements: number;
  size: number;
  onPage: (p: number) => void;
}) {
  const first = totalElements === 0 ? 0 : page * size + 1;
  const last = Math.min((page + 1) * size, totalElements);
  return (
    <nav aria-label="Pagination" className="flex items-center justify-between gap-3 px-1 pt-3 text-xs text-slate-600">
      <span data-testid="page-range">
        {first}–{last} of {totalElements.toLocaleString("en-US")}
      </span>
      <div className="flex items-center gap-1">
        <button className="btn px-2" disabled={page <= 0} onClick={() => onPage(page - 1)} aria-label="Previous page">
          <ChevronLeft className="h-4 w-4" aria-hidden />
        </button>
        <span className="px-2 tabular-nums" data-testid="page-indicator">
          Page {totalPages === 0 ? 0 : page + 1} / {totalPages}
        </span>
        <button
          className="btn px-2"
          disabled={page + 1 >= totalPages}
          onClick={() => onPage(page + 1)}
          aria-label="Next page"
        >
          <ChevronRight className="h-4 w-4" aria-hidden />
        </button>
      </div>
    </nav>
  );
}

export function DownloadLink({ href, children, testId }: { href: string; children: ReactNode; testId?: string }) {
  return (
    <a className="btn" href={href} download data-testid={testId}>
      <Download className="h-3.5 w-3.5" aria-hidden /> {children}
    </a>
  );
}

/** Global date range (UTC days, inclusive) shared by Overview, Trade Explorer and Analytics. */
export function DateRangeFilter() {
  const { from, to, setRange } = useDateRange();
  const invalid = from !== "" && to !== "" && from > to;
  return (
    <div className="flex flex-wrap items-end gap-2" role="group" aria-label="Date range">
      <div>
        <label className="label" htmlFor="range-from">
          From
        </label>
        <input
          id="range-from"
          type="date"
          className="input w-40"
          value={from}
          max={to || undefined}
          onChange={(e) => setRange({ from: e.target.value, to })}
        />
      </div>
      <div>
        <label className="label" htmlFor="range-to">
          To
        </label>
        <input
          id="range-to"
          type="date"
          className="input w-40"
          value={to}
          min={from || undefined}
          onChange={(e) => setRange({ from, to: e.target.value })}
        />
      </div>
      <button className="btn" disabled={!from && !to} onClick={() => setRange({ from: "", to: "" })}>
        All time
      </button>
      {invalid && (
        <span role="alert" className="text-xs text-red-700">
          “From” must not be after “To”.
        </span>
      )}
    </div>
  );
}

export const CURRENCY_NOTE =
  "Notional values are shown per currency. They are not converted or added across currencies because no exchange-rate source is configured.";
