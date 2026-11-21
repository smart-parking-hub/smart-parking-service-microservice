# Booking production RPC routing repair

Date: 2026-11-21 (UTC+7). Owner: backend and deployment maintainers.

## Evidence and scope

The production booking attempt at 18:55:22 returned 500 / ERR_500. Booking count
remained 9; no new booking or payment was created. Current container logs identify
`OwnedVehicleClient.require` failing to reach `localhost:8081`. Its constructor
uses `IDENTITY_SERVICE_URL` when supplied; the VPS3 Compose service omitted it.
This is deployment configuration, not a reason to bypass vehicle ownership.

At the start of this repair, main/release was `6d59f46` and the independent
release verifier reported 7/7 CURRENT. That checks artifacts and local health;
it does not demonstrate a successful booking transaction.

The patch changes three VPS Compose files and their configuration tests:

- Identity keeps its existing loopback port and adds an explicit Tailnet IP
  binding. There is no wildcard/public Identity binding.
- Booking receives required Identity/Payment URLs and the internal token.
- Parking receives Booking's Docker-network URL and the same internal token.
- Payment receives a required Booking URL and the internal token. Its booking
  verifier otherwise falls back to localhost in a different container.
- Existing API contracts, JWT/ownership checks, timeouts, databases, image data,
  provider settings, Redis, Kafka and application code are unchanged.

VPS3 now has Tailscale 1.104.7, active/enabled, hostname `smartparking-vps3`, and
operator `deployfriend`. Installation did not restart application containers.
DNS integration, route acceptance and Tailscale SSH are disabled. At the latest
initial verification the device was **NeedsLogin**, with no Tailnet IP. After
user authentication it reached **Running**, with both expected peers online.
VPS3 reached VPS1's private SSH listener and Payment's private health endpoint
(HTTP 200 / UP). Identity's private 8081 connection was refused, as expected
until its new binding is deployed. The existing internal tokens on all three
hosts were compared without disclosure and matched.
Do not record sudo passwords, login URLs or auth keys in Git.

## Required deployment values

Set these in each host's existing protected `~/smartparking/.env`. Use the actual
verified IPs from `tailscale ip -4`, not an address copied from test fixtures.

| Host | Variable | Required value |
| --- | --- | --- |
| VPS1 | `IDENTITY_TAILNET_IP` | Its assigned Tailnet IPv4 address |
| VPS3 | `BOOKING_IDENTITY_SERVICE_URL` | `http://<VPS1 Tailnet IP>:8081` |
| VPS3 | `BOOKING_PAYMENT_SERVICE_URL` | `http://<VPS2 Tailnet IP>:8084` |
| VPS2 | `PAYMENT_BOOKING_SERVICE_URL` | `http://<VPS3 Tailnet IP>:8087` |
| All relevant hosts | `APP_INTERNAL_SERVICE_TOKEN` | The existing shared internal secret, supplied securely |

Empty required values fail Compose rendering; there is no localhost/public
fallback for these cross-host RPCs. The checker supplies synthetic addresses and
credentials and never reads repository `.env` or inherited production values.
This does not change the existing gateway routing or public port policy of the
other services; wider network hardening is outside this repair.

## Rollout gate and order

The previous required-variable wiring was reverted because production values
and private routes had not been provisioned. Do not merge/deploy this repair
merely because configuration tests pass.

1. Authenticate VPS3 into the same Tailnet as VPS1/VPS2. Verify all three devices
   and the actual private host routes; no exit node or advertised subnet is needed.
2. Back up the affected host environment and Compose/release metadata with owner
   permissions preserved. Verify token consistency without printing secrets.
   Provision the values above and render each host's Compose quietly.
3. Release Identity's private bind first using the reviewed configuration and
   retained immutable image. From VPS3 verify the Identity health endpoint and
   an owned-vehicle read with a real driver session. Verify Payment reachability.
4. Deploy Payment, Parking and Booking wiring through the normal reviewed
   release procedure. Recreate only affected apps with dependencies preserved;
   no cluster down, infrastructure restart, volume change or database migration.
5. Verify cross-service authentication and all seven release artifacts/health.
   Submit an authorized future booking with an owned active vehicle, verify its
   returned record and slot hold, then cancel and verify release. Do not initiate
   a provider payment or claim that mock/unit tests prove this acceptance.

If a required private route cannot be verified, keep the PR unmerged and record
the concrete missing prerequisite. `NeedsLogin` is not a successful network test.

## Rollback

Before activation, retain each affected app's immutable image reference and
previous Compose/environment snapshot. If a recreated app fails acceptance,
restore those snapshots and recreate only that app using the retained image
with `--no-deps --pull never`; check local health and gateway reads. This is the
under-five-minute application rollback path and does not roll back a database.
Remove only the newly added Identity private binding if that phase is reverted.
Tailscale bootstrap is independent; stopping it before application wiring does
not require changing application containers. Never restore volumes or reset data.

## Verification evidence

Nine real Compose regression tests were run before the patch: they failed with
16 assertions/subtests, including missing Identity/Booking URLs, missing tokens
and the absent private bind. The same nine tests pass after the patch.
They also reject empty required configuration and isolate inherited credentials.
Run the full configuration and release-contract suites before opening/merging:

```text
python -m unittest discover -s scripts/build-test/tests -v
python -m unittest discover -s scripts/deploy/tests -v
```

The full local configuration suite passes 27 tests. The local release-contract
suite runs 47 tests: 45 pass and two Linux-only cases are skipped on Windows;
CI must exercise its applicable platform cases separately.

Full CI and production acceptance remain separate gates. At branch preparation,
**Booking is still OPEN/P0** and application wiring has not been activated.
Record the reviewed head, actual released SHA, CI result and live booking/cancel
evidence in the PR's release receipt and the private production retest report.
