# Deploy va kiem tra release VPS

## Cap nhat tu GitHub (khuyen dung)

Sau khi merge, theo doi run **moi cua commit main moi** trong `cd-deploy.yml`.
Dung Re-run cua commit cu van chay workflow cu; ban sua nay khong the sua nguoc run cu.
Workflow build 7 image, gan revision SHA, day tag rieng theo SHA/run/attempt,
chot digest, sau do cap nhat tung nhom app tren 3 VPS. Thanh cong chi khi moi
app duoc chon dang running dung image va `/actuator/health` tra `UP`.

De chu dong trien khai lai artifact da build:

1. Mo terminal moi trong checkout backend sach, HEAD bang `origin/main` moi nhat.
2. Chay `menu.bat`, chon **1.7**.
3. Chon nguon **2 - GITHUB CI** (hoac Enter).
4. Chon dich **1** de cap nhat ca 3 VPS; 2/3/4 cho tung VPS; 5 cho mot service.
5. Cho ket qua; chay **1.9** de doi chieu doc lap.

Nhan **0** o nguon hoac dich se quay lai menu. Khong nhan Enter lien tuc;
script hien trang thai va thoi gian cho. Ket qua loi se giu exit code khac 0.

Che do CI khong can Docker Desktop/local daemon de deploy. Can Git, Python
3.10+, GitHub CLI `gh` da dang nhap, SSH key va known_hosts cua tung VPS.
Ban kiem tra 1.9 can Docker CLI + buildx/manifest de doc registry; khong can daemon.
Checkout co file dang sua/chua commit se bi chan khi deploy de tranh gan nham SHA.

## Build tu may Windows

**1.7 -> 1 - LOCAL -> dich** tu dong mo Docker Desktop neu can, cho Linux
engine san sang, build JAR va image linux/amd64 roi push tag Local rieng.
Can Docker Desktop, Java 21, GHCR push auth va may du tai nguyen build.
Local khong ghi de tag CI/latest. Khong chap nhan SkipBuild/SkipPush trong che do nay.
Image Local chi duoc xac nhan khi revision, digest, deployment record va health khop.

```powershell
# Chi kiem tra SSH, khong deploy
.\scripts\deploy\deploy-vps.ps1 -Target all -Source CI -DryRun
# Dung artifact CI cho VPS 2
.\scripts\deploy\deploy-vps.ps1 -Target vps2 -Source CI
# Build va deploy rieng Payment tu may nay
.\scripts\deploy\deploy-vps.ps1 -Service payment-service -Source Local
# Kiem tra doc lap, luu JSON chi chua metadata duoc chon
python scripts/deploy/verify-vps-release.py --output build/release-check.json
```

Ket noi/phan bo nam trong `deploy.config.json`; SSH dung strict known_hosts.
Neu GHCR la private hoac VPS thieu quyen pull, dung muc dang nhap GHCR da co
voi token co quyen doc package. Khong dua token vao Git/file huong dan.
CI dung credential tam; khong luu GITHUB_TOKEN het han vao remote Git.

## Doc ket qua 1.9

| Trang thai | Y nghia |
| --- | --- |
| CURRENT | Dung artifact cua main/nguon da xac minh, container running va health UP. |
| STALE_ARTIFACT | Container running dung image cu/khac artifact mong doi. |
| STALE_SOURCE | Nguon release khac commit main hien tai. |
| MISSING | Khong co container running cho service; created/exited khong duoc tinh. |
| MULTIPLE_RUNNING | Co nhieu container running, khong the khang dinh ban dang phuc vu. |
| REGISTRY_MOVED | Tag latest khac digest artifact CI mong doi. |
| HEALTH_UNVERIFIED | Image khop nhung khong xac minh duoc health UP. |
| REVISION_MISMATCH | Image Local khong khai bao dung SHA nguon. |
| UNVERIFIED | Thieu bang chung hoac kiem tra GitHub/SSH/registry gap loi. |

`pending=N` dem moi container khong running (created/exited/restarting...), khong
phai so commit con thieu. Can xem trang thai cu the truoc khi don; khong tu xoa
container/volume chi de dua so nay ve 0. Exit 0 khi tat ca service CURRENT;
exit 1 khi con sai lech hoac khong the xac minh. Warning duoc in rieng.

## Gioi han va an toan

- Khoa workflow ngan hai run CI deploy chong; khoa host chung ngan CI/manual chong nhau.
- Pin digest; pull xong truoc khi recreate; cap nhat app bang `--no-deps --pull never`.
- Chi don container created chua tung start cua dung project/service/repository.
  Container mo ho bi chan truoc khi dung app hien tai.
- Khong down cluster, remove-orphans, prune image, xoa volume, reset DB hay upload env.
- Day la recreate tung app, co the gian doan ngan; khong cam ket zero downtime.
- Metadata truoc/sau luu trong `~/smartparking/.deploy-history/`. Khong tu rollback DB.
  Khi can rollback, dung imageRef/imageId truoc do qua override chi cho app bi loi,
  xac minh image con ton tai va health, khong chay lai nguyen workflow cu.
- Muc 1.7 cap nhat image/config Compose trong Git; khong dua env local len VPS.
  Thay doi Casso/Compose local chua phat hanh khong nam trong ban sua deploy nay.

Kiem thu: `python -m unittest discover -s scripts/deploy/tests -v`.
Windows kiem tra batch/PowerShell; Linux CI kiem tra flock va shell workflow that
voi Git/Docker/Python gia lap, khong cham VPS.
