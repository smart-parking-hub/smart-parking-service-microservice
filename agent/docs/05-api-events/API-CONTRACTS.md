# API Contracts

## Endpoint inventory
| Method/path | Owning service | Auth required | Permission | Request/response DTO | Error codes | Consumers | Evidence |
|---|---|---|---|---|---|---|---|

## Contract rules
- API change phải tìm caller và test.
- Error schema/status code phải nhất quán.
- Pagination có giới hạn và sort/filter được kiểm soát.
- Không lộ entity/field nội bộ hoặc PII không cần thiết.
- Versioning/deprecation policy:
- OpenAPI source:
- Contract test location:

# Event catalog
| Event name/version | Producer | Consumers | Key | Schema | Delivery/ordering assumptions | Retry/DLQ | Idempotency | Evidence |
|---|---|---|---|---|---|---|---|---|

Không ghi event vào catalog nếu chưa xác minh source producer/consumer.
