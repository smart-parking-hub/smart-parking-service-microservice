# Production Incident Report — Vercel QA 2026-11-21

## 1. Thông tin sự cố
- Incident ID: `INC-20261121-VERCEL-QA`
- Thời gian phát hiện: 2026-11-21; production log window checked read-only for the preceding 48 hours.
- Severity: CRITICAL for booking creation; MEDIUM for the lot image.
- Services: `booking-service`, `parking-lot-service`, `payment-service` configuration.
- Symptom: `POST /api/v1/bookings` returns HTTP 500; lot 1 image content cannot be read.

## 2. Chuỗi bằng chứng
- Correlation/trace ID: unavailable in the QA screenshot and the Vercel error response; the response exposed no correlation header.
- Booking runtime: Booking logs show `ResourceAccessException` for `GET http://localhost:8081/api/v1/vehicles/{id}` with connection refused. `OwnedVehicleClient` defaults to `http://localhost:8081` when `IDENTITY_SERVICE_URL` is absent.
- Booking/Parking runtime: neither container has `APP_INTERNAL_SERVICE_TOKEN` in its environment. The server `.env` contains the token name but no Booking service URL variables. The VPS 3 host has no `tailscale` command/client. Required private/TLS service endpoints are not configured and their reachability remains unverified.
- Image runtime: the parking container has `/home/deployfriend/smartparking/data/parking-lot-images` mounted at `/app/uploads/parking-lot-images`, but `img_lot1_cover.webp` is absent. Parking logs return `[ERR_404] Không tìm thấy file ảnh` for the content request. The deployment backup contains four UUID-named files, none with the expected seed filename; no exact original image was found in the checked local source locations.
- Vercel read-only probe at `2026-11-21T05:17:07Z`: HTTP 500 for the image content path. The QA screenshot records HTTP 404; the Parking service log confirms the underlying missing-file 404.
- The same request log shows `HttpMediaTypeNotAcceptableException: No acceptable representation` inside `GlobalExceptionHandler.handleAppException` for `Accept: image/*`. This explains why a missing image's 404 can become HTTP 500.

## 3. Root cause
- Booking: confirmed configuration failure. The deployed Booking container lacks `IDENTITY_SERVICE_URL`, so vehicle ownership validation calls localhost inside the Booking container and fails before booking persistence. Missing internal-token injection is a subsequent Booking-to-Parking failure that would remain after Identity routing is fixed.
- Image: confirmed missing asset. The database seed refers to stored file `img_lot1_cover.webp`; the mounted persistent directory does not contain it. An additional response-negotiation defect prevents the not-found JSON from being written when the caller accepts only images.
- Five whys summary: the frontend reaches the API; the API fails because required service connectivity was never passed to the containers, and the photo URL resolves to a stored filename with no corresponding file. This is not a frontend request-shape issue.

## 4. Đề xuất khắc phục
- Local Compose patch in `fix/infra-booking-vps-wiring` now requires explicit Booking-to-Identity, Booking-to-Payment, and Payment-to-Booking URLs; passes the shared internal token to the relevant containers; and retains the parking image mount/provider configuration.
- Compose validation uses synthetic `.invalid` URLs and never prints environment values. Production `.env` must be provisioned with secure reachable routes before any deployment. VPS 3 currently has no Tailscale client; no public plaintext service URL is proposed.
- The exact original `img_lot1_cover.webp` must be recovered from a verified source before restoring it to the mounted directory. No substitute image, metadata rewrite, or database change is proposed.
- Local Parking handler fix: explicitly mark application-error responses as JSON. A MockMvc test using `Accept: image/*` failed on the original code and passed after the fix; it asserts HTTP 404 and `ERR_404`. The controller/image-service/handler group passed all 17 tests. The full Parking suite and JAR build are additional release checks.
- Verification: `python scripts/build-test/check-compose.py --scenario core --scenario data --scenario edge` passed; `python -m unittest discover -s scripts/build-test/tests -p test_compose.py` passed (19 tests). FE checks are recorded in `smart-parking-app-v2/docs/09-audit/2026-11-21-vercel-qa-fix-report.md`.
- Full Compose check: all 24 scenarios passed, including two intentionally rejected combinations.
- Final Parking verification: `.\gradlew.bat :parking-lot-service:test :parking-lot-service:bootJar --no-daemon --console=plain` succeeded. JUnit XML reports 21 classes, 160 tests passed, 0 failures/errors/skips; the boot JAR was built. No Booking Java source or schema was changed, so a Booking-service Java suite was not rerun for this configuration-only change.

## 5. Rollback and status
- No VPS configuration, container, network, database, or media file was modified. There is no production rollback action for this local patch.
- If a future approved deployment fails, restore the prior verified Compose/image digest through the release rollback procedure and retain the existing persistent image directory. Do not remove the volume or alter image rows.
- Status: local Compose configuration, Parking application-error fix and tests are prepared in the existing worktree. Production booking recovery still requires secure route provisioning and an approved deployment. The image 404 still requires the exact original file. Neither production issue is declared fixed until its endpoint is verified after an approved release.
