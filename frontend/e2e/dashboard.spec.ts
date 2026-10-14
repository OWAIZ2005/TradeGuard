import { expect, test } from "@playwright/test";
import { csvOf, runId, setDateRange, TEST_DAY, uploadCsv } from "./helpers";

const fmt = (n: number) => n.toLocaleString("en-US");

test.describe("Dashboard", () => {
  test("KPIs come from the backend, not hard-coded values", async ({ page, request }) => {
    const overview = await (await request.get("/api/analytics/overview")).json();
    await page.goto("/");
    await expect(page.getByRole("heading", { name: "Overview" })).toBeVisible();
    await expect(page.getByTestId("kpi-total-value")).toHaveText(fmt(overview.totalRows));
    await expect(page.getByTestId("kpi-valid-value")).toHaveText(fmt(overview.validTrades));
    await expect(page.getByTestId("kpi-invalid-value")).toHaveText(fmt(overview.invalidTrades));
    await expect(page.getByTestId("kpi-duplicates-value")).toHaveText(fmt(overview.duplicateTrades));
    await expect(page.getByTestId("kpi-error-rate-value")).toHaveText(
      `${overview.validationErrorRatePercent.toFixed(2)}%`,
    );
    await expect(page.getByText("not converted or added across currencies").first()).toBeVisible();
    // recent imports come from the history endpoint
    await expect(page.getByTestId("recent-imports").getByRole("row")).not.toHaveCount(1);
  });

  test("date range filters every KPI and chart consistently, and carries over to Analytics", async ({ page }) => {
    const run = runId();
    await uploadCsv(
      page,
      `dash-${run}.csv`,
      csvOf([
        { id: `${run}-1`, side: "BUY", qty: "10", price: "100" },
        { id: `${run}-2`, side: "SELL", qty: "5", price: "200", instrument: "NQ" },
        { id: `${run}-3`, side: "BUY", qty: "1", price: "50", ccy: "EUR" },
        { id: `${run}-4`, price: "-5" },
        { id: `${run}-1` },
      ]),
    );

    await page.goto("/");
    await setDateRange(page, TEST_DAY, TEST_DAY);
    await expect(page.getByTestId("kpi-valid-value")).toHaveText("3");
    await expect(page.getByTestId("kpi-invalid-value")).toHaveText("2");
    await expect(page.getByTestId("kpi-duplicates-value")).toHaveText("1");
    await expect(page.getByTestId("kpi-total-value")).toHaveText("5");
    await expect(page.getByTestId("kpi-error-rate-value")).toHaveText("40.00%");
    await expect(page.getByTestId("kpi-quantity-value")).toHaveText("16"); // 10 + 5 + 1, invalid rows excluded
    // notional is shown per currency: 10x100 + 5x200 = 2,000 USD; 1x50 = 50 EUR; never summed together
    const notional = page.getByTestId("kpi-notional-value");
    await expect(notional).toContainText("$2,000.00");
    await expect(notional).toContainText("€50.00");

    await expect(page.getByTestId("chart-daily").locator(".recharts-surface").first()).toBeVisible();
    await expect(page.getByTestId("side-table")).toContainText("BUY");
    await expect(page.getByTestId("side-table")).toContainText("2 trades"); // 2 buys
    await expect(page.getByTestId("chart-errors").locator(".recharts-surface").first()).toBeVisible();

    // the same range is applied on the Analytics page
    await page.getByRole("link", { name: "Analytics & Reports" }).first().click();
    await expect(page.locator("#range-from")).toHaveValue(TEST_DAY);
    await expect(page.locator("#range-to")).toHaveValue(TEST_DAY);
    await expect(page.getByTestId("an-valid-value")).toHaveText("3");
    await expect(page.getByTestId("instrument-table")).toContainText("NQ");
    await expect(page.getByTestId("currency-note")).toContainText("not converted");

    // filters on the analytics page narrow every figure
    await page.getByLabel("Instrument").selectOption("NQ");
    await expect(page.getByTestId("an-valid-value")).toHaveText("1");
    await page.getByLabel("Instrument").selectOption("");
    await expect(page.getByTestId("an-valid-value")).toHaveText("3");
  });

  test("an inverted date range is flagged instead of silently returning nothing", async ({ page }) => {
    await page.goto("/");
    await setDateRange(page, "2031-05-06", "2031-05-05");
    await expect(page.getByRole("alert").filter({ hasText: "must not be after" })).toBeVisible();
    await expect(page.getByTestId("error-state").first()).toContainText("date range is invalid");
  });

  test("navigation reaches every section", async ({ page }) => {
    await page.goto("/");
    const nav = page.getByRole("navigation", { name: "Main", exact: true });
    for (const name of [
      "Trade Explorer",
      "Import Data",
      "Import History",
      "Analytics & Reports",
      "Validation Rules",
      "Overview",
    ]) {
      await nav.getByRole("link", { name }).click();
      await expect(page.getByRole("heading", { level: 1, name })).toBeVisible();
    }
  });
});
