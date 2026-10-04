import { test, expect } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  // No real provider calls or credentials: these checks cover offline UI behavior.
  await page.route("**/*", route => {
    const url = new URL(route.request().url());
    return url.origin === "http://127.0.0.1:4173" && !/^\/(v1|health|stt|tts)(\/|$)/.test(url.pathname)
      ? route.continue() : route.abort();
  });
});

for (const [width, height] of [[390, 844], [320, 568], [390, 480], [768, 1024], [1440, 900]]) {
  test(`composer stays clickable and navigation stays on screen at ${width}x${height}`, async ({ page }) => {
    await page.setViewportSize({ width, height });
    await page.goto("/");
    await page.locator("nav:visible").getByRole("button", { name: "Sohbet", exact: true }).click();
    const input = page.getByRole("textbox", { name: "Mesajınız", exact: true });
    await expect(input).toBeVisible();
    // A locator's visibility alone does not catch another element covering it.
    const reachable = () => input.evaluate(el => {
      const r = el.getBoundingClientRect();
      return r.top >= 0 && r.bottom <= innerHeight &&
        document.elementFromPoint(r.x + r.width / 2, r.y + r.height / 2) === el;
    });
    expect(await reachable()).toBe(true);
    await input.click();
    await input.fill("Birinci satır\nİkinci satır\nÜçüncü satır");
    expect(await reachable()).toBe(true);
    await input.press("Tab");
    await expect(page.getByRole("button", { name: "Gönder", exact: true })).toBeFocused();
    expect(await page.locator("nav:visible").evaluate(el => el.getBoundingClientRect().bottom <= innerHeight)).toBe(true);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  });
}

test("secret inputs have associated labels and stay named after reveal", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: "Ayarlar", exact: true }).last().click();
  const dialog = page.getByRole("dialog", { name: "Ayarlar" });
  const secrets = dialog.locator('input[type="password"]');
  expect(await secrets.count()).toBeGreaterThan(0);
  for (const input of await secrets.all()) {
    expect(await input.evaluate(el => [...el.labels].some(label => label.textContent.trim()))).toBe(true);
  }
  const key = dialog.getByLabel("Anahtar (opsiyonel)", { exact: true });
  await key.fill("ui-test-placeholder");
  await key.locator("..").getByRole("button", { name: "Göster", exact: true }).click();
  await expect(key).toHaveAttribute("type", "text");
  await expect(key).toHaveAccessibleName("Anahtar (opsiyonel)");
});

test("settings contains keyboard focus, closes with Escape and restores focus", async ({ page }) => {
  await page.goto("/");
  const opener = page.getByRole("button", { name: "Ayarlar", exact: true }).last();
  await opener.click();
  const dialog = page.getByRole("dialog", { name: "Ayarlar" });
  await expect(dialog).toHaveAttribute("aria-modal", "true");
  const close = dialog.getByRole("button", { name: "Kapat", exact: true });
  await expect(close).toBeFocused();
  await close.press("Shift+Tab");
  expect(await dialog.evaluate(el => el.contains(document.activeElement))).toBe(true);
  await page.keyboard.press("Tab");
  await expect(close).toBeFocused();
  await page.keyboard.press("Escape");
  await expect(dialog).toHaveCount(0);
  await expect(opener).toBeFocused();
});
