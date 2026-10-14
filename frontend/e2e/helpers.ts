import { expect, type Page } from "@playwright/test";

export const HEADER = "trade_id,instrument,trade_timestamp,side,price,quantity,currency,trader_id";

/** Unique per test run so re-running the suite never collides with trade ids already imported. */
export function runId(): string {
  return `E2E${Date.now().toString(36).toUpperCase()}${Math.floor(Math.random() * 1000)}`;
}

export interface Row {
  id: string;
  instrument?: string;
  ts?: string;
  side?: string;
  price?: string;
  qty?: string;
  ccy?: string;
  trader?: string;
}

/** Trades are dated 2031-05-05 so a date filter on that day isolates a test's own data from demo data. */
export const TEST_DAY = "2031-05-05";

export function csvOf(rows: Row[]): string {
  const lines = rows.map((r) =>
    [
      r.id,
      r.instrument ?? "ES",
      r.ts ?? `${TEST_DAY}T14:30:00Z`,
      r.side ?? "BUY",
      r.price ?? "100.50",
      r.qty ?? "10",
      r.ccy ?? "USD",
      r.trader ?? "TRD-E2E",
    ].join(","),
  );
  return [HEADER, ...lines].join("\n") + "\n";
}

export async function uploadCsv(page: Page, name: string, content: string) {
  await page.goto("/import");
  await page.getByTestId("file-input").setInputFiles({ name, mimeType: "text/csv", buffer: Buffer.from(content) });
  await expect(page.getByTestId("selected-file")).toContainText(name);
  await page.getByTestId("upload-button").click();
  await expect(page.getByTestId("batch-result")).toBeVisible();
}

export async function setDateRange(page: Page, from: string, to: string) {
  await page.locator("#range-from").fill(from);
  await page.locator("#range-to").fill(to);
}
