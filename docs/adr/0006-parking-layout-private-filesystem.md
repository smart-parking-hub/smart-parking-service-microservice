# ADR 0006 — Private filesystem storage for parking layouts on one VPS

Status: implementation selected by the user on 2026-11-13; production rollout and overall L04 acceptance remain pending. Supersedes the Firebase deployment choice in ADR 0004; does not approve the outstanding physical-booking findings.

## Context and decision
Firebase Storage requires billing setup that the user does not want to fund. The repository targets resource-constrained VPS deployments and already separates file operations behind `StoragePort`. Business records remain in each service's MySQL database. Source images and immutable revision JSON use a dedicated private filesystem root mounted from an external Docker volume. The persisted canonical JSON in MySQL remains available for the existing public-layout read path; this change does not remove that copy.

This is suitable for one `parking-lot-service` host with monitored disk and an independently restorable backup. It does not provide high availability, replication, or protection against loss of the whole VPS. Before multi-host scaling, replace the adapter with shared object storage and migrate/verify the objects; do not give each replica its own independent disk.

## Alternatives and technology policy
| Option | Decision |
|---|---|
| Managed object storage | Revisit for HA/multiple hosts; requires separate account/billing and ongoing costs |
| Self-hosted S3-compatible service | Deferred: adds daemon, resource use and operational work without a current multi-host requirement |
| Java NIO filesystem adapter | Chosen: no new library/service; reuses existing API and database metadata |

1. Concrete need: persistent uploads and revision snapshots without a Firebase prepayment or credentials.
2. Cost: existing VPS disk, backup storage and operator effort still cost money; no separate Firebase bill. No new daemon or dependency; each object is bounded to 5 MiB by default, with bounded transient byte arrays per concurrent operation. No RAM/image-size/VPS performance measurements are claimed. The former GCS adapter, configuration and SDK dependency were removed at the user's request; the application no longer includes a cloud provider.
3. Compatibility: Java 21 `Files`/`FileChannel`, existing Spring configuration binding, no framework or schema changes.
4. Simpler option: this filesystem adapter is simpler than operating an object-storage service for one host; RAM mock is not persistent storage.
5. Rollback: disable new layout/physical writes, preserve files and DB and retain code that releases existing holds. Do not switch to RAM or discard the volume.

## Object integrity and security
- Root must be absolute, pre-created, private to the service and outside any webroot. Reject symbolic/redirected path components and unsafe object keys. OS administrators and the service account are trusted writers; path checks do not defend against a malicious privileged process concurrently replacing directories.
- Write bounded bytes to a same-filesystem temporary file, flush the file and publish via `Files.createLink`. The immutable target must not exist. Unsupported hard links cause an error rather than a non-atomic copy fallback. Temporary files are cleaned individually; no scheduled recursive cleanup is introduced.
- Filesystem `generation` is a 64-character SHA-256 digest, not a GCS generation number. Size and Base64 CRC32C remain in the existing asset row. Reads compare bytes with metadata from MySQL and enforce size limits; no mutable sidecar is trusted.
- File flush plus a persistent volume is verified for adapter/service restart. Sudden power-loss guarantees depend on the host filesystem, directory-entry durability and storage hardware; those have not been certified by local Windows tests. Production needs restore tests and monitoring.
- Full disk, invalid configuration, checksum mismatch and missing files fail closed. Default flags off plus no root produces a disabled adapter, not an accepting RAM store. Mock/in-memory requires explicit `test` profile. The removed cloud provider is rejected at startup, preventing an accidental silent switch.
- Only backend APIs serve files under their existing owner/public checks. No public volume URL or static mount is added.

## Migration and operational limits
Never change provider against an existing asset database and assume the files follow it. New empty installations can select filesystem directly. Existing Firebase objects require an explicit export/import preserving object keys, verification of bytes and controlled updating of generation metadata before cutover. Existing mock-only objects are unrecoverable after their process exits; reupload from original sources. This change contains no migration command and makes no claim that the user's Firebase bucket is empty.

Back up MySQL and immutable objects as one recovery set while layout writes are paused. Store a verified copy outside the VPS; preserve the external volume across deployments and test restoration before rollout. See [the filesystem runbook](../deployment/parking-layout-filesystem.md).

References: [Docker volumes](https://docs.docker.com/engine/storage/volumes/), [Java 21 Files](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html).
