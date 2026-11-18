# Booking schedule contract

- Owner: SmartParkingHub; last verified from source: 2026-11-17.
- Implementation: `ScheduledReservationController`, `ScheduledSlotService`, `ParkingLotClient`, `BookingServiceImpl`, `ScheduledSlotLifecycle`.
- All responses use the existing `ApiResponse<T>` envelope. SpringDoc exposes the controller/record schemas through the existing `/v3/api-docs`.

## Public reads

`GET /api/v1/parking-lots/{id}/availability`

Query: `vehicleType`, `start`, `end`, optional `physicalSlotId`. Timestamps are Vietnam wall-clock ISO local datetime, for example `2026-11-20T08:00:00`. Start/end must be half-hour boundaries and end must be later. Returns capacity for the **entire** window, including the required gap. This read does not retain a place.

`GET /api/v1/parking-lots/{id}/timeline`

Query: `vehicleType`, ISO `date`, optional `physicalSlotId`. The server returns exactly 48 half-hour blocks for that Vietnam calendar day, including conflicts and cooldowns originating on adjacent days. Counts mean the ability to book that block with its gap; a longer duration still requires a separate full-window read.

Both return:

```json
{
  "timezone": "Asia/Ho_Chi_Minh",
  "blockMinutes": 30,
  "bufferMinutes": 30,
  "allocationMode": "PHYSICAL",
  "updatedAt": "2026-11-17T07:00:00",
  "remainingCapacity": 1,
  "slots": [{"id": "physical-slot-uuid", "code": "A01"}],
  "blocks": []
}
```

Availability returns `slots` and an empty `blocks`. Timeline returns `blocks` and an empty `slots`; its top-level remainingCapacity is 0 and is not a whole-day availability count. A block contains `start`, `end`, `remainingCapacity`, and `status`: `AVAILABLE`, `FULL`, `BUFFER` or `PAST`. `GROUP` returns no invented physical slots. When explicit physical booking is disabled by the existing feature flag, availability omits selectable slots but can still support automatic allocation in physical mode.

Reads are anonymous. No driver's identity, plate or booking identifier is included. Invalid dates/types/windows return 400, nonoperating lots are rejected, and the last write can return 409 even following a successful read.

## Booking create

Existing `POST /api/v1/bookings` retains its request fields, including optional `physicalSlotId`. New bookings validate the half-hour grid on the server and save `schedulePolicyVersion=1`. A repeated request ID with identical intent returns the original booking, including historical bookings; changing its intent returns conflict. Existing owner/vehicle validation, hold quotas, pricing, refund and payment contracts remain authoritative.

Booking DTO adds nullable `schedulePolicyVersion`, `checkInEarlyMinutes`, `bookingBlockMinutes`, `turnaroundMinutes`. Physical slot identifiers/codes already present on the backend are now retained by the FE schema. Policy 1 has early minutes 0; policy 0/null uses legacy entry rights. New paid QR deadlines never extend beyond the reserved end.

## Internal reservation writer

All `/api/v1/internal/**` operations require `X-Internal-Service-Token`. Booking uses its existing circuit-breaker dependency around the Parking Feign transport, with 3-second connect and 5-second read timeouts and an 8-second RPC time limit. HTTP 409 remains a domain conflict; network/5xx failures do not fabricate availability.

| Method/path under `/api/v1/internal/parking-lots/{id}/slots` | Body / behavior |
|---|---|
| POST `/reservations` | `bookingId`, `vehicleType`, optional `physicalSlotId`, `startTime`, `endTime`, `holdExpiresAt`. Returns existing `SlotHoldResponse` with server tariff snapshot and allocated physical slot if applicable. |
| POST `/reservations/{bookingId}/confirm` | Empty body. Idempotently mark a held schedule confirmed. Never revive a released reservation. |
| POST `/reservations/{bookingId}/occupy` | `{ "token": "attempt-uuid" }`. Check allowed entry time/current occupancy/cooldown; allocate actual slot. |
| POST `/reservations/{bookingId}/abort-occupation` | Same attempt token. Rollback compensation only; an old token cannot undo a newer attempt. |
| POST `/reservations/{bookingId}/complete` | `{ "checkoutTime": "..." }`. Idempotent exact actual exit; retain the cooldown. |
| POST `/release` | Existing body with vehicle type and booking ID. Scheduled reservations release only while unoccupied; completion callbacks cannot erase a cooldown. Legacy holds retain idempotent ledger release. |
| POST `/reservations/import` | Authenticated Booking snapshot for explicit legacy migration. Keeps the old hold while staging. |
| POST `/reservations/{bookingId}/activate-import` | Activate after Booking commits policy 0. Repeated delivery does not change capacity again. |

`GET /api/v1/internal/bookings/{id}/schedule-status` returns `status`, `checkoutTime`, `checkInTime`, `schedulePolicyVersion`, or status `ABSENT`. Failure/unknown is never evidence of free capacity. Reconciliation visits batches with an ID cursor, retries missed cancellations/checkout/legacy activation and does not blindly expire payment holds.

`POST /api/v1/internal/bookings/{id}/adopt-schedule` explicitly adopts one existing active booking. It additionally checks the service token at the controller, stages the legacy hold, commits policy 0 and activates after commit. A missing/inconsistent ledger produces conflict requiring manual reconciliation. Terminal historical bookings are not rewritten.
