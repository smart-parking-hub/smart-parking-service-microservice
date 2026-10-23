# Data Ownership and Consistency

## Ownership matrix
| Entity/table | Owning service | Who may write | Readers | Invariants | Migration path | Evidence |
|---|---|---|---|---|---|---|

## Rules
- Một service sở hữu việc thay đổi dữ liệu của nó.
- Service khác không ghi trực tiếp vào bảng owned bởi service khác.
- Nếu dùng chung DB server, vẫn tách account/schema/privileges theo khả năng.
- Không giả định foreign key xuyên service là lựa chọn mặc định.
- Ghi rõ eventual consistency và nguồn dữ liệu chính xác (source of truth).
- Đối với stock, balance, payment: nêu concurrency control, idempotency và audit trail.
- Migration phải có rollout compatibility plan.

## Backup / restore
- Backup frequency:
- Retention:
- Off-host copy:
- Encryption/access:
- Last restore test:
- RPO:
- RTO:
- Known gaps:
