# Deployment Runbook

> Runbook là hướng dẫn cho người được ủy quyền. Agent không tự chạy các bước thay đổi production.

## Pre-deploy
- [ ] Xác nhận environment/host/branch/image digest.
- [ ] CI xanh và artifact đúng version.
- [ ] Review diff, migration, config và secret changes.
- [ ] Backup/restore point phù hợp.
- [ ] Đánh giá downtime, backward compatibility và rollback.
- [ ] Có phê duyệt và cửa sổ triển khai nếu cần.

## Deploy
1. Lệnh chính xác (điền theo môi trường thật):
2. Tác dụng của lệnh:
3. Expected output:
4. Stop condition:
5. Rollback command/procedure:

## Post-deploy
- [ ] Container/process healthy.
- [ ] Chỉ các port dự kiến đang listen.
- [ ] Health endpoint trả kết quả đúng.
- [ ] Smoke test an toàn.
- [ ] Error rate/latency/logs bình thường.
- [ ] Background consumers/jobs hoạt động.
- [ ] Không có migration hoặc queue backlog bất thường.

## Rollback
- Trigger:
- Who approves:
- Steps:
- Data compatibility limitations:
- Verification:
- Escalation:
