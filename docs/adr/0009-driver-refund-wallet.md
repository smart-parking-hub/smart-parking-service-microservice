# ADR 0009 — Driver refund wallet and recoverable deposit payments

Status: accepted by user approval of SP-PAY-01 plan on 2026-11-17; implemented locally, not deployed.

## Decision

Payment owns a separate driver wallet, backed by the existing double-entry journal. Merchant wallets remain unchanged. Driver funds are liabilities under DRIVER_WALLET_PAYABLE (available) and DRIVER_WALLET_HOLD (reserved). Journals and postings have a nullable driver_id; merchant_id is never repurposed as a driver identifier. driver_wallet_entries is a display projection pointing to immutable journals.

The wallet receives verified cancellation refunds and pays a whole booking deposit when sufficient. There is no top-up, cash withdrawal, peer transfer, mixed Wallet/PayOS payment or wallet final-payment option in this release. The UI explains these limits before cancellation consent.

New cancellation requests explicitly consenting to wallet refunds carry refundChannel=WALLET. Existing requests and clients without consent remain BANK_MANUAL. A deposit paid from the wallet returns to the wallet. The refund channel is fixed for the request; manual bank evidence/verification rejects wallet requests. Old payments/refunds never become opening wallet balances automatically.

## Accounting

| Operation | Debit | Credit |
|---|---|---|
| Reserve deposit | DRIVER_WALLET_PAYABLE | DRIVER_WALLET_HOLD |
| Capture confirmed deposit | DRIVER_WALLET_HOLD | CUSTOMER_DEPOSIT_ESCROW |
| Release rejected deposit | DRIVER_WALLET_HOLD | DRIVER_WALLET_PAYABLE |
| Declare refundable amount before settlement | CUSTOMER_DEPOSIT_ESCROW | REFUND_PAYABLE |
| Declare refundable amount after settlement | MERCHANT_PAYABLE + PLATFORM_FEE_REVENUE | REFUND_PAYABLE |
| Complete wallet refund | REFUND_PAYABLE | DRIVER_WALLET_PAYABLE |

If some refund funds remain in escrow, use those before reversing settlement. Refund reversals retain the original settlement rate and rounding, instead of using the current configured rate. Wallet transfers do not create a GATEWAY_CLEARING cash receipt or cash payout. Platform fees apply at settlement, not again when a driver reuses wallet funds.

## Transactions and recovery

The reserve transaction persists a PENDING payment and a wallet hold under the existing payment-attempt group lock and driver row lock. The booking confirmation RPC runs outside the payment transaction, with bounded connection/read timeouts and a circuit breaker. Booking persists an immutable decision receipt keyed by paymentId while holding the booking row lock. Retrying the same payment returns that decision even if booking cancellation or expiry happened afterward.

A second payment transaction captures or releases the hold exactly once, then saves payment status and outbox event together. Ambiguous RPC outcomes retain the hold and recover in batches. Recovery also runs while new wallet payments are disabled. Batch cursors prevent permanently ambiguous early rows from starving later work.

A cancellation racing capture persists a deferred WALLET refund request rather than relying only on Kafka retry retention. A separate recovery worker retries REQUESTED wallet refunds after capture. Invalid data, unavailable funding or a paused feature does not mint a wallet balance. Requests needing reconciliation can be retried by an authorized admin once funding/data conditions are valid.

Settlement/refund consumers use READ_COMMITTED so a transaction that waited for a payment group lock reads the newly committed journal/refund state. Daily settlement acquires group locks in stable booking/payment order before locking merchant wallets. Driver SQL balance updates are conditional on both balances staying nonnegative; unique journal keys and row locks enforce idempotency across application instances.

## Compatibility and rollout

Payment V19 and Booking V10 are additive. A payment source WALLET is added to the existing gateway enum and displayed as Ví tài xế. Existing PayOS endpoints keep their contracts; the sync endpoint does not contact PayOS for a wallet payment.

Deploy booking schema/controller first, then payment schema/service with DRIVER_WALLET_ENABLED=false, then gateway/frontend, and enable the wallet only after verification. The repository default is true for the integrated local feature; explicitly set false during staged rollout. Do not deploy a new frontend wallet flow against an older booking service lacking durable confirmation receipts.

On rollback, disable new wallet activity and keep a wallet-aware payment version to finish pending work. Retain schema, receipt records and journals. Never roll back to a binary unable to read WALLET after wallet data exists. Correct posted amounts through an audited compensating operation, not direct balance edits; the generic merchant journal reversal rejects driver journals.

## Verification and limits

DriverWalletIntegrationTest covers 16k refunds, 8k→6k reuse, insufficient funds, idempotency, timeout recovery, cancellation racing capture and partial settlement, zero refunds, historical rates, paused refunds and wallet/ledger projections. WalletPaymentConfirmationControllerTest covers durable decisions and rejected ownership/amount/token. Real MySQL tests cover V18→V19, V9→V10 and concurrent conditional debits. Browser tests use stubbed HTTP responses; real PayOS transfers and deployment are not part of this verification.
