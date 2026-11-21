# Parking image QA release — 2026-11-21

Scope: preserve 404 responses for image-only Accept headers; remove the eight unchanged metadata-only image placeholders from V2; persist the existing photo mount and Cloudinary environment wiring.

The owner confirmed the V2 demo files never existed. V12 matches each original ID, lot, filename, size, cover flag, content type and timestamps and requires a null CDN URL. It does not delete uploaded files or changed/CDN records. V2 remains unchanged. Existing real uploads stay in the persistent mount. Lots without real uploads have no photo gallery until their merchant uploads an actual photo.

Before release, back up the matching Parking-owned metadata rows and verify the runtime photo mount. Rollback the application to its prior image digest if health fails; preserve the photo directory. Restoring the backed-up metadata is a separate explicit data rollback, since an application rollback does not reverse Flyway. No booking, payment or other service data is touched.

V12 is the next migration on current main. The unmerged status/TRAILER worktree also reserves V12/V13 and must renumber its migrations when rebasing before any future merge.

Verification: the image-only Accept regression failed before the handler fix. Release checks cover the full Parking suite, the cleanup's preservation/idempotence tests, Compose rendering and CI. Production acceptance checks the lot detail image list, the retained real image content, Flyway status and runtime image digest.
