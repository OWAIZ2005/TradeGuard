import { expect, test } from "@playwright/test";
import { csvOf, runId, setDateRange, TEST_DAY, uploadCsv, type Row } from "./helpers";

test.describe("Trade explorer", () => {
  const run = runId();
  const rows: Row[] = [];
  for (let i = 1; i <= 30; i++) {
    rows.push({
      id: `${run}-${String(i).padStart(2, "0")}`,
      instrument: i % 2 === 0 ? "NQ" : "ES",
      side: i % 3 === 0 ? "SELL" : "BUY",
      price: "100.00",
      qty: String(i),
    });
  }
  // two invalid rows with different problems
  rows.push({ id: `${run}-BAD1`, price: "0" });
  rows.push({ id: `${run}-BAD2`, ts: "05/05/2031 14:30", qty: "-4" });

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    await uploadCsv(page, `explorer-${run}.csv`, csvOf(rows));
    await expect(page.getByTestId("batch-total-value")).toHaveText("32");
    await page.close();
  });

  async function search(page: import("@playwright/test").Page, extra?: () => Promise<void>) {
    await page.goto("/trades");
    await page.getByLabel("Search trade or trader ID").fill(run);
    if (extra) await extra();
    await page.getByTestId("apply-filters").click();
  }

  test("paginates on the server and reports the total", async ({ page }) => {
    await search(page);
    await expect(page.getByTestId("page-range")).toHaveText("1–25 of 32");
    await expect(page.getByTestId("trade-row")).toHaveCount(25);

    await page.getByRole("button", { name: "Next page" }).click();
    await expect(page.getByTestId("page-range")).toHaveText("26–32 of 32");
    await expect(page.getByTestId("trade-row")).toHaveCount(7);
    await expect(page.getByRole("button", { name: "Next page" })).toBeDisabled();

    await page.getByLabel("Rows per page").selectOption("50");
    await expect(page.getByTestId("page-range")).toHaveText("1–32 of 32");
  });

  test("filters by instrument, side, status and combinations", async ({ page }) => {
    await search(page, () => page.getByLabel("Instrument").selectOption("NQ"));
    await expect(page.getByTestId("page-range")).toHaveText("1–15 of 15"); // even ids

    await page.getByLabel("Side").selectOption("SELL");
    await page.getByTestId("apply-filters").click();
    // NQ (even) and SELL (multiple of 3) => multiples of 6: 6,12,18,24,30
    await expect(page.getByTestId("page-range")).toHaveText("1–5 of 5");
    for (const text of await page.getByTestId("trade-row").allInnerTexts()) {
      expect(text).toContain("NQ");
      expect(text).toContain("SELL");
    }

    await page.getByRole("button", { name: "Reset" }).click();
    await page.getByLabel("Search trade or trader ID").fill(run);
    await page.getByLabel("Status").selectOption("INVALID");
    await page.getByTestId("apply-filters").click();
    await expect(page.getByTestId("page-range")).toHaveText("1–2 of 2");
    for (const text of await page.getByTestId("trade-row").allInnerTexts()) expect(text).toContain("INVALID");
  });

  test("filters by import batch and by the shared date range", async ({ page }) => {
    await page.goto("/trades");
    const option = page.getByLabel("Import batch").locator("option", { hasText: `explorer-${run}` });
    await page.getByLabel("Import batch").selectOption((await option.getAttribute("value")) as string);
    await page.getByTestId("apply-filters").click();
    await expect(page.getByTestId("page-range")).toHaveText("1–25 of 32");

    await setDateRange(page, "2031-05-06", "2031-05-06");
    await expect(page.getByTestId("empty-state")).toContainText("No trades match these filters");

    await setDateRange(page, TEST_DAY, TEST_DAY);
    // the two invalid rows keep their (unparseable or valid) dating: BAD1 dated 2031-05-05, BAD2 dated by upload time
    await expect(page.getByTestId("page-range")).toContainText("of 31");
  });

  test("sorts server-side by clicking a column header", async ({ page }) => {
    await search(page, () => page.getByLabel("Status").selectOption("VALID"));
    await page.getByRole("button", { name: "QUANTITY" }).click(); // ascending
    await expect(page.getByRole("columnheader", { name: /quantity/i })).toHaveAttribute("aria-sort", "ascending");
    const first = page.getByTestId("trade-row").first();
    await expect(first).toContainText(`${run}-01`);
    await page.getByRole("button", { name: "QUANTITY" }).click(); // descending
    await expect(page.getByTestId("trade-row").first()).toContainText(`${run}-30`);
  });

  test("inspecting an invalid trade shows every validation error with its explanation", async ({ page }) => {
    await search(page, () => page.getByLabel("Status").selectOption("INVALID"));
    await page.getByRole("button", { name: `Inspect trade ${run}-BAD2` }).click();

    const drawer = page.getByTestId("trade-drawer");
    await expect(drawer).toBeVisible();
    const errors = drawer.getByTestId("drawer-errors");
    await expect(errors).toContainText("Validation errors (2)");
    await expect(errors).toContainText("INVALID_TIMESTAMP");
    await expect(errors).toContainText("Timestamp must be ISO-8601 with a UTC offset");
    await expect(errors).toContainText("value: “05/05/2031 14:30”");
    await expect(errors).toContainText("NON_POSITIVE_QUANTITY");
    await expect(drawer).toContainText("Batch #");
    await expect(drawer).toContainText(`${run}-BAD2,ES,05/05/2031 14:30`); // original CSV row

    await page.keyboard.press("Escape");
    await expect(drawer).toHaveCount(0);
  });

  test("a valid trade shows no errors and its notional", async ({ page }) => {
    await page.goto("/trades");
    await page.getByLabel("Search trade or trader ID").fill(`${run}-02`);
    await page.getByTestId("apply-filters").click();
    await expect(page.getByTestId("page-range")).toHaveText("1–1 of 1");
    await page.getByRole("button", { name: `Inspect trade ${run}-02` }).click();
    const drawer = page.getByTestId("trade-drawer");
    await expect(drawer).toContainText("No validation errors");
    await expect(drawer).toContainText("200.00"); // 100.00 x 2
  });
});
