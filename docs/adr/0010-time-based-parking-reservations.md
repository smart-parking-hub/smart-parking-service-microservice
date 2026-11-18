# ADR 0010 — Time-based parking reservations

- Date: 2026-11-17 (UTC+7).
- Status: implementation authorized by product owner in chat; local verification recorded in the completion report.
- Owners: booking-service and parking-lot-service.

## Decision

Parking-lot owns `slot_reservations`, separate from `current_slot_allocations` (actual physical occupancy) and the legacy `slot_holds` ledger. Booking owns reservation intent, lifecycle, money and the snapshotted schedule policy. All exchanges use authenticated internal HTTP; there is no cross-database SQL or foreign key.

Policy version 1 accepts half-hour boundaries and positive multiples of 30 minutes. Occupation begins no earlier than the reserved start. Each reservation protects `[start, end + 30 minutes)`. Exactly one 30-minute gap suffices between consecutive uses of the same physical slot. Group allocation uses the peak of simultaneous protected intervals. Physical allocation requires one compatible slot throughout the complete requested window.

Creation and lifecycle mutations take the layout row when present, the slot-group row, then the reservation/current-allocation rows. The group row exists even when a conflict query returns no reservations. Locking reads are used for lifecycle/configuration checks to avoid a stale MySQL repeatable-read snapshot after waiting for a lock.

Future holds do not decrement the current capacity counter. Actual occupation decrements it; checkout restores it, while the reservation continues to protect the cooldown. Unknown legacy holds and counter deficits remain unavailable until reconciled.

## Early arrival (2026-11-18)

With early arrival enabled, new holds use policy 2 and protect `[start - 15 minutes, end + 15 minutes)`. The booking grid remains 30 minutes and consecutive policy 2 reservations on the same slot still need a 30-minute separation. Entry can start 15 minutes early if the slot is ready. After checkout the protected end is `max(expectedEnd, actualCheckout) + 15 minutes`, so a late return can delay the next entry without taking away its reservation. Billing starts at actual entry using the existing tariff rules.

Policy 1 continues to protect `[start, end + 30 minutes)`. Policy 0 and null retain their original rules. Mixed versions use their own stored intervals, so a slot with an existing reservation may need more separation during transition. Existing holds are read in place.

## Failure handling

- A payment deadline alone never causes Parking-lot to sell a potentially paid hold again. Booking explicitly cancels/expires it; missed releases are reconciled from Booking's authenticated status API.
- Scheduled cancellation and checkout delivery occur after the Booking transaction commits. Lost delivery leaves the reservation conservatively blocked until reconciliation.
- Check-in acquires the actual slot before committing Booking. Rollback compensates using an attempt token, so an old rollback cannot undo a newer successful attempt for the same booking.
- Overdue occupied vehicles remain blockers until an authoritative actual checkout arrives. Existing later reservations are retained; conflicting admission is rejected for staff intervention.
- Cooldown ends at `max(expectedEnd, actualCheckout)` plus the stored policy version’s turnaround, independent of when a scheduler runs. It adds no reserved billable time.

## Legacy adoption

Policy null retains the legacy path. Explicit adoption stages an `IMPORTING` record while keeping the original all-day hold. Booking commits policy 0, preserving its exact times, price and early check-in entitlement. Only then does Parking-lot activate the staged schedule and release the old hold. Reconciliation retries activation after transport failure. No automatic bulk backfill runs at startup.

Policy 0 includes the legacy early-entry window and, for a pending confirmed booking, its original late-entry deadline. These wider protection windows can make some half-hour blocks unavailable. Policy 1 never inherits early check-in.

## Consequences

Two additive migrations are required: Parking V9 and Booking V12. Booking and Parking releases must be coordinated; the new Booking writer requires the new reservation API. The legacy hold API rejects a group that still has active scheduled reservations.

Fee policy remains unchanged: regular parking charges commenced hours, overtime charges commenced half-hours, and the deposit is 20%. UI booking blocks and fee units are separate and explicitly explained before booking.

Rollback after accepting multiple future reservations cannot simply restore code that only supports one hold per slot. Pause new creation while preserving the new validators/lifecycle for already accepted reservations; prefer a forward fix. See the rollout runbook.
