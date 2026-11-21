# Vercel QA Remediation Plan — 2026-11-21

Status: approved by user reply "làm đi"; local configuration work only. Production deployment and server writes are outside this approval.

## 1. Mục tiêu thay đổi
- Mục tiêu: Khắc phục cấu hình liên service gây HTTP 500 khi tạo booking và chuẩn bị cấu hình bền vững cho ảnh bãi bị thiếu.
- Microservice liên quan: `booking-service`, `parking-lot-service`, `payment-service`; VPS 2/3 Compose.
- Người đề xuất: QA follow-up from `smart-parking-app-v2/docs/09-audit/2026-11-21-vercel-qa-full-test-report.md`.

## 2. Phạm vi tác động
- Tệp tin sẽ tạo mới:
  - `docs/implementation/vercel-qa-remediation-plan-20261121.md`
- Tệp tin sẽ chỉnh sửa:
  - `docker/compose/vps-cluster/compose.vps2-data.yml`: pass Payment-to-Booking URL and shared internal token.
  - `docker/compose/vps-cluster/compose.vps3-core.yml`: pass Booking identity/payment URLs, shared internal token, Booking callback URL, and persistent parking-image mount/provider configuration.
  - `scripts/build-test/check-compose.py` and `scripts/build-test/tests/test_compose.py`: supply synthetic endpoint fixtures and assert required routes and storage wiring.
  - `parking-lot-service/.../api/GlobalExceptionHandler.java` and `ParkingLotImageControllerTest.java`: set an explicit JSON content type on application errors so a missing image with `Accept: image/*` stays HTTP 404 instead of escaping as HTTP 500.
  - `.env.example` and `docs/deployment-runbook.md`: document the cluster-only required endpoint names and preserve-media deployment requirements.
- Tác động Database Schema: none.
- Không đổi API/DTO hoặc booking policy. Existing uncommitted Compose changes in this task worktree are retained and reviewed.
- Image `img_lot1_cover.webp` is absent from the mounted VPS directory and the verified deployment backup. This plan does not invent or substitute image content; a verified original file is still required.

## 3. Đánh giá rủi ro
| Rủi ro | Mức độ | Biện pháp giảm thiểu |
|---|---|---|
| Required service URLs are absent from VPS 3 `.env`; adding required Compose variables will fail fast until routes are provisioned. VPS 3 currently has no Tailscale client. | High | Do not deploy this Compose until the operator provisions and probes secure routes for Booking-to-Identity, Booking-to-Payment, and Payment-to-Booking. Keep values outside Git. |
| Parking image content remains 404 until the exact original asset is restored. | Medium | Restore only a source whose original name, bytes and SHA-256 match verified evidence; keep the persistent volume and prior backup. |
| Compose wiring may affect service startup. | Medium | Render `core` and `data` Compose scenarios with synthetic values; do not start or recreate containers. |

## 4. Kế hoạch kiểm thử
- Compose: run `scripts/build-test/check-compose.bat` and verify the `core` and `data` scenarios; include expected rejection when required endpoints/tokens are absent.
- Booking: no Java logic changes planned. Existing `:booking-service:test` is a regression gate if any Java changes become necessary.
- Parking: the new MockMvc missing-image regression failed before the handler fix; rerun the controller, image-service and exception-handler tests after the change.
- Image: verify the expected filename and SHA-256 against the approved original before any future restore; API acceptance requires HTTP 200 with the expected image bytes after an approved deployment.
- Frontend: already verified by the FE follow-up (vehicle component tests, typecheck, lint, build and 390/1440px E2E).

## 5. Kế hoạch rollback
- Local: revert only the Compose additions in this task worktree; preserve the original backend checkout's separate uncommitted photo configuration.
- Future deployment: use the previous verified Compose and image digest recorded by the release verifier; do not delete or recreate the parking-image data directory. If an asset restore is later approved, retain its verified pre-copy hash/backup and remove only the exact copied file if rollback is needed.
- No database migration, production container restart, network provisioning, file copy, or deployment is authorized by this plan.
