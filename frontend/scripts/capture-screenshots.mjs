// Captures the README screenshots from a RUNNING stack (demo data loaded).
// Usage (from frontend/):  BASE_URL=http://localhost:3000 node scripts/capture-screenshots.mjs
import { chromium } from "@playwright/test";
import { mkdirSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const base = process.env.BASE_URL ?? "http://localhost:3000";
const out = join(dirname(fileURLToPath(import.meta.url)), "..", "..", "docs", "screenshots");
mkdirSync(out, { recursive: true });

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
const shot = (name, opts = {}) => page.screenshot({ path: join(out, `${name}.png`), ...opts });

await page.goto(base + "/");
await page.getByTestId("chart-instruments").locator(".recharts-surface").first().waitFor();
await page.waitForTimeout(800);
await shot("overview", { fullPage: true });

// Import: upload the sample file under a unique name so it is processed as a new batch (rows already imported
// show up as duplicates, which is also a useful screenshot).
await page.goto(base + "/import");
const csv = readFileSync(join(out, "..", "..", "sample-data", "sample_trades.csv"), "utf8").replaceAll("DEMO-", "SHOT-");
await page.getByTestId("file-input").setInputFiles({ name: "sample_trades_copy.csv", mimeType: "text/csv", buffer: Buffer.from(csv) });
await page.getByTestId("upload-button").click();
await page.getByTestId("errors-table").waitFor();
await page.waitForTimeout(500);
await shot("import", { fullPage: true });

await page.goto(base + "/trades");
await page.getByLabel("Status").selectOption("INVALID");
await page.getByTestId("apply-filters").click();
await page.getByTestId("trade-row").first().waitFor();
await page.getByTestId("trade-row").nth(2).getByRole("button").click();
await page.getByTestId("drawer-errors").waitFor();
await page.waitForTimeout(300);
await shot("explorer");

await page.goto(base + "/analytics");
await page.getByTestId("instrument-table").waitFor();
await page.waitForTimeout(800);
await shot("analytics", { fullPage: true });

await page.goto(base + "/rules");
await page.getByLabel("Maximum trade quantity").waitFor();
await shot("rules");

await browser.close();
console.log("Screenshots written to docs/screenshots");
