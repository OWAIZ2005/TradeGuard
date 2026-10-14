"use client";

import { Sparkles } from "lucide-react";
import { useState } from "react";
import { api, reportUrl } from "@/lib/api";
import { fmtBytes, fmtDateTime, fmtInt, fmtPct, humanize } from "@/lib/format";
import type { BatchDetail } from "@/lib/types";
import { useApi } from "@/lib/useApi";
import { Async, Badge, Card, DownloadLink, Empty, Kpi, Pagination } from "./ui";

const PAGE_SIZE = 20;

export function BatchResult({ detail }: { detail: BatchDetail }) {
  const { batch, errorBreakdown, summary } = detail;
  const [code, setCode] = useState<string>("");
  const [page, setPage] = useState(0);
  const errors = useApi(() => api.importErrors(batch.id, page, PAGE_SIZE, code || undefined), [batch.id, page, code]);

  return (
    <div className="space-y-4" data-testid="batch-result">
      <Card
        title={`Batch #${batch.id} · ${batch.filename}`}
        subtitle={`${fmtBytes(batch.fileSizeBytes)} · uploaded ${fmtDateTime(batch.uploadedAt)}`}
        actions={<Badge value={batch.status} />}
      >
        {batch.failureReason && (
          <p role="alert" className="mb-3 rounded border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">
            {batch.failureReason}
          </p>
        )}
        <div className="grid grid-cols-2 gap-3 md:grid-cols-5">
          <Kpi label="Total rows" value={fmtInt(batch.totalRows)} testId="batch-total" />
          <Kpi label="Valid" value={fmtInt(batch.validRows)} tone="good" testId="batch-valid" />
          <Kpi label="Invalid" value={fmtInt(batch.invalidRows)} tone={batch.invalidRows ? "bad" : "default"} testId="batch-invalid" />
          <Kpi label="Duplicates" value={fmtInt(batch.duplicateRows)} tone={batch.duplicateRows ? "warn" : "default"} testId="batch-duplicates" />
          <Kpi label="Error rate" value={fmtPct(batch.errorRatePercent)} hint={`${fmtInt(batch.errorCount)} errors logged`} />
        </div>

        <div className="mt-4 rounded-md border border-brand-100 bg-brand-50 px-4 py-3" data-testid="batch-summary">
          <div className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-brand-700">
            <Sparkles className="h-3.5 w-3.5" aria-hidden /> Plain-English summary
            <span className="rounded bg-white px-1.5 py-0.5 text-[10px] font-medium normal-case text-slate-500 ring-1 ring-inset ring-slate-200">
              {summary.generator === "rule-based" ? "rule-based · no LLM" : summary.generator}
            </span>
          </div>
          <p className="mt-1.5 font-medium text-navy-950">{summary.headline}</p>
          {summary.points.length > 0 && (
            <ul className="mt-1.5 list-disc space-y-0.5 pl-5 text-sm text-slate-700">
              {summary.points.map((p) => (
                <li key={p}>{p}</li>
              ))}
            </ul>
          )}
        </div>

        <div className="mt-4 flex flex-wrap gap-2">
          <DownloadLink href={reportUrl.validationErrors({ batchId: batch.id })} testId="download-batch-errors">
            Invalid rows &amp; errors (CSV)
          </DownloadLink>
          <DownloadLink href={reportUrl.validTrades({ batchId: batch.id })} testId="download-batch-valid">
            Valid trades (CSV)
          </DownloadLink>
          <DownloadLink href={reportUrl.batchSummary(batch.id)} testId="download-batch-summary">
            Batch summary (CSV)
          </DownloadLink>
        </div>
      </Card>

      <Card
        title="Validation errors"
        subtitle="Every problem found, with the original row number (header = row 1)."
        testId="errors-card"
      >
        {errorBreakdown.length > 0 && (
          <div className="mb-3 flex flex-wrap gap-1.5" role="group" aria-label="Filter errors by code">
            <button
              className={`rounded-full border px-2.5 py-0.5 text-xs ${code === "" ? "border-brand-600 bg-brand-600 text-white" : "border-slate-300 bg-white"}`}
              onClick={() => {
                setCode("");
                setPage(0);
              }}
            >
              All ({fmtInt(batch.errorCount)})
            </button>
            {errorBreakdown.map((b) => (
              <button
                key={b.code}
                aria-pressed={code === b.code}
                className={`rounded-full border px-2.5 py-0.5 text-xs ${code === b.code ? "border-brand-600 bg-brand-600 text-white" : "border-slate-300 bg-white"}`}
                onClick={() => {
                  setCode(b.code);
                  setPage(0);
                }}
              >
                {humanize(b.code)} ({fmtInt(b.occurrences)})
              </button>
            ))}
          </div>
        )}
        <Async
          state={errors}
          isEmpty={(p) => p.totalElements === 0}
          empty={<Empty title="No validation errors" hint="Every row in this batch passed validation." />}
        >
          {(p) => (
            <>
              <div className="overflow-x-auto">
                <table className="w-full min-w-[720px] text-left" data-testid="errors-table">
                  <thead>
                    <tr>
                      <th className="th">Row</th>
                      <th className="th">Trade ID</th>
                      <th className="th">Field</th>
                      <th className="th">Code</th>
                      <th className="th">Explanation</th>
                      <th className="th">Rejected value</th>
                    </tr>
                  </thead>
                  <tbody>
                    {p.content.map((e, i) => (
                      <tr key={`${e.tradePk}-${e.code}-${i}`} className="hover:bg-slate-50">
                        <td className="td tabular-nums">{e.rowNumber}</td>
                        <td className="td font-mono text-xs">{e.tradeId ?? "—"}</td>
                        <td className="td font-mono text-xs">{e.field}</td>
                        <td className="td">
                          <span className="rounded bg-red-50 px-1.5 py-0.5 font-mono text-[11px] text-red-700">{e.code}</span>
                        </td>
                        <td className="td">{e.message}</td>
                        <td className="td max-w-[200px] truncate font-mono text-xs" title={e.rejectedValue ?? ""}>
                          {e.rejectedValue === null || e.rejectedValue === "" ? <span className="text-slate-400">(empty)</span> : e.rejectedValue}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <Pagination page={p.page} totalPages={p.totalPages} totalElements={p.totalElements} size={p.size} onPage={setPage} />
            </>
          )}
        </Async>
      </Card>
    </div>
  );
}
