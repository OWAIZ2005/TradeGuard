import { expect, test } from "@playwright/test";
import { csvOf, runId, uploadCsv } from "./helpers";

interface RulesBody {
  maxUploadSizeMb: number;
  allowedCurrencies: string[];
  allowedInstruments: string[];
  maxTradeQuantity: number;
}

test.describe.serial("Validation rules", () => {
  let original: RulesBody;

  test.beforeAll(async ({ request }) => {
    original = await (await request.get("/api/rules")).json();
  });

  test.afterAll(async ({ request }) => {
    const { maxUploadSizeMb, allowedCurrencies, allowedInstruments, maxTradeQuantity } = original;
    await request.put("/api/rules", { data: { maxUploadSizeMb, allowedCurrencies, allowedInstruments, maxTradeQuantity } });
  });

  test("shows the current rules from the backend", async ({ page }) => {
    await page.goto("/rules");
    await expect(page.getByLabel("Maximum upload size (MB)")).toHaveValue(String(original.maxUploadSizeMb));
    await expect(page.getByLabel("Allowed currencies")).toHaveValue(original.allowedCurrencies.join(", "));
    await expect(page.getByLabel("Maximum trade quantity")).toHaveValue(String(original.maxTradeQuantity));
  });

  test("the server rejects invalid values, reports them next to the field, and saves nothing", async ({ page, request }) => {
    await page.goto("/rules");
    await page.getByLabel("Maximum upload size (MB)").fill("0");
    await page.getByLabel("Allowed currencies").fill("USD, EURO");
    await page.getByLabel("Maximum trade quantity").fill("-3");
    await page.getByTestId("save-rules").click();

    await expect(page.getByTestId("rules-error")).toContainText("Nothing was saved");
    await expect(page.getByText("maxUploadSizeMb must be at least 1")).toBeVisible();
    await expect(page.getByText("currencies must be 3-letter ISO codes")).toBeVisible();
    await expect(page.getByText("maxTradeQuantity must be greater than zero")).toBeVisible();
    await expect(page.getByTestId("rules-saved")).toHaveCount(0);

    const current = await (await request.get("/api/rules")).json();
    expect(current.maxUploadSizeMb).toBe(original.maxUploadSizeMb);
    expect(current.maxTradeQuantity).toBe(original.maxTradeQuantity);
  });

  test("a saved change applies to the next import only", async ({ page, request }) => {
    const run = runId();
    // import under the original rules: quantity 20 is fine
    await uploadCsv(page, `before-${run}.csv`, csvOf([{ id: `${run}-1`, qty: "20" }]));
    await expect(page.getByTestId("batch-valid-value")).toHaveText("1");

    await page.goto("/rules");
    await page.getByLabel("Maximum trade quantity").fill("15");
    await page.getByLabel("Allowed currencies").fill("USD, GBP");
    await page.getByTestId("save-rules").click();
    await expect(page.getByTestId("rules-saved")).toBeVisible();
    const saved = await (await request.get("/api/rules")).json();
    expect(saved.maxTradeQuantity).toBe(15);
    expect(saved.allowedCurrencies).toEqual(["GBP", "USD"]);

    // the same quantity is now rejected, and so is a currency that was removed
    await uploadCsv(
      page,
      `after-${run}.csv`,
      csvOf([
        { id: `${run}-2`, qty: "20" },
        { id: `${run}-3`, qty: "15" },
        { id: `${run}-4`, qty: "1", ccy: "EUR" },
      ]),
    );
    await expect(page.getByTestId("batch-valid-value")).toHaveText("1");
    await expect(page.getByTestId("batch-invalid-value")).toHaveText("2");
    const table = page.getByTestId("errors-table");
    await expect(table).toContainText("QUANTITY_EXCEEDS_MAX");
    await expect(table).toContainText("maximum of 15");
    await expect(table).toContainText("UNSUPPORTED_CURRENCY");

    // the earlier batch is untouched
    await page.goto("/imports");
    const before = page.getByTestId("history-row").filter({ hasText: `before-${run}.csv` });
    await expect(before.getByRole("cell").nth(4)).toHaveText("1");
    await expect(before.getByRole("cell").nth(5)).toHaveText("0");
  });

  test("the configured upload size limit is enforced and reflected on the import page", async ({ page, request }) => {
    await request.put("/api/rules", { data: { ...original, maxUploadSizeMb: 1 } });
    await page.goto("/import");
    await expect(page.getByText("up to 1 MB")).toBeVisible();
    const big = Buffer.alloc(1024 * 1024 + 1024, "a");
    await page.getByTestId("file-input").setInputFiles({ name: "big.csv", mimeType: "text/csv", buffer: big });
    await expect(page.getByTestId("upload-error")).toContainText("maximum allowed is 1 MB");
  });
});
