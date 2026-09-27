const { test, expect } = require('@playwright/test');
const fs = require('node:fs');
const path = require('node:path');
const html = fs.readFileSync(path.join(__dirname, '../../app/androidApp/build/navigation-diagram/index.html'), 'utf8');

test('fits, pans, zooms and keeps node selection after a drag', async ({ page }) => {
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.setViewportSize({ width: 900, height: 700 });
  await page.setContent(html);
  await page.getByRole('button', { name: '全体を表示', exact: true }).click();
  const viewport = page.locator('#viewport');
  await expect.poll(() => viewport.evaluate(e => e.scrollWidth <= e.clientWidth + 1 && e.scrollHeight <= e.clientHeight + 1)).toBe(true);
  await page.locator('#zoom').evaluate(e => { e.value = '100'; e.dispatchEvent(new Event('input', { bubbles: true })); });
  await viewport.evaluate(e => { e.scrollLeft = 200; e.scrollTop = 200; });
  await viewport.scrollIntoViewIfNeeded();
  const box = await viewport.boundingBox();
  const before = await viewport.evaluate(e => ({ x: e.scrollLeft, y: e.scrollTop }));
  await page.mouse.move(box.x + 150, box.y + 150);
  await page.mouse.down();
  await page.mouse.move(box.x + 200, box.y + 180, { steps: 8 });
  await page.mouse.up();
  const after = await viewport.evaluate(e => ({ x: e.scrollLeft, y: e.scrollTop }));
  expect(after.x).toBeLessThan(before.x);
  expect(after.y).toBeLessThan(before.y);
  const width = await page.locator('#graph').getAttribute('width');
  await page.keyboard.down('Control');
  await page.mouse.wheel(0, -50);
  await page.keyboard.up('Control');
  await expect.poll(async () => Number(await page.locator('#graph').getAttribute('width'))).toBeGreaterThan(Number(width));
  await page.getByRole('button', { name: '全体を表示', exact: true }).click();
  await page.locator('.node[aria-label="HOME"]').click();
  await expect(page.locator('.node.selected')).toHaveCount(1);
  await page.getByRole('button', { name: '選択を解除', exact: true }).click();
  await expect(page.locator('.node.selected')).toHaveCount(0);
  expect(errors).toEqual([]);
});

test('fits in a narrow embedded viewport', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 400 });
  await page.setContent(html);
  await page.getByRole('button', { name: '全体を表示', exact: true }).click();
  await expect.poll(() => page.locator('#viewport').evaluate(e => e.scrollWidth <= e.clientWidth + 1 && e.scrollHeight <= e.clientHeight + 1)).toBe(true);
});
