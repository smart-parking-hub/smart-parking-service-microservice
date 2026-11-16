# Booking timestamps and charges

Owner: SPH backend. Last verified: 2026-11-17.

Booking API wall-clock times are interpreted in Asia/Ho_Chi_Minh (UTC+7). Creation validation, the payment deadline (`now + 15 minutes`), deposit confirmation through the internal API and Kafka listener, check-in, check-out, unpaid expiration (existing three-minute grace) and no-show expiration explicitly use `BookingRefundPolicy.BUSINESS_ZONE` independently of the host's default timezone. No schema or endpoint change; historical rows are not rewritten.

`BookingServiceApplication.init()` also sets the JVM default timezone to Vietnam time at startup. An operating system configured for UTC therefore does not by itself mean that the running application's JVM remains on UTC. The explicit zone above makes these operations independent of that mutable global default, including direct unit tests or a later change to the JVM timezone.

Deposit confirmation requires an unexpired hold (`expiresAt > now`); the scheduler's three-minute release grace does not extend the payment window. A late Kafka deposit event leaves the booking unchanged and logs that reconciliation is required. After confirmation the check-in deadline remains expected arrival + 60 minutes; when the event listener has no expected arrival, it uses Vietnam time + 2 hours.

Unpaid expiration sets `EXPIRED`; the no-show scheduler sets `CANCELLED` with the no-show reason and refund policy. Neither transition was changed by this timezone fix.

Evidence: `BookingServiceImpl`, `CheckInOutServiceImpl`, `BookingExpirationScheduler`, `InternalPaymentConfirmationController`, `PaymentEventListener`; UTC-host regressions in `BookingServiceImplTest`, `CheckoutBusinessTimeTest`, `BookingExpirationSchedulerTest`, `InternalPaymentConfirmationControllerTest`, `PaymentEventListenerTest`.

`ParkingCharge` bills every commenced hour in the regular interval. After expected departure, `chargeOvertime` bills every commenced 30-minute block at half the saved hourly rate multiplied by the saved `overtimePenaltyMultiplier`. There is no overtime grace period: any positive overtime starts the first block; exactly 30 minutes is one block, and any excess starts the next. Duration is not truncated to whole minutes before billing. If actual entry is after expected departure, overtime starts at actual entry. A multiplier of 1 still bills overtime in half-hour blocks at the ordinary rate. Configured peak hours apply the saved surge multiplier based on the start of each billing block, including windows crossing midnight. Money is rounded HALF_UP to VND after summing each interval's blocks. Deposit is 20% of the estimate, rounded HALF_UP to VND; checkout amount due is max(total minus deposit, 0).

At checkout, the regular fee is at least the charge for the full reserved interval (`expectedCheckInTime` to `expectedCheckOutTime`), calculated using the saved hourly rate and peak tariff. Late arrival or early departure does not reduce that fee. The system takes the maximum of this reserved fee and the existing actual regular-time fee, preserving billing for early arrival. Legacy bookings missing a reserved timestamp retain actual-time billing. The minimum applies to the regular fee before adding overtime, so overtime is charged in addition to the full reservation even when actual regular parking was short. The reservation estimate and 20% deposit at booking creation are unchanged; the final total and remaining balance are settled only at checkout. `parkingMinutes` continues to show actual elapsed parking time.

For an 08:00-10:00 reservation at 20,000 VND/hour, entry at 08:00 and exit at 08:10 still costs 40,000 VND. With an 8,000 VND deposit, checkout requires 32,000 VND more. Entry at 08:30 and exit at 08:40 also costs 40,000 VND. Saved peak-hour multipliers apply to the entire reserved interval even if the driver leaves before the peak window.

Example covered by regression: 20,000 VND/hour, 70 regular minutes plus 20 overtime minutes at ×2 yields 40,000 + 20,000 = 60,000 VND when the full reserved fee is no higher than 40,000 VND. A 10,000 deposit leaves 50,000 due. The regular and overtime intervals still round independently, using 60-minute and 30-minute blocks respectively.

For a booking from 08:00 to 10:00, actual entry at 08:30 and exit at 10:05, with 20,000 VND/hour and overtime ×1.5: regular parking costs 40,000 VND, and 5 minutes overtime costs 15,000 VND (one 30-minute block), totaling 55,000 VND. Exit at 10:35 instead incurs 30,000 VND overtime (two blocks), totaling 70,000 VND. These totals also apply to entry at 09:30: late arrival does not reduce the reserved fee. Booking re-entry and deposit rules are unchanged. Regressions: `ParkingChargeTest`, `CheckInOutServiceImplTest`, `BookingServiceTest`, `GoldenPathCheckInTest`, `CheckoutBusinessTimeTest`.

Check-in remains guarded by a pessimistic booking lock, merchant/lot ownership and PENDING_CHECKIN status. A repeated request cannot change check-in time or publish another checked-in event. Automated tests verify one save/event.

Rollback: restore the previous application artifact if needed; no database rollback. Existing malformed historical timestamps, if any, require separate evidence-based reconciliation and are not auto-adjusted.
