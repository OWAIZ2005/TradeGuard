"use client";

import { useState } from "react";
import { DailyVolumeChart, ErrorCategoryBars, SideDonut } from "@/components/charts";
import { Async, Badge, Card, CURRENCY_NOTE, DateRangeFilter, DownloadLink, Empty, Kpi, PageHeader } from "@/components/ui";
import { api, reportUrl } from "@/lib/api";
import { useDateRange } from "@/lib/filters";
import { fmtDateTime, fmtInt, fmtMoney, fmtPct, humanize } from "@/lib/format";
import { useApi } from "@/lib/useApi";

export default function AnalyticsPage() {
  const { from, to } = useDateRange();
  const [instrument, setInstrument] = useState("");
  const [side, setSide] = useState("");
  const [batchId, setBatchId] = useState("");
  const filters = { from, to, instrument, side, batchId };
  const key = [from, to, instrument, side, batchId];

  const instruments = useApi(() => api.tradeInstruments(), []);
  const batches = useApi(() => api.imports(0, 100), []);
  const overview = useApi(() => api.overview(filters), key);
  const daily = useApi(() => api.dailyVolume(filters), key);
  const sides = useApi(() => api.sides(filters), key);
  const byInstrument = useApi(() => api.instruments(filters), key);
  const validation = useApi(() => api.validation(filters), key);

  return (
    <>
      <PageHeader
        title="Analytics & Reports"
        subtitle="Valid-trade analytics and data-quality reporting. Filters apply to every number, chart and download on this page."
      />

      <Card className="mb-4">
        <div className="grid gap-3 md:grid-cols-4">
          <div className="md:col-span-4"><DateRangeFilter /></div>
          <div>
            <label className="label" htmlFor="a-instrument">Instrument</label>
            <select id="a-instrument" className="input" value={instrument} onChange={(e) => setInstrument(e.target.value)}>
              <option value="">All</option>
              {(instruments.data ?? []).map((i) => <option key={i}>{i}</option>)}
            </select>
          </div>
          <div>
            <label className="label" htmlFor="a-side">Side</label>
            <select id="a-side" className="input" value={side} onChange={(e) => setSide(e.target.value)}>
              <option value="">All</option>
              <option value="BUY">Buy</option>
              <option value="SELL">Sell</option>
            </select>
          </div>
          <div className="md:col-span-2">
            <label className="label" htmlFor="a-batch">Import batch</label>
            <select id="a-batch" className="input" value={batchId} onChange={(e) => setBatchId(e.target.value)}>
              <option value="">All batches</option>
              {(batches.data?.content ?? []).map((b) => <option key={b.id} value={b.id}>#{b.id} · {b.filename}</option>)}
            </select>
          </div>
        </div>
        <div className="mt-3 flex flex-wrap gap-2 border-t border-slate-100 pt-3" data-testid="report-downloads">
          <span className="self-center text-xs font-medium uppercase tracking-wide text-slate-500">Download (current filters)</span>
          <DownloadLink href={reportUrl.validTrades(filters)} testId="report-valid-trades">Valid trades CSV</DownloadLink>
          <DownloadLink href={reportUrl.validationErrors(filters)} testId="report-validation-errors">Invalid rows &amp; errors CSV</DownloadLink>
          {batchId && <DownloadLink href={reportUrl.batchSummary(batchId)} testId="report-batch-summary">Batch summary CSV</DownloadLink>}
        </div>
      </Card>

      <Async state={overview} isEmpty={(o) => o.totalRows === 0} empty={<Card><Empty title="No data for these filters" hint="Import a file or widen the filters." /></Card>}>
        {(o) => (
          <div className="space-y-4">
            <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
              <Kpi label="Valid trades" value={fmtInt(o.validTrades)} tone="good" testId="an-valid" />
              <Kpi label="Total quantity" value={fmtInt(o.totalQuantity)} hint="valid trades" />
              <Kpi label="Rows rejected" value={fmtInt(o.invalidTrades)} tone={o.invalidTrades ? "bad" : "default"} />
              <Kpi label="Error rate" value={fmtPct(o.validationErrorRatePercent)} />
            </div>

            <Card title="Notional value by currency" subtitle="price × quantity, valid trades only" testId="notional-card">
              <div className="flex flex-wrap gap-6">
                {o.notionalByCurrency.map((c) => (
                  <div key={c.currency}>
                    <div className="text-xs uppercase text-slate-500">{c.currency}</div>
                    <div className="text-xl font-semibold tabular-nums">{fmtMoney(c.amount, c.currency)}</div>
                  </div>
                ))}
              </div>
              <p className="mt-3 rounded border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-900" data-testid="currency-note">
                {CURRENCY_NOTE}
              </p>
            </Card>
          </div>
        )}
      </Async>

      <div className="mt-4 grid gap-4 lg:grid-cols-3">
        <Card title="Daily activity" subtitle="Trades per UTC day with cumulative quantity (SQL window function)" className="lg:col-span-2">
          <Async state={daily} isEmpty={(d) => d.length === 0} empty={<Empty title="No valid trades" />}>
            {(d) => <DailyVolumeChart data={d} showCumulative />}
          </Async>
        </Card>
        <Card title="Buy vs sell">
          <Async state={sides} isEmpty={(d) => d.length === 0} empty={<Empty title="No valid trades" />}>
            {(d) => <SideDonut data={d} />}
          </Async>
        </Card>
      </div>

      <Card title="Instrument breakdown" subtitle="Valid trades grouped by instrument (SQL GROUP BY)" className="mt-4" testId="instrument-table-card">
        <Async state={byInstrument} isEmpty={(d) => d.length === 0} empty={<Empty title="No valid trades" />}>
          {(d) => (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[640px]" data-testid="instrument-table">
                <thead>
                  <tr>
                    <th className="th">Instrument</th>
                    <th className="th text-right">Trades</th>
                    <th className="th text-right">Buys</th>
                    <th className="th text-right">Sells</th>
                    <th className="th text-right">Quantity</th>
                    <th className="th">Notional (per currency)</th>
                  </tr>
                </thead>
                <tbody>
                  {d.map((r) => (
                    <tr key={r.instrument} className="hover:bg-slate-50">
                      <td className="td font-medium">{r.instrument}</td>
                      <td className="td text-right tabular-nums">{fmtInt(r.tradeCount)}</td>
                      <td className="td text-right tabular-nums">{fmtInt(r.buyCount)}</td>
                      <td className="td text-right tabular-nums">{fmtInt(r.sellCount)}</td>
                      <td className="td text-right tabular-nums">{fmtInt(r.quantity)}</td>
                      <td className="td tabular-nums">{r.notionalByCurrency.map((c) => fmtMoney(c.amount, c.currency)).join(" · ")}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Async>
      </Card>

      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <Card title="Validation error distribution" subtitle="By category" testId="validation-distribution">
          <Async state={validation} isEmpty={(v) => v.errorsByCode.length === 0} empty={<Empty title="No validation errors" />}>
            {(v) => (
              <>
                <ErrorCategoryBars data={v.errorsByCategory} />
                <table className="mt-3 w-full">
                  <thead>
                    <tr>
                      <th className="th">Error code</th>
                      <th className="th text-right">Occurrences</th>
                      <th className="th text-right">Rows affected</th>
                    </tr>
                  </thead>
                  <tbody>
                    {v.errorsByCode.map((e) => (
                      <tr key={e.code}>
                        <td className="td">{humanize(e.code)} <span className="font-mono text-[10px] text-slate-400">{e.code}</span></td>
                        <td className="td text-right tabular-nums">{fmtInt(e.occurrences)}</td>
                        <td className="td text-right tabular-nums">{fmtInt(e.affectedRows)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </>
            )}
          </Async>
        </Card>

        <Card title="Import batch quality" subtitle="Rows in the selected range, by batch" testId="batch-quality">
          <Async state={validation} isEmpty={(v) => v.batchQuality.length === 0} empty={<Empty title="No batches" />}>
            {(v) => (
              <table className="w-full">
                <thead>
                  <tr>
                    <th className="th">Batch</th>
                    <th className="th text-right">Rows</th>
                    <th className="th text-right">Valid</th>
                    <th className="th text-right">Invalid</th>
                    <th className="th text-right">Error rate</th>
                  </tr>
                </thead>
                <tbody>
                  {v.batchQuality.map((b) => (
                    <tr key={b.batchId}>
                      <td className="td">
                        #{b.batchId} · {b.filename}
                        <div className="text-[11px] text-slate-400">{fmtDateTime(b.uploadedAt)}</div>
                      </td>
                      <td className="td text-right tabular-nums">{fmtInt(b.totalRows)}</td>
                      <td className="td text-right tabular-nums">{fmtInt(b.validRows)}</td>
                      <td className="td text-right tabular-nums">{fmtInt(b.invalidRows)}</td>
                      <td className="td text-right tabular-nums">
                        {b.errorRatePercent === 0 ? <Badge value="VALID" /> : fmtPct(b.errorRatePercent)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </Async>
        </Card>
      </div>
    </>
  );
}
