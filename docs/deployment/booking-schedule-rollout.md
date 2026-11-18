# Booking schedule rollout and rollback

- Owner: SmartParkingHub maintainers; date: 2026-11-17.
- Local p0 deployment completed on 2026-11-17 at 18:16 UTC+7: Parking V9, Booking V12. Production/VPS deployment is not recorded here. Evidence: `D:/DuAnOjt/audit-reports/booking-schedule-local-rollout-20261117/REPORT.md`.

## Retained local p0 startup

Use `scripts/dev/start-p0-local.bat`. It retains `compose.restore-p0.local.yaml` database URLs and appends `compose.schedule.local.yaml` and `compose.no-request-rate.local.yaml`, pinning the tested schedule images and the subsequent removal of booking request-rate blocking. It does not build or pull images. The helper exits successfully only after the application health endpoints report UP; startup timeout exits with code 1 and reports unready ports.

The schedule image tags are `smart-parking/parking-lot-service:schedule-local-20261117` and `smart-parking/booking-service:schedule-local-20261117`. The final local override selects `smart-parking/booking-service:no-request-rate-local-20261117`; omitting it would restore the old limiter. These tags are local artifacts, not published registry releases; build or retain the matching verified artifacts before using this local-data helper. Do not start the retained p0 dataset using only the base Compose file: that selects other database defaults and may select old images.

Local photo storage correction (2026-11-17): the schedule overlay mounts named volume `docker_parking_images` at `/app/uploads/parking-lot-images`. Lot-37 photo source files were recovered by matching original name/byte count, checked by SHA-256, and verified through their content APIs after mounting. Keep this volume across container replacement. Proof and the original source copies are retained under `D:/DuAnOjt/audit-reports/parking-detail-presentation-20261117`; no DB image metadata was rewritten. Filesystem assets must be preserved alongside database/artifact backups during future releases.

The deployment first proved V8→V9 and V11→V12 against private copies, preserving original records and applied migration checksums. Frozen rollback schemas and exact image IDs are recorded in the audit's `rollout-state.json`. Historical bookings were not adopted. Live read checks through Vite port 3000 return 200 for both timeline and availability. One existing hold recheck job advanced only its `checked_at` timestamps after startup; original booking times, money and statuses were unchanged.

## Release sequence

1. Back up each service-owned database and record its migration history/application artifact. Verify existing holds and actual occupancy; list any missing hold ledgers for manual resolution.
2. Verify database defaults match the historical Parking migration collation, `utf8mb4_unicode_ci`. A fresh MySQL 8.4 schema created with its default `utf8mb4_0900_ai_ci` can fail the historical V8 foreign key when altering the older hold table. Do not edit applied migration checksums to work around this.
3. Release Parking with additive V9 first. It accepts the existing hold/release API and the new reservation API. Release Booking with additive V12, then the FE. The new Booking writer requires the new Parking endpoint; avoid routing it to an old Parking instance.
4. New creates use policy 1. Existing null-policy bookings remain conservative all-day holds until completed/cancelled or explicitly adopted. Current availability counters still reflect actual occupation and legacy holds; exact future availability comes from the new API.
5. For legacy adoption, inspect an explicit set of booking IDs first. Set `SPH_INTERNAL_SERVICE_TOKEN` privately and run the supplied PowerShell tool without `-Apply` for read-only status output. After checking IDs, rerun with `-Apply`. Use the intended Booking service URL; default is local port 8083. No service queries another database.

```powershell
.\scripts\migrations\Convert-LegacyBookingSchedule.ps1 -BookingIds 101,102
.\scripts\migrations\Convert-LegacyBookingSchedule.ps1 -BookingIds 101,102 -Apply
```

The script stops on error. Repeating an adopted ID retries activation without a second capacity increment. Do not print/store the service token in reports. A committed adoption may temporarily remain `IMPORTING` if activation is interrupted; its old hold stays unavailable until reconciliation verifies Booking policy 0.

6. Verify the morning/afternoon reuse example, at least one paid booking, check-in at its allowed time, checkout/cooldown and cancellation. Monitor failed reconciliation/compensation logs and existing service health metrics. A vehicle remaining beyond its reservation must be resolved by staff before another vehicle uses that slot.

Reconciliation defaults on, with batches every 30 seconds. `app.schedule.reconciliation.enabled=false` disables the new recovery jobs for isolated tests; do not use it to bypass lifecycle work during normal operation. Existing physical-selection feature flags and storage requirements still apply. The current rollout is service-wide for new creates; this release does not add a per-lot enablement toggle.

## Early-arrival release (policy 2)

Release Parking and Booking with policy 2 support together, Parking first, then the compatible frontend. No schema migration or historical rewrite is required. Older Booking callers omit the version and continue creating policy 1. If an older Parking responds without a version, Booking saves policy 1. Verify a new paid ticket permits entry at T−15, still respects an occupied slot or late cooldown, and retains its existing check-out and pricing rules.

If an early-arrival release must be paused after issuing policy 2 tickets, retain policy 2 readers and gate/checkout behavior until all such reservations and cooldowns have ended. Rolling services back to pre-policy-2 builds during that window can change ticket display or turnaround behavior.

## Rollback

Before any new schedules exist, restore compatible application artifacts while leaving additive tables/columns in place. After accepting schedules, pause new writes and keep the new schedule validation/lifecycle active for accepted bookings. An old one-hold-per-slot application cannot safely represent those commitments. Do not drop V9/V12 data or restore an old database backup over newer payments/bookings. Prefer a forward fix, or wait until every new reservation/cooldown has ended and reconcile before reverting completely.

## Local test environments

`ScheduledReservationIntegrationTest` supports `SCHEDULE_TEST_JDBC_URL`, `SCHEDULE_TEST_DB_USER`, `SCHEDULE_TEST_DB_PASSWORD`. It only accepts a schema named `sph_schedule_test_*`, runs Flyway and validates the entity schema. Use a fresh schema with the collation above.

`ScheduleMigrationMySqlTest` accepts `SCHEDULE_BOOKING_UPGRADE_JDBC_URL` with the same credential variables and schema-name restriction. It refuses a nonempty schema, upgrades to Booking V11 then V12 and compares every existing booking's times, money and status. Neither test cleans an existing database.
