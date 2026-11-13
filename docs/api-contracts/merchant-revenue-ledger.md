# Merchant revenue and wallet activity — SP-S3-PAY-03

- Owner: SPH
- last_verified: 2026-11-14
- Status: VERIFIED by source, Spring MVC/security tests and H2 repository test; not deployed or verified against VPS/MySQL.

## Read wallet activity

`GET /api/v1/merchant/revenue/ledger?page=0&size=20`

- Uses the existing gateway payment route `/api/v1/merchant/revenue/**` and payment security configuration.
- Requires `ROLE_MERCHANT`. Merchant identity comes exclusively from `AuthenticatedUser` established by the existing trusted gateway headers or validated JWT. There is no merchant selector; an extra `merchantId` query parameter cannot change ownership.
- Zero-based `page` defaults to 0. `size` defaults to 20 and must be in 1..100. Negative pages, out-of-range sizes and non-integer/overflowing values return HTTP 400 with the existing `ApiResponse` error envelope.
- Anonymous requests, untrusted identity headers, DRIVER and ADMIN are denied by existing service security (HTTP 403 in the MVC tests). Existing gateway authentication behavior is unchanged.
- Reads only `payment-service.wallet_ledger_entries`; orders by `createdAt DESC, id DESC`. No entity relations are serialized and no cross-service database reads occur.
- HTTP 200 uses the existing `ApiResponse<PageResponse<MerchantLedgerEntryResponse>>` envelope. `data` contains `content`, zero-based `currentPage`, `totalPages`, `totalElements`. An empty/out-of-range page has empty `content`.

Example `data`:

```json
{
  "content": [{
    "id": 81,
    "bookingId": null,
    "entryType": "WITHDRAWAL_HOLD",
    "grossAmount": 50000,
    "feePercent": 0,
    "feeAmount": 0,
    "netAmount": -50000,
    "note": "Giữ tiền rút",
    "createdAt": "2026-11-14T09:30:00"
  }],
  "currentPage": 0,
  "totalPages": 1,
  "totalElements": 1
}
```

`entryType` follows the existing enum: `SETTLEMENT_CREDIT`, `WITHDRAWAL_HOLD`, `WITHDRAWAL_REFUND`, `ADJUSTMENT`. `netAmount` is signed: positive adds to the wallet, negative deducts. `bookingId` and `note` may be null. Time uses the existing service's local datetime serialization convention. Bank details, merchant identity fields and the nested Payment entity are excluded.

This is the merchant wallet activity ledger already written by settlement/withdrawal flows, not an API for the full double-entry `ledger_journals`/`ledger_postings` accounting model. No guarantee is introduced that summing this legacy activity table reconstructs every historical/current wallet balance, especially opening balances and journal reversals.

## Existing revenue semantics preserved

`GET /api/v1/merchant/revenue` remains backward compatible:

- `totalGrossRevenue`: cumulative SUCCESS payment amounts.
- `totalPlatformFee`: cumulative activity-ledger fee amounts.
- `totalNetRevenue`: sum of signed `wallet_ledger_entries.net_amount`, including withdrawal holds/refunds and adjustments. It is not solely settlement revenue.
- `currentWalletBalance`: current wallet balance.
- `series`: daily successful payment amounts after applying the service's current fee calculation, filtered by dates. These are not necessarily already settled wallet credits. Daily booking counts are not a cross-period count of distinct completed bookings.
- The existing implementation accepts `granularity` but always returns daily points. The revenue screen requests DAY and adds existing daily points into month/year buckets locally, preserving the amounts and counts returned by the service.
- The date range does not filter the cumulative KPIs. Omitting dates preserves the service's default seven-day/latest-available window. Missing single bounds and malformed/reversed dates retain the existing service behavior; this task only blocks reversed fully specified ranges in the frontend.

## Verification and rollout

- `MerchantLedgerControllerTest`: four MVC tests through real SecurityConfig/InternalAuthFilter and the real query service, mocked repository; verifies ownership despite an extra merchant ID, signed DTO fields, default paging, empty response, validation, role denial and untrusted headers.
- `MerchantLedgerRepositoryTest`: one H2/JPA test verifies merchant isolation, paging and deterministic ordering of tied timestamps.
- `gradlew.bat test --console=plain`: BUILD SUCCESSFUL; 55 tasks, 41 up-to-date. Reports contain 603 tests, 597 passed and 6 skipped; payment contains 143 tests, 141 passed and 2 skipped. Existing skipped tests remain skipped.
- Frontend evidence: `smart-parking-app-v2/docs/09-audit/sp-s3-pay-03-report.md` in the sibling repository.
- Rollout requires the new payment endpoint before the frontend ledger can load. The revenue and ledger queries fail independently, so an unavailable ledger does not hide revenue.
- No schema/migration/event/dependency/auth changes. Rollback removes the additive controller/query/DTO and frontend integration; no data restoration is required.
- Human review/sign-off and deployment are pending.


## PR self-review — 2026-11-14

Reviewed on isolated main base 8c2ced3. Only the additive ledger endpoint/DTO/query/tests, checkout validation follow-up and deposit regression assertion are included. Preserved the demo-confirmation fix already merged into main; the old working checkout's other changes are excluded.

Authorization, merchant ownership, DTO privacy, existing gateway coverage, stable paging, signed amounts, API errors and compatibility were inspected. Checkout review added stored merchant/lot consistency and rejected a blank provider URL. Ten amount-consistency tests pass, including exact 4,000 VND requests and mismatch/failure paths. No blocking finding remains in this scope; human approval is pending.

Full backend gate passed after supplying the existing local MySQL credential via process environment at localhost:3306; no credential file was copied or committed. The initial missing-credential run failed the two Identity MySQL migration checks and was not treated as a pass. Logs remain local. No production deployment or live PayOS payment was performed.
