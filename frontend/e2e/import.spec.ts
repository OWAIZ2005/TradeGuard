import { expect, test } from "@playwright/test";
import { csvOf, runId, uploadCsv } from "./helpers";

test.describe("CSV import flow", () => {
  test("uploads a mixed file and shows the batch summary and an inspectable error table", async ({ page }) => {
    const run = runId();
    const csv = csvOf([
      { id: `${run}-1`, instrument: "ES", side: "BUY" },
      { id: `${run}-2`, instrument: "NQ", side: "SELL", price: "18000.25", qty: "3" },
      { id: `${run}-3`, instrument: "ES", side: "SELL" },
      { id: `${run}-4`, price: "-1" }, // row 5: non-positive price
      { id: `${run}-1` }, // row 6: duplicate of row 2
    ]);
    await uploadCsv(page, `mixed-${run}.csv`, csv);

    await expect(page.getByTestId("batch-total-value")).toHaveText("5");
    await expect(page.getByTestId("batch-valid-value")).toHaveText("3");
    await expect(page.getByTestId("batch-invalid-value")).toHaveText("2");
    await expect(page.getByTestId("batch-duplicates-value")).toHaveText("1");

    const summary = page.getByTestId("batch-summary");
    await expect(summary).toContainText("2 of 5 rows (40%) were rejected");
    await expect(summary).toContainText("rule-based");
    await expect(summary).toContainText("price is zero or below");
    await expect(summary).toContainText("repeats an earlier row");

    const rows = page.getByTestId("errors-table").getByRole("row");
    await expect(rows).toHaveCount(3); // header + 2 errors
    const priceRow = rows.filter({ hasText: "NON_POSITIVE_PRICE" });
    await expect(priceRow).toContainText("5"); // original row number
    await expect(priceRow).toContainText("Price must be greater than zero.");
    await expect(priceRow).toContainText("-1");
    const dupRow = rows.filter({ hasText: "DUPLICATE_IN_BATCH" });
    await expect(dupRow).toContainText("already appears at row 2");
    await expect(dupRow).toContainText(`${run}-1`);

    // filter the error table by code
    await page.getByRole("button", { name: /Duplicate In Batch/ }).click();
    await expect(page.getByTestId("errors-table").getByRole("row")).toHaveCount(2);
  });

  test("re-uploading the same file never overwrites: every row is a duplicate of an existing trade", async ({ page }) => {
    const run = runId();
    const csv = csvOf([{ id: `${run}-A` }, { id: `${run}-B`, instrument: "NQ" }]);
    await uploadCsv(page, `first-${run}.csv`, csv);
    await expect(page.getByTestId("batch-valid-value")).toHaveText("2");

    await uploadCsv(page, `second-${run}.csv`, csv);
    await expect(page.getByTestId("batch-valid-value")).toHaveText("0");
    await expect(page.getByTestId("batch-invalid-value")).toHaveText("2");
    await expect(page.getByTestId("batch-summary")).toContainText("already imported");
    await expect(page.getByTestId("errors-table")).toContainText("DUPLICATE_EXISTING");
  });

  test("rejects a non-CSV file in the browser before uploading", async ({ page }) => {
    await page.goto("/import");
    await page.getByTestId("file-input").setInputFiles({ name: "trades.txt", mimeType: "text/plain", buffer: Buffer.from("x") });
    await expect(page.getByTestId("upload-error")).toContainText("Only .csv files are accepted");
    await expect(page.getByTestId("upload-button")).toHaveCount(0);
  });

  test("shows the server's reason when required headers are missing", async ({ page }) => {
    await page.goto("/import");
    await page.getByTestId("file-input").setInputFiles({
      name: "bad-headers.csv",
      mimeType: "text/csv",
      buffer: Buffer.from("trade_id,instrument,price\nX-1,ES,10\n"),
    });
    await page.getByTestId("upload-button").click();
    const err = page.getByTestId("upload-error");
    await expect(err).toContainText("MISSING_HEADERS");
    await expect(err).toContainText("trade_timestamp");
    await expect(err).toContainText("trader_id");
    await expect(page.getByTestId("batch-result")).toHaveCount(0);
  });

  test("shows the server's reason for an empty (header only) file", async ({ page }) => {
    await page.goto("/import");
    await page.getByTestId("file-input").setInputFiles({
      name: "header-only.csv",
      mimeType: "text/csv",
      buffer: Buffer.from("trade_id,instrument,trade_timestamp,side,price,quantity,currency,trader_id\n"),
    });
    await page.getByTestId("upload-button").click();
    await expect(page.getByTestId("upload-error")).toContainText("EMPTY_FILE");
  });

  test("a new batch appears in Import History and its detail page shows the same counts", async ({ page }) => {
    const run = runId();
    const name = `history-${run}.csv`;
    await uploadCsv(page, name, csvOf([{ id: `${run}-1` }, { id: `${run}-2`, side: "HOLD" }, { id: `${run}-3` }]));

    await page.goto("/imports");
    const row = page.getByTestId("history-row").filter({ hasText: name });
    await expect(row).toHaveCount(1);
    await expect(row).toContainText("COMPLETED");
    const cells = row.getByRole("cell");
    await expect(cells.nth(3)).toHaveText("3"); // total rows
    await expect(cells.nth(4)).toHaveText("2"); // valid
    await expect(cells.nth(5)).toHaveText("1"); // invalid
    await expect(cells.nth(6)).toHaveText("1"); // errors logged
    await expect(cells.nth(7)).toHaveText("33.33%");

    await row.getByRole("link", { name: /^#\d+/ }).click();
    await expect(page.getByTestId("batch-total-value")).toHaveText("3");
    await expect(page.getByTestId("errors-table")).toContainText("UNSUPPORTED_SIDE");
  });

  test("history can be filtered by status", async ({ page }) => {
    await page.goto("/imports");
    await expect(page.getByTestId("history-row").first()).toBeVisible();
    await page.getByLabel("Status").selectOption("FAILED");
    // either there are failed batches (all FAILED) or the empty state shows
    const rows = page.getByTestId("history-row");
    if ((await rows.count()) > 0) {
      for (const text of await rows.allInnerTexts()) expect(text).toContain("FAILED");
    } else {
      await expect(page.getByTestId("empty-state")).toBeVisible();
    }
  });
});
