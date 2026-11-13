# Withdrawal notifications — local verification, 2026-11-13

Owner: SPH. Status: implemented and verified on the local Docker stack. No VPS deployment. The runtime verification below describes the earlier local build; see the PR alignment note before deployment.

## PR alignment with main

The PR is based on main `5b1a197`. Main already includes Identity's timestamp fix as V6 and its operations outbox as V7; these migrations are preserved. The additional timestamp regression uses the existing V6. Main uses Payment V16 for operations outbox, so this PR adds the missing-hold migration as **V17**. V17 restores only the gap between pending withdrawal totals and existing net hold postings, avoiding double credits for holds already recorded by newer code. A regression covers partially recorded holds and rerunning the SQL.

The earlier local stack used a different migration numbering sequence. Its Flyway history must be reconciled through a separately reviewed migration transition before replacing that stack with this PR's image; do not run Flyway repair, edit historical checksums, or rename already applied migrations blindly. No database migration changes were made while preparing this PR. This deployment limitation is why the backend PR is a draft.

## Result

Creating a withdrawal publishes `WITHDRAWAL_REQUESTED` through the existing transactional operations outbox to eligible Admin recipients. Approving/rejecting publishes a separate `WithdrawalDecisionEvent` through the same outbox infrastructure, consumed by Notification and delivered only to the withdrawal owner. Processing locks the request row, so concurrent decisions cannot both settle/refund it. Notifications and processed markers commit together; unique event/recipient keys prevent duplicate delivery.

The merchant approval message states that the request was approved. It does not claim bank funds have arrived. The rejection message explains that held funds returned to the wallet. Decision events contain withdrawal ID, merchant ID, amount and currency, without bank account details or administrator free-text reasons.

## Runtime changes

Only Identity, Notification and Payment containers were updated, using isolated-build images tagged `withdrawal-notifications-20261113`. Existing DB URLs, internal authentication, PayOS and SMTP configuration were preserved. Identity additionally needed `KAFKA_BOOTSTRAP_SERVERS` because the old Compose definition did not configure its new snapshot producer. The maintained local apps Compose file now supplies the internal broker address.

Migration chains verified first on a separate MySQL 8.4/Kafka stack restored from backups:

- Identity V3 → V7. New V7 fixes the missing inherited `user_warnings.updated_at` column; the failure was observed during lab startup before the fix. Historical V4/V5 migrations were preserved.
- Payment V4 → V7. V5 converts ledger history; V6 adds operations outbox. New V7 preserves the separate hold-account liability of legacy PENDING withdrawals without changing available balances or request states.
- Notification V2 → V3, adding recipient projection and source-event uniqueness.

Before/after comparisons on both lab and active local databases confirmed identical wallet balances/versions and withdrawal IDs, amounts, states and timestamps. Pending hold totals matched; journal headers balanced.

Backups and deployment evidence are in `D:/DuAnOjt/audit-reports/withdrawal-notifications-20261113`; database dumps are in its ACL-restricted `private` directory and must not be committed or published. Original image IDs and the Compose image override are recorded for rollback. Keep additive schema and event records when reverting an application image; do not reset Flyway history or consumer offsets.

## Historical event recovery

`scripts/dev/recover-withdrawal-admin-event.mjs` is dry-run by default. It validates a single PENDING request, checks for existing request events, then requires `--apply` to write one outbox event. It never creates, approves, refunds or holds a withdrawal. Repeated runs are no-ops.

For local WD-28, event `legacy-withdrawal-requested:28` reached PUBLISHED and created exactly one notification for each current Admin ID 1, 2 and 3. Gateway and browser showed `Yêu cầu rút tiền mới #28`. A second apply produced no event. WD-28 remained PENDING and wallet data stayed unchanged. WD-26/WD-27 were not backfilled or processed.

## Verification

- Red producer tests reproduced both missing decision events before the fix.
- Gradle tests for common, Identity, Payment and Notification passed; the initial complete run contained 313 test cases, with 2 existing Payment tests skipped. Additional timestamp/legacy-hold migration regressions passed; Payment's full suite was rerun after its final changes.
- MySQL upgrade/startup and Kafka embedded retry/DLT tests passed. Notification API tests cover ownership, chronological ordering, duplicate event delivery and Merchant-only decision delivery.
- Real lab API flow created test-only WD-29 and WD-30: Admin request notification, concurrent approval (exactly one succeeds), rejection and two private Merchant decisions all passed. Wallet delta matched one approved amount; the rejected amount was refunded. These requests exist only in the lab database.
- Six API sort variants passed: four withdrawal orders and two notification chronological orders. Frontend browser verification used real lab APIs through a test proxy. Active local gateway/browser verification confirmed WD-28 and withdrawal ordering.
- Frontend complete suite passed 676 tests; the final sorting suite passed all 4 cases, including returning to page zero on order change. Build/TypeScript and ESLint passed.

## Boundaries

Notification pages refresh on navigation or Cập nhật; no polling, WebSocket, email or SMS was added. Merchant result notifications apply to decisions processed by the new Payment version. Existing layout constraints on narrow Merchant screens were observed during screenshot inspection and remain outside this backend/sorting change. Other uncommitted booking/parking-layout work was preserved; it was not included in these images.

PR validation on main 5b1a197: 26 targeted tests passed (Payment 11, Notification 14, Identity 1); Payment/Notification bootJar passed. Includes partial existing holds and repeat-migration coverage.
