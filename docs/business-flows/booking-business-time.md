# Booking timestamps and charges

Owner: SPH backend. Last verified: 2026-11-15.

Booking API wall-clock times are interpreted in Asia/Ho_Chi_Minh (UTC+7). Creation validation, the payment deadline (`now + 15 minutes`), deposit confirmation through the internal API and Kafka listener, check-in, check-out, unpaid expiration (existing three-minute grace) and no-show expiration explicitly use `BookingRefundPolicy.BUSINESS_ZONE` independently of the host's default timezone. No schema or endpoint change; historical rows are not rewritten.

`BookingServiceApplication.init()` also sets the JVM default timezone to Vietnam time at startup. An operating system configured for UTC therefore does not by itself mean that the running application's JVM remains on UTC. The explicit zone above makes these operations independent of that mutable global default, including direct unit tests or a later change to the JVM timezone.

Deposit confirmation requires an unexpired hold (`expiresAt > now`); the scheduler's three-minute release grace does not extend the payment window. A late Kafka deposit event leaves the booking unchanged and logs that reconciliation is required. After confirmation the check-in deadline remains expected arrival + 60 minutes; when the event listener has no expected arrival, it uses Vietnam time + 2 hours.

Unpaid expiration sets `EXPIRED`; the no-show scheduler sets `CANCELLED` with the no-show reason and refund policy. Neither transition was changed by this timezone fix.

Evidence: `BookingServiceImpl`, `CheckInOutServiceImpl`, `BookingExpirationScheduler`, `InternalPaymentConfirmationController`, `PaymentEventListener`; UTC-host regressions in `BookingServiceImplTest`, `CheckoutBusinessTimeTest`, `BookingExpirationSchedulerTest`, `InternalPaymentConfirmationControllerTest`, `PaymentEventListenerTest`.

The tariff policy remains unchanged: `ParkingCharge` bills every commenced hour in the regular interval and separately in the interval after expected departure. Overtime applies the saved `overtimePenaltyMultiplier` only to that latter interval. A multiplier of 1 still bills extra parking time at the ordinary rate. Configured peak hours apply the saved surge multiplier. Deposit is 20% of the estimate, rounded HALF_UP to VND; checkout amount due is max(total minus deposit, 0).

Example covered by regression: 20,000 VND/hour, 70 regular minutes plus 20 overtime minutes at ×2 yields 40,000 + 40,000 = 80,000 VND. A 10,000 deposit leaves 70,000 due. Both portions round up independently, as before this fix.

Check-in remains guarded by a pessimistic booking lock, merchant/lot ownership and PENDING_CHECKIN status. A repeated request cannot change check-in time or publish another checked-in event. Automated tests verify one save/event.

Rollback: restore the previous application artifact if needed; no database rollback. Existing malformed historical timestamps, if any, require separate evidence-based reconciliation and are not auto-adjusted.
