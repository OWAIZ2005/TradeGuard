"use client";

import { AlertTriangle, FileSpreadsheet, UploadCloud } from "lucide-react";
import { useRef, useState } from "react";
import { BatchResult } from "@/components/BatchResult";
import { Card, PageHeader } from "@/components/ui";
import { ApiClientError, api, uploadTrades } from "@/lib/api";
import { fmtBytes } from "@/lib/format";
import type { BatchDetail } from "@/lib/types";
import { useApi } from "@/lib/useApi";

const COLUMNS = ["trade_id", "instrument", "trade_timestamp", "side", "price", "quantity", "currency", "trader_id"];

export default function ImportPage() {
  const rules = useApi(() => api.rules(), []);
  const inputRef = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [dragging, setDragging] = useState(false);
  const [progress, setProgress] = useState<number | null>(null);
  const [processing, setProcessing] = useState(false);
  const [error, setError] = useState<{ message: string; code?: string } | null>(null);
  const [result, setResult] = useState<BatchDetail | null>(null);

  const maxMb = rules.data?.maxUploadSizeMb ?? 10;

  function choose(f: File | null) {
    setResult(null);
    setError(null);
    setProgress(null);
    if (!f) {
      setFile(null);
      return;
    }
    if (!f.name.toLowerCase().endsWith(".csv")) {
      setFile(null);
      setError({ message: "Only .csv files are accepted.", code: "UNSUPPORTED_FILE_TYPE" });
      return;
    }
    if (f.size === 0) {
      setFile(null);
      setError({ message: "The selected file is empty.", code: "EMPTY_FILE" });
      return;
    }
    if (f.size > maxMb * 1024 * 1024) {
      setFile(null);
      setError({ message: `File is ${fmtBytes(f.size)}; the maximum allowed is ${maxMb} MB.`, code: "FILE_TOO_LARGE" });
      return;
    }
    setFile(f);
  }

  async function submit() {
    if (!file) return;
    setError(null);
    setResult(null);
    setProgress(0);
    setProcessing(false);
    try {
      const detail = await uploadTrades(file, (p) => {
        setProgress(p);
        if (p >= 1) setProcessing(true);
      });
      setResult(detail);
    } catch (e) {
      const err = e instanceof ApiClientError ? e : null;
      setError({ message: err?.message ?? "Upload failed.", code: err?.code });
    } finally {
      setProcessing(false);
      setProgress(null);
    }
  }

  const busy = progress !== null;

  return (
    <>
      <PageHeader
        title="Import Data"
        subtitle="Upload a trade CSV. Every row is validated; invalid rows are kept for inspection and never reach analytics."
      />

      <div className="grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2" title="Upload CSV">
          <div
            data-testid="dropzone"
            onDragOver={(e) => {
              e.preventDefault();
              setDragging(true);
            }}
            onDragLeave={() => setDragging(false)}
            onDrop={(e) => {
              e.preventDefault();
              setDragging(false);
              choose(e.dataTransfer.files?.[0] ?? null);
            }}
            className={`flex flex-col items-center gap-2 rounded-lg border-2 border-dashed px-6 py-10 text-center ${
              dragging ? "border-brand-500 bg-brand-50" : "border-slate-300 bg-slate-50"
            }`}
          >
            <UploadCloud className="h-8 w-8 text-brand-600" aria-hidden />
            <p className="font-medium text-navy-950">Drag &amp; drop a CSV file here</p>
            <p className="text-xs text-slate-500">or</p>
            <button className="btn" type="button" onClick={() => inputRef.current?.click()} disabled={busy}>
              Choose file…
            </button>
            <input
              ref={inputRef}
              type="file"
              accept=".csv,text/csv"
              className="sr-only"
              aria-label="CSV file"
              data-testid="file-input"
              onChange={(e) => {
                choose(e.target.files?.[0] ?? null);
                e.target.value = "";
              }}
            />
            <p className="text-xs text-slate-500">.csv · up to {maxMb} MB (configurable on the Validation Rules page)</p>
          </div>

          {file && (
            <div className="mt-3 flex items-center justify-between gap-3 rounded-md border border-slate-200 px-3 py-2" data-testid="selected-file">
              <div className="flex items-center gap-2">
                <FileSpreadsheet className="h-4 w-4 text-slate-500" aria-hidden />
                <span className="font-medium">{file.name}</span>
                <span className="text-xs text-slate-500">{fmtBytes(file.size)}</span>
              </div>
              <button className="btn-primary" onClick={submit} disabled={busy} data-testid="upload-button">
                {busy ? "Uploading…" : "Upload & validate"}
              </button>
            </div>
          )}

          {busy && (
            <div className="mt-3" role="status" aria-live="polite" data-testid="upload-progress">
              <div className="mb-1 flex justify-between text-xs text-slate-600">
                <span>{processing ? "Validating and storing rows…" : "Uploading…"}</span>
                <span>{processing ? "" : `${Math.round((progress ?? 0) * 100)}%`}</span>
              </div>
              <div className="h-2 overflow-hidden rounded bg-slate-200">
                <div
                  className={`h-full bg-brand-600 transition-all ${processing ? "animate-pulse" : ""}`}
                  style={{ width: `${processing ? 100 : Math.round((progress ?? 0) * 100)}%` }}
                />
              </div>
            </div>
          )}

          {error && (
            <div role="alert" className="mt-3 flex gap-2 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800" data-testid="upload-error">
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
              <div>
                <div className="font-medium">Import rejected{error.code ? ` · ${error.code}` : ""}</div>
                <div>{error.message}</div>
              </div>
            </div>
          )}
        </Card>

        <Card title="File format">
          <p className="mb-2 text-xs text-slate-500">Required columns (header row, any order, case-insensitive):</p>
          <ul className="grid grid-cols-2 gap-1 font-mono text-xs">
            {COLUMNS.map((c) => (
              <li key={c} className="rounded bg-slate-100 px-2 py-1">{c}</li>
            ))}
          </ul>
          <ul className="mt-3 list-disc space-y-1 pl-4 text-xs text-slate-600">
            <li>Timestamps: ISO-8601 with offset, e.g. <span className="font-mono">2025-03-14T14:30:00Z</span></li>
            <li>Side: BUY or SELL · price and quantity: plain decimals</li>
            <li>Trade IDs must be unique; an ID already imported is never overwritten.</li>
          </ul>
          <a className="btn mt-3" href="/sample_trades.csv" download data-testid="sample-download">
            Download synthetic sample CSV
          </a>
        </Card>
      </div>

      {result && (
        <div className="mt-4">
          <BatchResult detail={result} />
        </div>
      )}
    </>
  );
}
