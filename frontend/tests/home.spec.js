import { test, expect } from '@playwright/test';

test('首頁載入', async ({ page }) => {
    await page.goto('/');

    await expect(
      page.getByText('智慧會議室預約系統')
    ).toBeVisible();

    await expect(page.locator('body')).toContainText( '智慧會議室預約系統' );

});

test('進入首頁後應顯示預約表單', async ({ page }) => {
      await page.goto('/');

      await expect(
        page.getByText('通知對象：')
      ).toBeVisible();
});


test('可切換通知對象', async ({ page }) => {
    await page.goto('/');

    await page.getByTestId('notification-user-select').click();

    await page.getByText('鴕鳥').last().click();

    await expect(
      page.getByTestId('notification-user-select')
    ).toContainText('鴕鳥');
});