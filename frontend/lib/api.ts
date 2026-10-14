import type {
  ApiErrorBody,
  BatchDetail,
  DailyVolume,
  ImportBatch,
  InstrumentStat,
  Overview,
  Page,
  Rules,
  RulesUpdate,
  SideStat,
  Trade,
  TradeDetail,
  ValidationErrorRow,
  ValidationStats,
} from "./types";

export class ApiClientError extends Error {
  status: number;
  code: string;
  details: { field: string; message: string }[];

  constructor(status: number, code: string, message: string, details: { field: string; message: string }[] = []) {
    super(message);
    this.name = "ApiClientError";
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

type Params = Record<string, string | number | boolean | null | undefined>;

export function qs(params: Params): string {
  const sp = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== "") sp.set(k, String(v));
  }
  const s = sp.toString();
  return s ? `?${s}` : "";
}

async function toError(res: Response): Promise<ApiClientError> {
  try {
    const body = (await res.json()) as ApiErrorBody;
    return new ApiClientError(res.status, body.code ?? "ERROR", body.message ?? res.statusText, body.details ?? []);
  } catch {
    return new ApiClientError(res.status, "ERROR", `Request failed (${res.status}).`);
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let res: Response;
  try {
    res = await fetch(path, { ...init, headers: { Accept: "application/json", ...(init?.headers ?? {}) } });
  } catch {
    throw new ApiClientError(0, "NETWORK_ERROR", "Cannot reach the TradeGuard backend. Is it running?");
  }
  if (!res.ok) throw await toError(res);
  return (await res.json()) as T;
}

/** Filters shared by every analytics/report/explorer call, so all pages select the same rows. */
export interface Filters {
  from?: string;
  to?: string;
  instrument?: string;
  side?: string;
  batchId?: number | string;
}

export const api = {
  overview: (f: Filters) => request<Overview>(`/api/analytics/overview${qs({ ...f })}`),
  dailyVolume: (f: Filters) => request<DailyVolume[]>(`/api/analytics/daily-volume${qs({ ...f })}`),
  instruments: (f: Filters) => request<InstrumentStat[]>(`/api/analytics/instruments${qs({ ...f })}`),
  sides: (f: Filters) => request<SideStat[]>(`/api/analytics/sides${qs({ ...f })}`),
  validation: (f: Filters) => request<ValidationStats>(`/api/analytics/validation${qs({ ...f })}`),

  imports: (page: number, size: number, status?: string) =>
    request<Page<ImportBatch>>(`/api/imports${qs({ page, size, status })}`),
  importDetail: (id: number | string) => request<BatchDetail>(`/api/imports/${id}`),
  importErrors: (id: number | string, page: number, size: number, code?: string) =>
    request<Page<ValidationErrorRow>>(`/api/imports/${id}/errors${qs({ page, size, code })}`),

  trades: (params: Params) => request<Page<Trade>>(`/api/trades${qs(params)}`),
  trade: (id: number) => request<TradeDetail>(`/api/trades/${id}`),
  tradeInstruments: () => request<string[]>(`/api/trades/instruments`),

  rules: () => request<Rules>(`/api/rules`),
  updateRules: (body: RulesUpdate) =>
    request<Rules>(`/api/rules`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    }),
};

/** Upload with progress events (fetch has no upload progress). */
export function uploadTrades(file: File, onProgress: (fraction: number) => void): Promise<BatchDetail> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open("POST", "/api/imports");
    xhr.responseType = "json";
    xhr.setRequestHeader("Accept", "application/json");
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) onProgress(e.loaded / e.total);
    };
    xhr.onerror = () =>
      reject(new ApiClientError(0, "NETWORK_ERROR", "Cannot reach the TradeGuard backend. Is it running?"));
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve(xhr.response as BatchDetail);
      } else {
        const body = (xhr.response ?? {}) as Partial<ApiErrorBody>;
        reject(
          new ApiClientError(
            xhr.status,
            body.code ?? "ERROR",
            body.message ?? `Upload failed (${xhr.status}).`,
            body.details ?? [],
          ),
        );
      }
    };
    const form = new FormData();
    form.append("file", file);
    xhr.send(form);
  });
}

/** URLs of CSV downloads (plain links, so the browser handles the file save). */
export const reportUrl = {
  validTrades: (f: Filters & { q?: string }) => `/api/reports/valid-trades${qs({ ...f })}`,
  validationErrors: (f: Filters & { code?: string }) => `/api/reports/validation-errors${qs({ ...f })}`,
  batchSummary: (id: number | string) => `/api/reports/batch-summary/${id}`,
};
