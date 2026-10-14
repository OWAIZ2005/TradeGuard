import { expect, test } from "@playwright/test";
import { readFileSync } from "node:fs";
import { csvOf, runId, uploadCsv, type Row } from "./helpers";

function lines(path: string): string[] {
  return readFileSync(path, "utf8").split("\r\n").filter((l) => l.length > 0);
}

test.describe("Report downloads", () => {
  const run = runId();
  const rows: Row[] = [
    { id: `${run}-1`, instrument: "ES", side: "BUY", price: "100.5", qty: "10" },
    { id: `${run}-2`, instrument: "NQ", side: "SELL", price: "200", qty: "2" },
    { id: `${run}-3`, instrument: "NQ", side: "BUY", price: "300", qty: "1", trader: '"DESK 1, NY"' },
    { id: `${run}-4`, price: "-1" }, // invalid: negative price
    { id: `${run}-5`, side: "HOLD" }, // invalid: side
  ];

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage({ baseURL: process.env.BASE_URL ?? "http://localhost:3000" });
    await uploadCsv(page, `reports-${run}.csv`, csvOf(rows));
    await expect(page.getByTestId("batch-total-value")).toHaveText("5");
    await page.close();
  });

  test("valid trades CSV contains only valid rows and honours the explorer filters", async ({ page }) => {
    await page.goto("/trades");
    await page.getByLabel("Search trade or trader ID").fill(run);
    await page.getByLabel("Instrument").selectOption("NQ");
    await page.getByTestId("apply-filters").click();
    await expect(page.getByTestId("page-range")).toHaveText("1–2 of 2");

    const [download] = await Promise.all([
      page.waitForEvent("download"),
      page.getByTestId("download-valid-trades").click(),
    ]);
    expect(download.suggestedFilename()).toBe("valid-trades.csv");
    const out = lines((await download.path())!);
    expect(out[0]).toBe(
      "trade_id,instrument,trade_timestamp,side,price,quantity,currency,notional,trader_id,batch_id,source_row",
    );
    expect(out).toHaveLength(3); // header + the 2 NQ trades; the ES trade and both invalid rows are excluded
    expect(out.join("\n")).not.toContain(`${run}-1,`);
    expect(out[1]).toContain(`${run}-2,NQ,2031-05-05T14:30:00Z,SELL,200.00,2.00,USD,400.00,TRD-E2E`);
    expect(out[2]).toContain(',"DESK 1, NY",'); // a comma inside a value is quoted, not split
  });

  test("validation errors CSV lists row numbers and codes and neutralises formula-like values", async ({ page }) => {
    await page.goto("/imports");
    await page.getByTestId("history-row").filter({ hasText: `reports-${run}.csv` }).getByRole("link").click();
    const [download] = await Promise.all([
      page.waitForEvent("download"),
      page.getByTestId("download-batch-errors").click(),
    ]);
    expect(download.suggestedFilename()).toBe("validation-errors.csv");
    const out = lines((await download.path())!);
    expect(out[0]).toContain("batch_id,filename,row_number,trade_id,field,error_code,error_category,message");
    expect(out).toHaveLength(3); // header + 2 errors
    const priceLine = out.find((l) => l.includes("NON_POSITIVE_PRICE"))!;
    expect(priceLine).toContain(`,5,${run}-4,price,NON_POSITIVE_PRICE,OUT_OF_RANGE,`); // row 5 (header = row 1)
    expect(priceLine).toContain(",'-1,"); // a spreadsheet would read -1 as a formula; it is prefixed with '
    const sideLine = out.find((l) => l.includes("UNSUPPORTED_SIDE"))!;
    expect(sideLine).toContain(`,6,${run}-5,side,UNSUPPORTED_SIDE,UNSUPPORTED_VALUE,`);
  });

  test("batch summary CSV reports the batch quality figures", async ({ page }) => {
    await page.goto("/imports");
    await page.getByTestId("history-row").filter({ hasText: `reports-${run}.csv` }).getByRole("link").click();
    const [download] = await Promise.all([
      page.waitForEvent("download"),
      page.getByTestId("download-batch-summary").click(),
    ]);
    const text = readFileSync((await download.path())!, "utf8");
    expect(text).toContain("quality,total_rows,5");
    expect(text).toContain("quality,valid_rows,3");
    expect(text).toContain("quality,invalid_rows,2");
    expect(text).toContain("quality,error_rate_percent,40.00");
    expect(text).toContain("valid_notional_USD,notional,");
    expect(text).toContain("not converted or added across currencies");
  });

  test("analytics page downloads respect its filters (batch and side)", async ({ page }) => {
    await page.goto("/analytics");
    await page.getByLabel("Import batch").selectOption({ index: 1 }); // newest batch first
    await page.getByLabel("Side").selectOption("BUY");
    const href = await page.getByTestId("report-valid-trades").getAttribute("href");
    expect(href).toContain("side=BUY");
    expect(href).toMatch(/batchId=\d+/);
    const [download] = await Promise.all([
      page.waitForEvent("download"),
      page.getByTestId("report-valid-trades").click(),
    ]);
    const out = lines((await download.path())!);
    expect(out.length).toBeGreaterThan(1);
    for (const line of out.slice(1)) expect(line).toContain(",BUY,");
  });
});
