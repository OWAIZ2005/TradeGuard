"use client";

import Link from "next/link";
import { DailyVolumeChart, ErrorCategoryBars, InstrumentBars, SideDonut } from "@/components/charts";
import { Async, Badge, Card, DateRangeFilter, Empty, Kpi, PageHeader, CURRENCY_NOTE } from "@/components/ui";
import { ApiClientError, api } from "@/lib/api";
import { useDateRange } from "@/lib/filters";
import { fmtDateTime, fmtDec, fmtInt, fmtMoney, fmtPct } from "@/lib/format";
import { useApi } from "@/lib/useApi";

export default function OverviewPage() {
  const { from, to } = useDateRange();
  const filters = { from, to };
  const rangeInvalid = from !== "" && to !== "" && from > to;
  const key = [from, to];

  const overview = useApi(() => (rangeInvalid ? Promise.reject(new ApiClientError(400, "BAD_RANGE", "The date range is invalid: “From” is after “To”.")) : api.overview(filters)), key);
  const daily = useApi(() => api.dailyVolume(filters), key);
  const sides = useApi(() => api.sides(filters), key);
  const instruments = useApi(() => api.instruments(filters), key);
  const validation = useApi(() => api.validation(filters), key);
  const recent = useApi(() => api.imports(0, 5), []);

  return (
    <>
      <PageHeader
        title="Overview"
        subtitle="Data quality and activity computed from imported trade files. Analytics use VALID trades only."
        actions={<DateRangeFilter />}
      />

      <Async
        state={overview}
        isEmpty={(o) => o.totalRows === 0}
        empty={
          <Card>
            <Empty
              title="No trades imported yet"
              hint={
                <>
                  Upload a CSV on the <Link className="text-brand-700 underline" href="/import">Import Data</Link> page
                  (a synthetic sample file is available there) to populate the dashboard. If a date range is set, try
                  “All time”.
                </>
              }
            />
          </Card>
        }
      >
        {(o) => (
          <div className="space-y-4">
            <div className="grid grid-cols-2 gap-3 md:grid-cols-4 xl:grid-cols-7">
              <Kpi label="Trades imported" value={fmtInt(o.totalRows)} hint="all rows incl. rejected" testId="kpi-total" />
              <Kpi label="Valid trades" value={fmtInt(o.validTrades)} tone="good" testId="kpi-valid" />
              <Kpi label="Invalid trades" value={fmtInt(o.invalidTrades)} tone={o.invalidTrades ? "bad" : "default"} testId="kpi-invalid" />
              <Kpi label="Duplicates detected" value={fmtInt(o.duplicateTrades)} tone={o.duplicateTrades ? "warn" : "default"} testId="kpi-duplicates" />
              <Kpi label="Validation error rate" value={fmtPct(o.validationErrorRatePercent)} testId="kpi-error-rate" />
              <Kpi label="Total quantity" value={fmtDec(o.totalQuantity).replace(/\.00$/, "")} hint="valid trades, contract units" testId="kpi-quantity" />
              <Kpi
                label="Notional (valid)"
                testId="kpi-notional"
                value={
                  <ul className="text-base leading-snug">
                    {o.notionalByCurrency.map((c) => (
                      <li key={c.currency}>{fmtMoney(c.amount, c.currency)}</li>
                    ))}
                  </ul>
                }
                hint="per currency, not converted"
              />
            </div>
            <p className="text-xs text-slate-500">{CURRENCY_NOTE}</p>
          </div>
        )}
      </Async>

      <div className="mt-4 grid gap-4 lg:grid-cols-3">
        <Card title="Daily trade volume" subtitle="Valid trades per UTC day, split by side" className="lg:col-span-2" testId="chart-daily">
          <Async state={daily} isEmpty={(d) => d.length === 0} empty={<Empty title="No valid trades in this range" />}>
            {(d) => <DailyVolumeChart data={d} />}
          </Async>
        </Card>
        <Card title="Buy vs sell" subtitle="Valid trades" testId="chart-sides">
          <Async state={sides} isEmpty={(d) => d.length === 0} empty={<Empty title="No valid trades" />}>
            {(d) => <SideDonut data={d} />}
          </Async>
        </Card>
        <Card title="Instrument activity" subtitle="Valid trades by instrument" testId="chart-instruments">
          <Async state={instruments} isEmpty={(d) => d.length === 0} empty={<Empty title="No valid trades" />}>
            {(d) => <InstrumentBars data={d} />}
          </Async>
        </Card>
        <Card title="Validation errors by category" subtitle="All imported rows in range" className="lg:col-span-2" testId="chart-errors">
          <Async
            state={validation}
            isEmpty={(v) => v.errorsByCategory.length === 0}
            empty={<Empty title="No validation errors" hint="Every row in this range passed validation." />}
          >
            {(v) => <ErrorCategoryBars data={v.errorsByCategory} />}
          </Async>
        </Card>
      </div>

      <Card
        title="Recent imports"
        className="mt-4"
        testId="recent-imports"
        actions={<Link className="text-xs text-brand-700 underline" href="/imports">View all</Link>}
      >
        <Async state={recent} isEmpty={(p) => p.content.length === 0} empty={<Empty title="No imports yet" />}>
          {(p) => (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[640px]">
                <thead>
                  <tr>
                    <th className="th">Batch</th>
                    <th className="th">File</th>
                    <th className="th">Uploaded</th>
                    <th className="th text-right">Rows</th>
                    <th className="th text-right">Valid</th>
                    <th className="th text-right">Invalid</th>
                    <th className="th">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {p.content.map((b) => (
                    <tr key={b.id} className="hover:bg-slate-50">
                      <td className="td"><Link className="text-brand-700 underline" href={`/imports/${b.id}`}>#{b.id}</Link></td>
                      <td className="td">{b.filename}</td>
                      <td className="td whitespace-nowrap">{fmtDateTime(b.uploadedAt)}</td>
                      <td className="td text-right tabular-nums">{fmtInt(b.totalRows)}</td>
                      <td className="td text-right tabular-nums">{fmtInt(b.validRows)}</td>
                      <td className="td text-right tabular-nums">{fmtInt(b.invalidRows)}</td>
                      <td className="td"><Badge value={b.status} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Async>
      </Card>
    </>
  );
}
