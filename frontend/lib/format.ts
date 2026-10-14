const int = new Intl.NumberFormat("en-US", { maximumFractionDigits: 0 });
const dec = new Intl.NumberFormat("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 6 });

export const fmtInt = (n: number | null | undefined) => (n == null ? "—" : int.format(n));
export const fmtDec = (n: number | null | undefined) => (n == null ? "—" : dec.format(n));
export const fmtPct = (n: number | null | undefined) => (n == null ? "—" : `${n.toFixed(2)}%`);

export function fmtMoney(amount: number | null | undefined, currency: string | null | undefined): string {
  if (amount == null) return "—";
  try {
    return new Intl.NumberFormat("en-US", { style: "currency", currency: currency ?? "USD" }).format(amount);
  } catch {
    return `${dec.format(amount)} ${currency ?? ""}`.trim();
  }
}

/** Timestamps are shown in UTC so what you see is what is stored. */
export function fmtDateTime(iso: string | null | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return `${d.toISOString().slice(0, 19).replace("T", " ")} UTC`;
}

export function fmtBytes(n: number): string {
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / 1024 / 1024).toFixed(1)} MB`;
}

export const humanize = (code: string) =>
  code
    .toLowerCase()
    .split("_")
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(" ");
