import { test, expect } from '@playwright/test';

const today = new Date();
const tomorrow = new Date(today);
tomorrow.setDate(tomorrow.getDate() + 1);
const TARGET_DATE = tomorrow.toISOString().split('T')[0];
const START_TIME = '15:00';
const END_TIME = '16:00';

/**
 * 目的：因預訂會議室在此測試中反覆使用，利用function封裝後只需呼叫函式
 * @param {import('@playwright/test').Page} page Playwright 的頁面物件
 * @param {string} date 欲預約的日期 (例如: '2026-06-09')
 * @param {string} start 開始時間 (例如: '15:00')
 * @param {string} end 結束時間 (例如: '16:00')
 */
async function createBookingHelper(page, date, start, end) {

    await page.getByLabel('會議室').click();
    await page.getByText('會議室 A', { exact: true }).last().click();

    await page.getByLabel('借用人').click();
    await page.getByText('鴕鳥', { exact: true }).last().click();

    // 1. 填寫「日期」
    await page.locator('#date').click();
    await page.locator('#date').press('Control+A');
    await page.locator('#date').press('Backspace'); // 先刪乾淨
    await page.locator('#date').pressSequentially(date); // 一個字一個字打
    await page.locator('#date').blur();

    // 2. 填寫「開始時間」
    await page.locator('#start').click();
    await page.locator('#start').press('Control+A');
    await page.locator('#start').press('Backspace');
    await page.locator('#start').pressSequentially(start);
    await page.locator('#start').blur();

    // 3. 填寫「結束時間」
    await page.locator('#end').click();
    await page.locator('#end').press('Control+A');
    await page.locator('#end').press('Backspace');
    await page.locator('#end').pressSequentially(end);
    await page.locator('#end').blur();

    await page.click('body');
    await page.getByRole('button', { name: '+ 送出預約（鎖定）', exact: true }).click();
}


test('使用者可以建立會議預約', async ({ page }) => {
    await page.goto('/');
    await createBookingHelper(page, TARGET_DATE, START_TIME, END_TIME);

    await expect(page.getByText('已鎖定時段')).toBeVisible();

    // 驗證是否成功變成鎖定狀態
      await expect(page.getByText('已鎖定時段')).toBeVisible();

      // 額外驗證：畫面上應該要出現對應時間的 BookingBlock
      await expect(page.locator('.booking-block', { hasText: '鴕鳥' })).toBeVisible();
});

test('使用者可以對已鎖定的預約點擊「確認預約」', async ({ page }) => {
  await page.goto('/');
  //await createBookingHelper(page, TARGET_DATE, '15:00', '16:00');
  const myBooking = page.locator('.booking-block', { hasText: '鴕鳥' }).first();

  await myBooking.click();

  const confirmButton = page.getByRole('button', { name: '確認預約', exact: true });
  await expect(confirmButton).toBeVisible();
  await confirmButton.click();

  await expect(page.getByText('已預約')).toBeVisible();
});

test('使用者可以「取消預約」並將時段釋放', async ({ page }) => {
  await page.goto('/');
  const myBooking = page.locator('.booking-block', { hasText: '鴕鳥' }).first();
  await myBooking.click();

  const cancelButton = page.getByRole('button', { name: '取消預約', exact: true });
  await expect(cancelButton).toBeVisible();
  await cancelButton.click();

  // 驗證：畫面上那個寫著「鴕鳥」的預約方塊應該要「消失」
  await expect(myBooking).toBeHidden(); // toBeHidden 代表預期它不再出現在畫面上
});

test('使用者可以在確定後點擊「取消」', async ({ page }) => {
    await page.clock.setFixedTime(`${TARGET_DATE}T14:59:55`);

    const time1 = await page.evaluate(() => new Date().toLocaleString());
    console.log('【檢查點 1】剛進網頁時，瀏覽器時間為:', time1);

    await page.goto('/');

    // 建立預約（此時是 14:55，預約 15:00 的會議是允許的未來時間）
    await createBookingHelper(page, TARGET_DATE, START_TIME, END_TIME);

    // 先進行確定預約
    const myBookingCancel = page.locator('.booking-block', { hasText: '鴕鳥' }).first();
    await myBookingCancel.click();
    await page.getByRole('button', { name: '確認預約', exact: true }).click();

    // 利用模擬時間來轉跳時間
    const timeTravelInput = page.getByPlaceholder('跳轉至…');

    await timeTravelInput.click();
    await timeTravelInput.press('Control+A');
    await timeTravelInput.press('Backspace');

    const targetTimeStr = `${TARGET_DATE} 15:05:00`;
    await timeTravelInput.pressSequentially(targetTimeStr);
    await timeTravelInput.press('Enter'); // 按下 Enter 確認日期選擇

    console.log('【檢查點】目前組裝出來的時間字串為：', targetTimeStr);

    await page.getByRole('button', { name: '🚀 跳轉' }).click();

    // 測試取消
    await myBookingCancel.click();

    await page.waitForTimeout(1000);

    const allText = await page.evaluate(() => document.body.innerText);
    console.log('【檢查點 B】此時網頁畫面上所有的文字：', allText);

    // 尋找「取消」按鈕並點擊
    const cancelButtonAfter = page.getByText(/取.*消/).last();

    await expect(cancelButtonAfter).toBeVisible();
    await cancelButtonAfter.click();

      // 驗證取消成功
    await expect(myBookingCancel).toBeHidden();
    await page.getByRole('button', { name: '↩ 還原真實時間' }).click();
});


test('使用者在會議時間可以點擊「報到」', async ({ page }) => {
    await page.clock.setFixedTime(`${TARGET_DATE}T14:59:55`);

    const time1 = await page.evaluate(() => new Date().toLocaleString());
    console.log('【檢查點 1】剛進網頁時，瀏覽器時間為:', time1);

    await page.goto('/');

    // 建立預約（此時是 14:55，預約 15:00 的會議是允許的未來時間）
    await createBookingHelper(page, TARGET_DATE, START_TIME, END_TIME);

    // 先進行確定預約
    const myBookingSecond = page.locator('.booking-block', { hasText: '鴕鳥' }).first();
    await myBookingSecond.click();
    await page.getByRole('button', { name: '確認預約', exact: true }).click();

    // 利用模擬時間來轉跳時間
    const timeTravelInput = page.getByPlaceholder('跳轉至…');

    await timeTravelInput.click();
    await timeTravelInput.press('Control+A');
    await timeTravelInput.press('Backspace');

    const targetTimeStr = `${TARGET_DATE} 15:05:00`;
    await timeTravelInput.pressSequentially(targetTimeStr);
    await timeTravelInput.press('Enter'); // 按下 Enter 確認日期選擇

    console.log('【檢查點】目前組裝出來的時間字串為：', targetTimeStr);

    await page.getByRole('button', { name: '🚀 跳轉' }).click();


//      await myBookingSecond.click();
//      const cancelButton = page.getByRole('button', { name: '取消', exact: true });
//      await expect(cancelButton).toBeVisible();
//      await cancelButton.click();
//
//      await expect(myBooking).toBeHidden();
    // 再測試報到
    const myBookingForAttend = page.locator('.booking-block', { hasText: '鴕鳥' }).first();

    console.log('【檢查點 A】點擊前的區塊文字：', await myBookingForAttend.innerText());

    await myBookingForAttend.click();

    await page.waitForTimeout(1000);

    const allText = await page.evaluate(() => document.body.innerText);
    console.log('【檢查點 B】此時網頁畫面上所有的文字：', allText);

    // 尋找「報到」按鈕並點擊
    const checkInButton = page.getByText(/報.*到/).last();

    await expect(checkInButton).toBeVisible();
    await checkInButton.click();

      // 驗證報到成功
      await expect(page.getByText('已報到')).toBeVisible();
    await page.getByRole('button', { name: '↩ 還原真實時間' }).click();
});

