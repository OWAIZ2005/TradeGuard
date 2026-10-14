"use client";

import Link from "next/link";
import { useState } from "react";
import { Async, Badge, Card, Empty, PageHeader, Pagination } from "@/components/ui";
import { api } from "@/lib/api";
import { fmtBytes, fmtDateTime, fmtInt, fmtPct } from "@/lib/format";
import { useApi } from "@/lib/useApi";

const SIZE = 15;

export default function ImportHistoryPage() {
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState("");
  const list = useApi(() => api.imports(page, SIZE, status || undefined), [page, status]);

  return (
    <>
      <PageHeader
        title="Import History"
        subtitle="Every uploaded batch with its processing status and data-quality counts."
        actions={
          <div>
            <label className="label" htmlFor="status-filter">Status</label>
            <select
              id="status-filter"
              className="input w-40"
              value={status}
              onChange={(e) => {
                setStatus(e.target.value);
                setPage(0);
              }}
            >
              <option value="">All</option>
              <option value="COMPLETED">Completed</option>
              <option value="FAILED">Failed</option>
              <option value="PROCESSING">Processing</option>
            </select>
          </div>
        }
      />
      <Card testId="history-card">
        <Async
          state={list}
          isEmpty={(p) => p.totalElements === 0}
          empty={<Empty title="No imports found" hint={<>Upload a file on the <Link className="text-brand-700 underline" href="/import">Import Data</Link> page.</>} />}
        >
          {(p) => (
            <>
              <div className="overflow-x-auto">
                <table className="w-full min-w-[900px]" data-testid="history-table">
                  <thead>
                    <tr>
                      <th className="th">Batch ID</th>
                      <th className="th">Filename</th>
                      <th className="th">Uploaded</th>
                      <th className="th text-right">Total rows</th>
                      <th className="th text-right">Valid</th>
                      <th className="th text-right">Invalid</th>
                      <th className="th text-right">Errors</th>
                      <th className="th text-right">Error rate</th>
                      <th className="th">Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {p.content.map((b) => (
                      <tr key={b.id} className="hover:bg-slate-50" data-testid="history-row">
                        <td className="td">
                          <Link className="font-medium text-brand-700 underline" href={`/imports/${b.id}`}>#{b.id}</Link>
                        </td>
                        <td className="td">
                          {b.filename} <span className="text-xs text-slate-400">{fmtBytes(b.fileSizeBytes)}</span>
                        </td>
                        <td className="td whitespace-nowrap">{fmtDateTime(b.uploadedAt)}</td>
                        <td className="td text-right tabular-nums">{fmtInt(b.totalRows)}</td>
                        <td className="td text-right tabular-nums">{fmtInt(b.validRows)}</td>
                        <td className="td text-right tabular-nums">{fmtInt(b.invalidRows)}</td>
                        <td className="td text-right tabular-nums">{fmtInt(b.errorCount)}</td>
                        <td className="td text-right tabular-nums">{fmtPct(b.errorRatePercent)}</td>
                        <td className="td"><Badge value={b.status} /></td>
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
    </>
  );
}
