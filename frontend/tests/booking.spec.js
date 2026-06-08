import { test, expect } from '@playwright/test';

test('首頁載入', async ({ page }) => {
    await page.goto('/');

    await expect(
      page.getByText('智慧會議室預約系統')
    ).toBeVisible();

    await expect(page.locator('body')).toContainText( '智慧會議室預約系統' );

});


test('使用者可以建立會議預約', async ({ page }) => {

    await page.goto('/');

    await page.getByLabel('會議室').click();
    await page.getByText('會議室 A', { exact: true }).last().click();

    await page.getByLabel('借用人').click();
    await page.getByText('鴕鳥', { exact: true }).last().click();

    // 1. 填寫「日期」
    await page.locator('#date').click();
    await page.locator('#date').press('Control+A');
    await page.locator('#date').press('Backspace'); // 先刪乾淨
    await page.locator('#date').pressSequentially('2026-06-09'); // 一個字一個字打
    await page.locator('#date').blur();

    // 2. 填寫「開始時間」
    await page.locator('#start').click();
    await page.locator('#start').press('Control+A');
    await page.locator('#start').press('Backspace');
    await page.locator('#start').pressSequentially('15:00');
    await page.locator('#start').blur();

    // 3. 填寫「結束時間」
    await page.locator('#end').click();
    await page.locator('#end').press('Control+A');
    await page.locator('#end').press('Backspace');
    await page.locator('#end').pressSequentially('16:00');
    await page.locator('#end').blur();

    await page.click('body');
    await page.getByRole('button', { name: '+ 送出預約（鎖定）', exact: true }).click();

    await expect(page.getByText('已鎖定時段')).toBeVisible();
});