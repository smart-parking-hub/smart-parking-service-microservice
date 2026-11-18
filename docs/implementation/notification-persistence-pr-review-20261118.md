# Notification persistence PR review

Owner: SPH. Last verified: 2026-11-18.

Port local save commit `2da719e` onto `origin/main` (`d85dec7`). Scope is the
booking-created recipient field and notification Kafka processing; retain all
main changes to scheduled bookings, Cloudinary, Docker and SMTP configuration.

`BookingCreatedEvent.merchantId` is additive and copied from the booking owner
snapshot by the transactional outbox publisher. Historical messages without
that field keep driver-only delivery. No cross-database lookup is introduced.
Booking/payment/user listeners use the existing String JSON factory with three
fixed-delay retries and checked DLT publication. Storage failures propagate
through the transactional proxy so notification writes and processed-event
markers roll back together. Malformed JSON is non-retryable.

Review covered typed producer headers and raw outbox payloads, event-ID
deduplication, recipient ownership, exception propagation, DLT configuration,
and preservation of the SMTP/Cloudinary changes on main.

Verification: `gradlew.bat test --console=plain` with JDK 21 completed
successfully (55 tasks: 5 executed, 50 up-to-date). Gradle reused valid cached
results for unchanged task inputs, including the notification and booking test
tasks. This run does not establish real SMTP delivery or VPS acceptance.

Rollout: rebuild common-library dependents and deploy notification-service plus
booking-service through the normal release process; no database migration is
added. Confirm the new topic DLTs can be provisioned with the cluster's configured
partition/replica settings. Historical failures are not automatically replayed.
Rollback: revert the PR and rebuild affected services; retain DLT messages and
existing notification records. This task creates a PR, without deploying it.
