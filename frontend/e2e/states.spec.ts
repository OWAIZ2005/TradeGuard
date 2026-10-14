import { expect, test } from "@playwright/test";

// Only these tests stub API responses: they need states (empty database, backend failure) that the shared
// development database cannot be put into on demand.
test.describe("Empty, loading and error states", () => {
  test("overview shows an empty state with guidance when nothing is imported", async ({ page }) => {
    await page.route(/\/api\/analytics\/overview/, (route) =>
      route.fulfill({
        json: {
          totalRows: 0,
          validTrades: 0,
          invalidTrades: 0,
          duplicateTrades: 0,
          validationErrorRatePercent: 0,
          totalQuantity: 0,
          notionalByCurrency: [],
          currencyNote: "n/a",
        },
      }),
    );
    await page.route(/\/api\/analytics\/(daily-volume|sides|instruments)/, (route) => route.fulfill({ json: [] }));
    await page.goto("/");
    const empty = page.getByTestId("empty-state").first();
    await expect(empty).toContainText("No trades imported yet");
    await expect(empty.getByRole("link", { name: "Import Data" })).toHaveAttribute("href", "/import");
    await expect(page.getByTestId("kpi-total")).toHaveCount(0);
  });

  test("trade explorer shows an empty state when no rows match", async ({ page }) => {
    await page.route(/\/api\/trades\?/, (route) =>
      route.fulfill({ json: { content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 } }),
    );
    await page.goto("/trades");
    await expect(page.getByTestId("empty-state")).toContainText("No trades match these filters");
    await expect(page.getByTestId("trades-table")).toHaveCount(0);
  });

  test("shows a loading indicator while the API is slow", async ({ page }) => {
    await page.route(/\/api\/imports\?/, async (route) => {
      await new Promise((r) => setTimeout(r, 1500));
      await route.continue();
    });
    await page.goto("/imports");
    await expect(page.getByRole("status")).toContainText("Loading");
    await expect(page.getByTestId("history-table")).toBeVisible();
  });

  test("a failing API shows the error with a working Retry", async ({ page }) => {
    let fail = true;
    await page.route(/\/api\/analytics\/overview/, (route) =>
      fail
        ? route.fulfill({
            status: 500,
            json: { status: 500, code: "INTERNAL_ERROR", message: "An unexpected error occurred. Please try again later." },
          })
        : route.continue(),
    );
    await page.goto("/");
    const error = page.getByTestId("error-state").first();
    await expect(error).toContainText("An unexpected error occurred");
    await expect(error).not.toContainText("Exception"); // no internals leak to the UI

    fail = false;
    await error.getByRole("button", { name: "Retry" }).click();
    await expect(page.getByTestId("kpi-total")).toBeVisible();
  });

  test("an unreachable backend is reported clearly", async ({ page }) => {
    await page.route(/\/api\/imports\?/, (route) => route.abort("connectionrefused"));
    await page.goto("/imports");
    await expect(page.getByTestId("error-state")).toContainText("Cannot reach the TradeGuard backend");
  });

  test("an unknown batch id shows the server not-found message", async ({ page }) => {
    await page.goto("/imports/99999999");
    await expect(page.getByTestId("error-state")).toContainText("Import batch 99999999 was not found");
  });
});
