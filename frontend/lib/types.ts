// Mirrors the backend DTOs (see backend/src/main/java/com/tradeguard/web/dto).

export type BatchStatus = "PROCESSING" | "COMPLETED" | "FAILED";

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ImportBatch {
  id: number;
  filename: string;
  fileSizeBytes: number;
  status: BatchStatus;
  totalRows: number;
  validRows: number;
  invalidRows: number;
  duplicateRows: number;
  errorCount: number;
  errorRatePercent: number;
  uploadedAt: string;
  completedAt: string | null;
  failureReason: string | null;
}

export interface ErrorBreakdownItem {
  code: string;
  category: string;
  occurrences: number;
  affectedRows: number;
}

export interface Summary {
  generator: string;
  headline: string;
  points: string[];
}

export interface BatchDetail {
  batch: ImportBatch;
  errorBreakdown: ErrorBreakdownItem[];
  summary: Summary;
}

export interface ValidationErrorRow {
  tradePk: number;
  batchId: number;
  rowNumber: number;
  tradeId: string | null;
  field: string;
  code: string;
  category: string;
  message: string;
  rejectedValue: string | null;
}

export interface Trade {
  id: number;
  batchId: number;
  batchFilename: string;
  rowNumber: number;
  tradeId: string | null;
  instrument: string | null;
  timestamp: string | null;
  side: string | null;
  price: number | null;
  quantity: number | null;
  currency: string | null;
  traderId: string | null;
  notional: number | null;
  status: "VALID" | "INVALID";
  errorCount: number;
}

export interface TradeDetail {
  trade: Trade;
  rawRecord: string;
  errors: ValidationErrorRow[];
}

export interface CurrencyAmount {
  currency: string;
  amount: number;
}

export interface Overview {
  totalRows: number;
  validTrades: number;
  invalidTrades: number;
  duplicateTrades: number;
  validationErrorRatePercent: number;
  totalQuantity: number;
  notionalByCurrency: CurrencyAmount[];
  currencyNote: string;
}

export interface DailyVolume {
  date: string;
  tradeCount: number;
  quantity: number;
  buyCount: number;
  sellCount: number;
  cumulativeQuantity: number;
}

export interface InstrumentStat {
  instrument: string;
  tradeCount: number;
  quantity: number;
  buyCount: number;
  sellCount: number;
  notionalByCurrency: CurrencyAmount[];
}

export interface SideStat {
  side: "BUY" | "SELL";
  tradeCount: number;
  quantity: number;
}

export interface BatchQuality {
  batchId: number;
  filename: string;
  uploadedAt: string;
  totalRows: number;
  validRows: number;
  invalidRows: number;
  errorRatePercent: number;
}

export interface ValidationStats {
  totalRows: number;
  validRows: number;
  invalidRows: number;
  duplicateRows: number;
  errorRatePercent: number;
  errorsByCode: ErrorBreakdownItem[];
  errorsByCategory: { category: string; occurrences: number }[];
  batchQuality: BatchQuality[];
}

export interface Rules {
  maxUploadSizeMb: number;
  hardUploadLimitMb: number;
  allowedCurrencies: string[];
  allowedInstruments: string[];
  maxTradeQuantity: number;
  updatedAt: string | null;
}

export interface RulesUpdate {
  maxUploadSizeMb: number;
  allowedCurrencies: string[];
  allowedInstruments: string[];
  maxTradeQuantity: number;
}

export interface ApiErrorBody {
  status: number;
  code: string;
  message: string;
  details?: { field: string; message: string }[];
}
