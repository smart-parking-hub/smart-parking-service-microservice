# Skill: Repository Discovery

## Khi dùng
Khi bắt đầu repo mới, sau khi context bị mất, hoặc trước nhiệm vụ có phạm vi rộng.

## Steps
1. Xác nhận root repo và working tree; không reset/stash tự động.
2. Đọc README, build files, module list, CI workflow, Docker/Compose, deployment manifests, env example.
3. Liệt kê service/module, entry points, ports, database, external dependencies từ source.
4. Xác định tests và cách chạy tests.
5. Xác định security entry points và secrets handling mà không in secret.
6. So sánh với `docs/` và đánh dấu stale/conflict.
7. Cập nhật `docs/00-project-context/PROJECT_PROFILE.md` và `docs/01-architecture/AS-IS.md` bằng bằng chứng.
8. Chỉ sau khi discovery hoàn tất mới lập kế hoạch sửa.

## Output
- Repo facts VERIFIED
- Facts INFERRED/UNKNOWN
- Service/module map
- Request/data flow map
- Build/test commands verified
- Risks and missing docs
- Không sửa code trong discovery trừ khi người dùng yêu cầu riêng.
