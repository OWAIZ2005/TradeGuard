// Generates the SYNTHETIC sample trade file. Deterministic (seeded) so the committed CSV is reproducible.
// Usage: node scripts/generate-sample-data.mjs [rows=300] [prefix=DEMO]
// Writes sample-data/sample_trades.csv and a copy for the backend demo seeder.
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const total = Number(process.argv[2] ?? 300);
const prefix = process.argv[3] ?? "DEMO";

let seed = 20250303;
const rand = () => {
  seed = (seed * 1664525 + 1013904223) % 4294967296;
  return seed / 4294967296;
};
const pick = (a) => a[Math.floor(rand() * a.length)];

// Illustrative symbols and price bands; NOT real market data.
const instruments = {
  ES: [5000, 5600, "USD"],
  NQ: [17500, 19500, "USD"],
  YM: [38000, 42000, "USD"],
  CL: [68, 85, "USD"],
  NG: [1.8, 3.4, "USD"],
  GC: [2100, 2700, "USD"],
  ZN: [108, 113, "USD"],
  "6E": [1.05, 1.12, "USD"],
};
const traders = ["TRD-001", "TRD-002", "TRD-003", "TRD-004", "TRD-005", "TRD-006"];

const day0 = Date.UTC(2025, 2, 3); // Mon 3 Mar 2025
const tradingDays = [];
for (let i = 0; tradingDays.length < 20; i++) {
  const d = new Date(day0 + i * 86400000);
  if (d.getUTCDay() !== 0 && d.getUTCDay() !== 6) tradingDays.push(d);
}

function validRow(n) {
  const instrument = pick(Object.keys(instruments));
  const [lo, hi, currency] = instruments[instrument];
  const decimals = hi < 10 ? 4 : 2;
  const price = (lo + rand() * (hi - lo)).toFixed(decimals);
  const quantity = 1 + Math.floor(rand() * 250);
  const day = pick(tradingDays);
  const ts = new Date(day.getTime() + (13 * 3600 + Math.floor(rand() * 8 * 3600)) * 1000);
  return {
    trade_id: `${prefix}-${String(n).padStart(5, "0")}`,
    instrument,
    trade_timestamp: ts.toISOString().replace(".000Z", "Z"),
    side: rand() < 0.5 ? "BUY" : "SELL",
    price,
    quantity: String(quantity),
    currency,
    trader_id: pick(traders),
  };
}

const rows = Array.from({ length: total }, (_, i) => validRow(i + 1));

// Intentional defects, placed at fixed positions so the demo is predictable.
const defects = [
  [4, (r) => (r.trade_id = "")],
  [11, (r) => (r.instrument = "")],
  [19, (r) => (r.trade_timestamp = "03/05/2025 14:30")],
  [27, (r) => (r.trade_timestamp = "not-a-date")],
  [33, (r) => (r.side = "HOLD")],
  [41, (r) => (r.side = "")],
  [48, (r) => (r.price = "0")],
  [55, (r) => (r.price = "-12.50")],
  [62, (r) => (r.price = "12,5")],
  [70, (r) => (r.quantity = "0")],
  [77, (r) => (r.quantity = "-40")],
  [84, (r) => (r.quantity = "abc")],
  [91, (r) => (r.quantity = "250000")],
  [99, (r) => (r.currency = "XXX")],
  [106, (r) => (r.currency = "")],
  [113, (r) => (r.instrument = "ZZ")],
  [120, (r) => (r.trader_id = "")],
  [127, (r) => (r.trade_timestamp = "")],
  [134, (r) => (r.price = "")],
  // duplicate trade ids within the file (later rows reuse an earlier valid id)
  [141, (r, all) => (r.trade_id = all[10].trade_id)],
  [148, (r, all) => (r.trade_id = all[10].trade_id)],
  [155, (r, all) => (r.trade_id = all[60].trade_id)],
  [162, (r, all) => (r.trade_id = all[60].trade_id)],
  [169, (r, all) => (r.trade_id = all[100].trade_id)],
];
for (const [idx, apply] of defects) if (idx < rows.length) apply(rows[idx], rows);

const esc = (v) => (/[",\n]/.test(v) ? `"${v.replaceAll('"', '""')}"` : v);
const header = "trade_id,instrument,trade_timestamp,side,price,quantity,currency,trader_id";
const lines = rows.map((r) => Object.values(r).map(esc).join(","));

// A quoted field containing a comma (valid), a formula-looking trader id (valid text, neutralised on export),
// and a structurally malformed row (too many columns).
if (rows.length > 200) {
  lines[180] = lines[180].replace(/,[^,]*$/, ',"DESK-1, NEW YORK"');
  lines[190] = lines[190].replace(/,[^,]*$/, ",=HYPERLINK(\"http://example.com\")").replace(/,=HYPERLINK.*$/, ',"=SUM(1+1)"');
  lines[200] = lines[200] + ",EXTRA";
}

const csv = [header, ...lines].join("\n") + "\n";
for (const dir of ["sample-data", "backend/src/main/resources/demo"]) {
  mkdirSync(join(root, dir), { recursive: true });
  writeFileSync(join(root, dir, "sample_trades.csv"), csv);
}
console.log(`Wrote ${rows.length} rows (+header) to sample-data/ and backend demo resources.`);
