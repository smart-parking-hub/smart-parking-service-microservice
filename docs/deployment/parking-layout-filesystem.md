# Parking layout filesystem deployment and recovery

This is source configuration and an operator runbook, not evidence of a VPS deployment or approval of L04 booking. The user's requested direction is MySQL plus private files on the existing VPS. No Firebase account, Google key/token, Blaze prepayment or frontend Firebase SDK is required for this provider.

## Data locations and configuration
| Data | Owner/location |
|---|---|
| Parking lots, layout metadata, asset checksums, slots/holds | parking-lot-service MySQL database |
| Bookings and physical-slot snapshots | booking-service MySQL database |
| Layout source images and immutable JSON revision files | private volume mounted only into parking-lot-service |

Do not generalize this change to gallery uploads, avatars or other services: their existing storage behavior is unchanged.

| Environment variable | Default | Meaning |
|---|---|---|
| `APP_PARKING_LAYOUT_ENABLED` | `false` | Existing layout feature flag |
| `APP_PARKING_LAYOUT_PHYSICAL_BOOKING_ENABLED` | `false` | Existing physical booking feature flag |
| `APP_PARKING_LAYOUT_STORAGE_PROVIDER` | `filesystem` | Default provider; `mock`/`in-memory` requires profile `test`; other providers are rejected |
| `APP_PARKING_LAYOUT_FILESYSTEM_ROOT` | empty | Absolute existing directory. Required when either flag is enabled. No root plus both flags off uses disabled storage and requires no credentials |
| `APP_PARKING_LAYOUT_MAX_OBJECT_BYTES` | `5242880` | Maximum object read/write size; original image validation still applies |
| `APP_PARKING_LAYOUT_FILESYSTEM_MIN_FREE_BYTES` | `67108864` | Free-space reserve checked before writing; not a disk quota or a concurrent-write reservation |
| `PARKING_LAYOUT_VOLUME` | `smartparking-layout-files` | External Docker volume name used by optional overlays |

Native local development: create a dedicated private directory outside source, then set the absolute root and provider in the service process environment. Do not put production files in a Git checkout, build directory, OS temp directory or the container's writable layer.

## Optional Compose overlays
- Base service `parking-lot-service`: `docker/compose/local/compose.layout-filesystem.yaml`; compatible service name for local and VPS-single overlays.
- Cluster service `smart-parking-parkinglot`: `docker/compose/vps-cluster/compose.layout-filesystem.yaml`.

Both keep feature flags off by default. Both require an external volume that the operator provisions separately; Compose must fail if it is missing, rather than create a new empty data volume unnoticed. Neither overlay was applied to a VPS. Validate the exact base + overlay combination with `docker compose ... config --quiet` before deployment.

Before rollout the operator must:
1. Confirm this deployment has one parking-lot-service host, the existing disk has enough capacity and supports same-filesystem hard links, and the chosen external volume points to the intended disk. Record baseline file count/size and asset database count.
2. Resolve the actual image runtime UID/GID and permissions for this volume and existing gallery/log paths. Current image hardening is not changed here; do not set a new UID blindly or make the volume world-writable. Only the service/administrator may mutate its paths; do not mount it into Nginx or expose a file browser.
3. For any preexisting layout asset rows, establish their current provider and perform an explicitly planned import/cutover if needed. This code does not move cloud/mock files or update stored generations automatically.
4. Verify off-host backups, MySQL/file recovery consistency and a restore into an isolated directory/database. Without this, a single disk failure can lose uploaded files.
5. Deploy reviewed images via the normal pipeline with flags off and the volume mounted; verify service health/configuration. Enable layout writes only after baseline L04 findings are resolved and accepted. Physical booking is a separate enablement step.

No deployment command is executed by this change. No Google billing account is closed or changed by switching the application's provider.

## Smoke and fault verification
With synthetic images and a local/test database only:
- Owner uploads PNG/JPEG, saves/publishes a valid layout and reads the permitted image through the API. Check DB object keys, generation, size and CRC32C against the written bytes.
- Recreate the service/container while preserving the volume and DB; verify the same source asset and publication. Adapter restart tests alone do not replace this container/live HTTP test.
- Wrong owner/unauthenticated callers remain denied; public source stays private unless explicitly published under existing rules. Do not expose filesystem paths in API responses.
- Same key writers: exactly one successful create. Simulate missing file, byte corruption, configured size limit, insufficient disk reserve and unwritable root; verify errors with no false upload success or pointer changes.
- Verify cancellation/expiry/release of existing holds does not require file storage to be online; the overall booking suite/review must still validate this invariant.

## Backup and recovery
Suggested initial operational target: daily coordinated backup and a verified off-host copy; the owner must accept the resulting recovery point/time before rollout. It is a target, not a configured schedule.

Pause new layout uploads/publishes at the API ingress or a controlled maintenance window (do not assume existing feature flags fully guard every endpoint until L04 review passes). Finish in-flight writes. Take the parking-lot MySQL consistent backup and the filesystem snapshot/copy as one named recovery set. Record object keys, byte sizes, SHA-256/CRC32C and a manifest; preserve file permissions. Copy encrypted backups to a different failure domain. Only then resume writes.

Restore into a separate root and isolated MySQL database, validate every referenced file and its metadata, then run owner/public smoke tests. A MySQL backup alone is insufficient; an uncoordinated file copy can omit newly referenced objects. Do not restore over the live volume without a separate approved recovery plan.

Monitor disk usage/free bytes, backup age/failures, integrity/read/write errors and object growth. Initial alert threshold can be 80% disk utilization; tune with observed usage and retention. The 64 MiB application reserve does not protect MySQL, logs or other services from a full VPS disk. No metrics/alerts or backup job are claimed to have been provisioned here.

## Rollback
Disable new layout/physical booking creation through a reviewed operational change, retain the volume and schema, and keep the code that drains existing physical holds. Do not revert to mock, remove the volume, delete assets, or downgrade to code unable to release existing holds. Restoring a previous image/provider is safe only with a matching verified asset/database recovery set. No automatic orphan cleanup is enabled; operators must prove no DB reference/in-flight publish exists before deleting individual orphan objects.
