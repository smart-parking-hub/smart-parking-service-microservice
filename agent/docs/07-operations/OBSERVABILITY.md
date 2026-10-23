# Observability

## Correlation
- Request/correlation ID:
- Trace propagation:
- Log format:
- PII/secret redaction:
- Retention:

## Metrics
- Request rate:
- Error rate:
- Latency p50/p95/p99:
- JVM heap/RSS/GC:
- CPU/memory/disk:
- DB connections/slow queries:
- Broker lag/retries:
- Business metrics:

## Alerts
| Signal | Threshold/window | Severity | Runbook | Owner |
|---|---|---|---|---|

## Debug flow
1. Find request/trace ID.
2. Locate edge/gateway logs.
3. Follow downstream service spans/logs.
4. Inspect DB/external dependency timings.
5. Verify final business state before retrying an operation.
6. Never retry payment/order blindly.
