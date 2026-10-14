"use client";

import { ArrowDown, ArrowUp, Search, X } from "lucide-react";
import { useEffect, useState } from "react";
import { Async, Badge, Card, DateRangeFilter, DownloadLink, Empty, Loading, ErrorState, PageHeader, Pagination } from "@/components/ui";
import { api, reportUrl } from "@/lib/api";
import { useDateRange } from "@/lib/filters";
import { fmtDateTime, fmtDec } from "@/lib/format";
import type { Trade } from "@/lib/types";
import { useApi } from "@/lib/useApi";

interface Draft {
  q: string;
  instrument: string;
  side: string;
  status: string;
  batchId: string;
}

const EMPTY: Draft = { q: "", instrument: "", side: "", status: "", batchId: "" };
const SIZE_OPTIONS = [10, 25, 50, 100];

const COLUMNS: { key: string; label: string; sortable?: boolean; align?: "right" }[] = [
  { key: "tradeId", label: "Trade ID", sortable: true },
  { key: "instrument", label: "Instrument", sortable: true },
  { key: "timestamp", label: "Timestamp", sortable: true },
  { key: "side", label: "Side" },
  { key: "price", label: "Price", sortable: true, align: "right" },
  { key: "quantity", label: "Quantity", sortable: true, align: "right" },
  { key: "currency", label: "Ccy" },
  { key: "notional", label: "Notional", sortable: true, align: "right" },
  { key: "status", label: "Status" },
  { key: "batchId", label: "Batch", sortable: true },
];

export default function TradeExplorerPage() {
  const { from, to } = useDateRange();
  const [draft, setDraft] = useState<Draft>(EMPTY);
  const [applied, setApplied] = useState<Draft>(EMPTY);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [sort, setSort] = useState("timestamp");
  const [direction, setDirection] = useState<"asc" | "desc">("desc");
  const [selected, setSelected] = useState<number | null>(null);

  const instruments = useApi(() => api.tradeInstruments(), []);
  const batches = useApi(() => api.imports(0, 100), []);

  const params = {
    from,
    to,
    instrument: applied.instrument,
    side: applied.side,
    status: applied.status,
    batchId: applied.batchId,
    q: applied.q,
  };
  const trades = useApi(
    () => api.trades({ ...params, sort, direction, page, size }),
    [from, to, applied, sort, direction, page, size],
  );

  // Changing the shared date range returns to the first page.
  useEffect(() => setPage(0), [from, to]);

  function apply(e: React.FormEvent) {
    e.preventDefault();
    setApplied(draft);
    setPage(0);
  }

  function reset() {
    setDraft(EMPTY);
    setApplied(EMPTY);
    setPage(0);
  }

  function toggleSort(key: string) {
    if (sort === key) setDirection(direction === "asc" ? "desc" : "asc");
    else {
      setSort(key);
      setDirection("asc");
    }
    setPage(0);
  }

  const exportFilters = { from, to, instrument: applied.instrument, side: applied.side, batchId: applied.batchId };

  return (
    <>
      <PageHeader
        title="Trade Explorer"
        subtitle="Search every imported row, valid or rejected. Filtering, sorting and paging run on the server."
        actions={
          <>
            <DownloadLink href={reportUrl.validTrades({ ...exportFilters, q: applied.q })} testId="download-valid-trades">
              Valid trades (CSV)
            </DownloadLink>
            <DownloadLink href={reportUrl.validationErrors(exportFilters)} testId="download-errors">
              Validation errors (CSV)
            </DownloadLink>
          </>
        }
      />

      <Card className="mb-4">
        <form onSubmit={apply} className="grid gap-3 md:grid-cols-6" aria-label="Trade filters">
          <div className="md:col-span-2">
            <label className="label" htmlFor="f-q">Search trade or trader ID</label>
            <div className="relative">
              <Search className="pointer-events-none absolute left-2 top-2 h-4 w-4 text-slate-400" aria-hidden />
              <input id="f-q" className="input pl-8" placeholder="e.g. DEMO-0012" value={draft.q} maxLength={64} onChange={(e) => setDraft({ ...draft, q: e.target.value })} />
            </div>
          </div>
          <div>
            <label className="label" htmlFor="f-instrument">Instrument</label>
            <select id="f-instrument" className="input" value={draft.instrument} onChange={(e) => setDraft({ ...draft, instrument: e.target.value })}>
              <option value="">All</option>
              {(instruments.data ?? []).map((i) => <option key={i} value={i}>{i}</option>)}
            </select>
          </div>
          <div>
            <label className="label" htmlFor="f-side">Side</label>
            <select id="f-side" className="input" value={draft.side} onChange={(e) => setDraft({ ...draft, side: e.target.value })}>
              <option value="">All</option>
              <option value="BUY">Buy</option>
              <option value="SELL">Sell</option>
            </select>
          </div>
          <div>
            <label className="label" htmlFor="f-status">Status</label>
            <select id="f-status" className="input" value={draft.status} onChange={(e) => setDraft({ ...draft, status: e.target.value })}>
              <option value="">All</option>
              <option value="VALID">Valid</option>
              <option value="INVALID">Invalid</option>
            </select>
          </div>
          <div>
            <label className="label" htmlFor="f-batch">Import batch</label>
            <select id="f-batch" className="input" value={draft.batchId} onChange={(e) => setDraft({ ...draft, batchId: e.target.value })}>
              <option value="">All</option>
              {(batches.data?.content ?? []).map((b) => <option key={b.id} value={b.id}>#{b.id} · {b.filename}</option>)}
            </select>
          </div>
          <div className="md:col-span-5"><DateRangeFilter /></div>
          <div className="flex items-end justify-end gap-2">
            <button type="button" className="btn" onClick={reset}>Reset</button>
            <button type="submit" className="btn-primary" data-testid="apply-filters">Apply filters</button>
          </div>
        </form>
      </Card>

      <Card testId="trades-card">
        <Async
          state={trades}
          isEmpty={(p) => p.totalElements === 0}
          empty={<Empty title="No trades match these filters" hint="Adjust or reset the filters, or import a file first." />}
        >
          {(p) => (
            <>
              <div className="overflow-x-auto">
                <table className="w-full min-w-[1000px]" data-testid="trades-table">
                  <thead>
                    <tr>
                      {COLUMNS.map((c) => (
                        <th
                          key={c.key}
                          className={`th ${c.align === "right" ? "text-right" : ""}`}
                          aria-sort={sort === c.key ? (direction === "asc" ? "ascending" : "descending") : undefined}
                        >
                          {c.sortable ? (
                            <button className="inline-flex items-center gap-1 uppercase" onClick={() => toggleSort(c.key)}>
                              {c.label}
                              {sort === c.key && (direction === "asc" ? <ArrowUp className="h-3 w-3" aria-hidden /> : <ArrowDown className="h-3 w-3" aria-hidden />)}
                            </button>
                          ) : (
                            c.label
                          )}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {p.content.map((t) => <TradeRow key={t.id} t={t} onOpen={() => setSelected(t.id)} />)}
                  </tbody>
                </table>
              </div>
              <div className="flex items-center justify-between">
                <Pagination page={p.page} totalPages={p.totalPages} totalElements={p.totalElements} size={p.size} onPage={setPage} />
                <label className="flex items-center gap-2 pt-3 text-xs text-slate-600">
                  Rows per page
                  <select className="input w-20 py-1" value={size} onChange={(e) => { setSize(Number(e.target.value)); setPage(0); }}>
                    {SIZE_OPTIONS.map((s) => <option key={s}>{s}</option>)}
                  </select>
                </label>
              </div>
            </>
          )}
        </Async>
      </Card>

      {selected !== null && <TradeDrawer id={selected} onClose={() => setSelected(null)} />}
    </>
  );
}

function TradeRow({ t, onOpen }: { t: Trade; onOpen: () => void }) {
  return (
    <tr className={`hover:bg-slate-50 ${t.status === "INVALID" ? "bg-red-50/30" : ""}`} data-testid="trade-row">
      <td className="td">
        <button className="font-mono text-xs font-medium text-brand-700 underline" onClick={onOpen} aria-label={`Inspect trade ${t.tradeId ?? `row ${t.rowNumber}`}`}>
          {t.tradeId ?? `(row ${t.rowNumber})`}
        </button>
      </td>
      <td className="td">{t.instrument ?? "—"}</td>
      <td className="td whitespace-nowrap">{fmtDateTime(t.timestamp)}</td>
      <td className="td"><Badge value={t.side} /></td>
      <td className="td text-right tabular-nums">{fmtDec(t.price)}</td>
      <td className="td text-right tabular-nums">{fmtDec(t.quantity)}</td>
      <td className="td">{t.currency ?? "—"}</td>
      <td className="td text-right tabular-nums">{t.status === "VALID" ? fmtDec(t.notional) : "—"}</td>
      <td className="td">
        <Badge value={t.status} />
        {t.errorCount > 0 && <span className="ml-1 text-xs text-slate-500">{t.errorCount} err</span>}
      </td>
      <td className="td whitespace-nowrap text-xs">#{t.batchId} · row {t.rowNumber}</td>
    </tr>
  );
}

function TradeDrawer({ id, onClose }: { id: number; onClose: () => void }) {
  const detail = useApi(() => api.trade(id), [id]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  return (
    <div className="fixed inset-0 z-40 flex justify-end bg-navy-950/30" onClick={onClose}>
      <aside
        role="dialog"
        aria-modal="true"
        aria-label="Trade details"
        data-testid="trade-drawer"
        className="h-full w-full max-w-md overflow-y-auto bg-white p-5 shadow-xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mb-4 flex items-start justify-between">
          <h2 className="text-base font-semibold text-navy-950">Trade details</h2>
          <button className="btn px-2" onClick={onClose} aria-label="Close details" autoFocus>
            <X className="h-4 w-4" aria-hidden />
          </button>
        </div>
        {detail.loading && !detail.data && <Loading />}
        {detail.error && <ErrorState error={detail.error} onRetry={detail.reload} />}
        {detail.data && (
          <div className="space-y-4">
            <dl className="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
              {[
                ["Trade ID", detail.data.trade.tradeId ?? "—"],
                ["Status", <Badge key="s" value={detail.data.trade.status} />],
                ["Instrument", detail.data.trade.instrument ?? "—"],
                ["Side", <Badge key="d" value={detail.data.trade.side} />],
                ["Timestamp", fmtDateTime(detail.data.trade.timestamp)],
                ["Currency", detail.data.trade.currency ?? "—"],
                ["Price", fmtDec(detail.data.trade.price)],
                ["Quantity", fmtDec(detail.data.trade.quantity)],
                ["Notional", detail.data.trade.status === "VALID" ? fmtDec(detail.data.trade.notional) : "—"],
                ["Trader", detail.data.trade.traderId ?? "—"],
                ["Source", `Batch #${detail.data.trade.batchId}, row ${detail.data.trade.rowNumber}`],
              ].map(([k, v]) => (
                <div key={String(k)}>
                  <dt className="text-xs uppercase tracking-wide text-slate-500">{k}</dt>
                  <dd className="font-medium text-navy-950">{v}</dd>
                </div>
              ))}
            </dl>

            <div>
              <h3 className="mb-1 text-xs font-semibold uppercase tracking-wide text-slate-500">Original CSV row</h3>
              <pre className="overflow-x-auto rounded bg-slate-100 p-2 font-mono text-xs">{detail.data.rawRecord}</pre>
            </div>

            <div data-testid="drawer-errors">
              <h3 className="mb-1 text-xs font-semibold uppercase tracking-wide text-slate-500">
                Validation errors ({detail.data.errors.length})
              </h3>
              {detail.data.errors.length === 0 ? (
                <p className="text-sm text-emerald-700">No validation errors. This trade is included in analytics.</p>
              ) : (
                <ul className="space-y-2">
                  {detail.data.errors.map((e, i) => (
                    <li key={i} className="rounded border border-red-200 bg-red-50 px-3 py-2 text-sm">
                      <div className="flex items-center justify-between gap-2">
                        <span className="font-mono text-[11px] font-semibold text-red-700">{e.code}</span>
                        <span className="text-xs text-slate-500">field: {e.field}</span>
                      </div>
                      <p className="mt-0.5 text-slate-800">{e.message}</p>
                      {e.rejectedValue !== null && (
                        <p className="mt-0.5 font-mono text-xs text-slate-600">value: “{e.rejectedValue}”</p>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>
        )}
      </aside>
    </div>
  );
}
