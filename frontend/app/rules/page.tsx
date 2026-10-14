"use client";

import { CheckCircle2 } from "lucide-react";
import { useEffect, useState } from "react";
import { Async, Card, PageHeader } from "@/components/ui";
import { ApiClientError, api } from "@/lib/api";
import { fmtDateTime } from "@/lib/format";
import type { Rules } from "@/lib/types";
import { useApi } from "@/lib/useApi";

const split = (s: string) => s.split(/[\s,;]+/).map((x) => x.trim()).filter(Boolean);

export default function RulesPage() {
  const rules = useApi(() => api.rules(), []);
  return (
    <>
      <PageHeader
        title="Validation Rules"
        subtitle="Thresholds applied to every new import. Saved changes affect later imports only; stored trades are never re-validated."
      />
      <Async state={rules}>{(r) => <RulesForm key={r.updatedAt ?? "initial"} initial={r} />}</Async>
    </>
  );
}

function RulesForm({ initial }: { initial: Rules }) {
  const [maxMb, setMaxMb] = useState(String(initial.maxUploadSizeMb));
  const [currencies, setCurrencies] = useState(initial.allowedCurrencies.join(", "));
  const [instruments, setInstruments] = useState(initial.allowedInstruments.join(", "));
  const [maxQty, setMaxQty] = useState(String(initial.maxTradeQuantity));
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState<Rules | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);

  useEffect(() => setSaved(null), [maxMb, currencies, instruments, maxQty]);

  async function save(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true);
    setFieldErrors({});
    setFormError(null);
    try {
      const updated = await api.updateRules({
        maxUploadSizeMb: Number(maxMb),
        allowedCurrencies: split(currencies),
        allowedInstruments: split(instruments),
        maxTradeQuantity: Number(maxQty),
      });
      setSaved(updated);
    } catch (err) {
      if (err instanceof ApiClientError) {
        const map: Record<string, string> = {};
        for (const d of err.details) map[d.field.replace(/\[\d+\]$/, "")] = d.message;
        setFieldErrors(map);
        setFormError(err.details.length === 0 ? err.message : "Some values were rejected by the server. Nothing was saved.");
      } else {
        setFormError("Could not save the rules.");
      }
    } finally {
      setSaving(false);
    }
  }

  const err = (k: string) =>
    fieldErrors[k] ? <p role="alert" className="mt-1 text-xs text-red-700">{fieldErrors[k]}</p> : null;

  return (
    <form noValidate onSubmit={save} aria-label="Validation rules" className="grid gap-4 lg:grid-cols-3">
      <Card className="lg:col-span-2" title="Thresholds">
        <div className="grid gap-4 md:grid-cols-2">
          <div>
            <label className="label" htmlFor="r-size">Maximum upload size (MB)</label>
            <input id="r-size" className="input" type="number" min={1} max={initial.hardUploadLimitMb} step={1} value={maxMb} onChange={(e) => setMaxMb(e.target.value)} />
            <p className="mt-1 text-xs text-slate-500">1–{initial.hardUploadLimitMb} MB (server hard limit).</p>
            {err("maxUploadSizeMb")}
          </div>
          <div>
            <label className="label" htmlFor="r-qty">Maximum trade quantity</label>
            <input id="r-qty" className="input" type="number" min={0} step="any" value={maxQty} onChange={(e) => setMaxQty(e.target.value)} />
            <p className="mt-1 text-xs text-slate-500">Rows above this are rejected (QUANTITY_EXCEEDS_MAX).</p>
            {err("maxTradeQuantity")}
          </div>
          <div className="md:col-span-2">
            <label className="label" htmlFor="r-ccy">Allowed currencies</label>
            <textarea id="r-ccy" className="input font-mono" rows={2} value={currencies} onChange={(e) => setCurrencies(e.target.value)} />
            <p className="mt-1 text-xs text-slate-500">3-letter ISO codes separated by commas or spaces.</p>
            {err("allowedCurrencies")}
          </div>
          <div className="md:col-span-2">
            <label className="label" htmlFor="r-inst">Allowed instruments</label>
            <textarea id="r-inst" className="input font-mono" rows={3} value={instruments} onChange={(e) => setInstruments(e.target.value)} />
            <p className="mt-1 text-xs text-slate-500">Symbols separated by commas or spaces. Instrument symbols here are illustrative.</p>
            {err("allowedInstruments")}
          </div>
        </div>

        <div className="mt-4 flex flex-wrap items-center gap-3">
          <button type="submit" className="btn-primary" disabled={saving} data-testid="save-rules">
            {saving ? "Saving…" : "Save rules"}
          </button>
          {saved && (
            <span role="status" className="inline-flex items-center gap-1 text-sm text-emerald-700" data-testid="rules-saved">
              <CheckCircle2 className="h-4 w-4" aria-hidden /> Saved. Applies to the next import.
            </span>
          )}
          {formError && <span role="alert" className="text-sm text-red-700" data-testid="rules-error">{formError}</span>}
        </div>
      </Card>

      <Card title="How rule changes apply">
        <ul className="list-disc space-y-2 pl-4 text-sm text-slate-700">
          <li>Rules are validated on the server; invalid values are rejected and nothing is saved.</li>
          <li>Each import reads the current rules when it starts and stores a snapshot with the batch.</li>
          <li>Already-imported trades keep their status; they are not re-validated.</li>
          <li>The upload limit is enforced before any parsing happens.</li>
        </ul>
        <p className="mt-3 text-xs text-slate-500">Last updated: {fmtDateTime(initial.updatedAt)}</p>
      </Card>
    </form>
  );
}
