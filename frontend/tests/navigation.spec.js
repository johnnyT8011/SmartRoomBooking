import { test, expect } from '@playwright/test';

test('預設為週視角', async ({ page }) => {
  await page.goto('/');

  await expect(
    page.getByRole('radio', { name: /週視角/ })
  ).toBeChecked();
});

test('可以切換到日視角', async ({ page }) => {
  await page.goto('/');

  await page.getByText('日視角').click();

  await expect(
    page.getByRole('radio', { name: /日視角/ })
  ).toBeChecked();
//  await expect(
//    page.getByText('日視角')
//  ).toBeVisible();
});


test('可以切換到月視角', async ({ page }) => {
    await page.goto('/');

    await page.getByText('月視角').click();

    await expect(
      page.getByRole('radio', { name: /月視角/ })
    ).toBeChecked();

//  await expect(
//    page.getByText(/月/)
//  ).toBeVisible();
});

test('上一週按鈕可以切換日期', async ({ page }) => {
  await page.goto('/');

  const title = page.locator('.nav-title');

  const before = await title.textContent();

  await page.getByRole('button', { name: '◀' }).click();

  const after = await title.textContent();

  expect(after).not.toBe(before);
});

test('下一週按鈕可以切換日期', async ({ page }) => {
  await page.goto('/');

  const title = page.locator('.nav-title');

  const before = await title.textContent();

  await page.getByRole('button', { name: '▶' }).click();

  const after = await title.textContent();

  expect(after).not.toBe(before);
});

test('回到今日按鈕可正常運作', async ({ page }) => {
  await page.goto('/');

  await page.getByRole('button', { name: '▶' }).click();

  await page.getByRole('button', { name: '回到今日' }).click();

  await expect(
    page.getByRole('button', { name: '回到今日' })
  ).toBeVisible();
});