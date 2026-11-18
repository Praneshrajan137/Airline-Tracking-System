import { expect, test } from '@playwright/test';

const candidates = [
  {
    fa_flight_id: 'fa-flight-old', ident: 'UAL123', status: 'Landed',
    scheduled_out: '2026-11-16T08:00:00-06:00', actual_out: '2026-11-16T08:05:00-06:00',
    scheduled_in: '2026-11-16T12:00:00-08:00', actual_in: '2026-11-16T12:10:00-08:00',
    origin: 'KORD', destination: 'KLAX', origin_name: "Chicago O'Hare", origin_city: 'Chicago',
    origin_timezone: 'America/Chicago', destination_name: 'Los Angeles International',
    destination_city: 'Los Angeles', destination_timezone: 'America/Los_Angeles',
    aircraft_type: 'B738', latitude: null, longitude: null, altitude: null, groundspeed: null,
    last_position_at: null, checked_at: '2026-11-16T20:10:00Z', data_source: 'Demo fixture',
  },
  {
    fa_flight_id: 'fa-flight-current', ident: 'UAL123', status: 'En-Route / In Flight',
    scheduled_out: '2026-11-17T08:00:00-06:00', actual_out: '2026-11-17T08:05:00-06:00',
    scheduled_in: '2026-11-17T12:00:00-08:00', actual_in: null,
    origin: 'KORD', destination: 'KLAX', origin_name: "Chicago O'Hare", origin_city: 'Chicago',
    origin_timezone: 'America/Chicago', destination_name: 'Los Angeles International',
    destination_city: 'Los Angeles', destination_timezone: 'America/Los_Angeles',
    aircraft_type: 'B738', latitude: 39.8283, longitude: -98.5795, altitude: 35000, groundspeed: 452,
    last_position_at: '2026-11-17T15:00:00Z', checked_at: '2026-11-17T15:02:00Z', data_source: 'Demo fixture',
  },
];

test('guest selects a dated instance before its summary is requested', async ({ page }) => {
  const requestedSummaries: string[] = [];

  await page.route('**/api/v1/flight/UAL123/candidates', (route) => route.fulfill({ json: candidates }));
  await page.route('**/api/v1/flight/UAL123/summary/**', async (route) => {
    requestedSummaries.push(route.request().url());
    await route.fulfill({
      json: {
        ident: 'UAL123',
        fa_flight_id: 'fa-flight-current',
        summary_text: 'Demo summary for the selected flight instance.',
        generated_at: '2026-11-17T15:02:00Z',
      },
    });
  });

  await page.goto('/');
  await expect(page.getByText('No account required')).toBeVisible();
  await page.getByRole('button', { name: 'UAL123' }).click();

  const choices = page.getByRole('group', { name: 'Matching flight instances' }).getByRole('button');
  await expect(choices).toHaveCount(2);
  await expect(page.getByText('Demo fixture · sample data')).toHaveCount(2);
  expect(requestedSummaries).toHaveLength(0);

  await choices.nth(1).click();
  await expect(choices.nth(1)).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByText('Demo summary for the selected flight instance.')).toBeVisible();
  await expect(page.getByText(/flight instance fa-flight-current/)).toBeVisible();
  expect(requestedSummaries).toHaveLength(1);
  expect(requestedSummaries[0]).toContain('/summary/fa-flight-current');
});
