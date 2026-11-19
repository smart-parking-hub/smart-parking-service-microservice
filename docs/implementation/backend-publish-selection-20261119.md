# Backend selective publication - 2026-11-19

## Objective and authorization

The user explicitly requested reviewing remaining backend changes, choosing reusable
work for Git, keeping machine-local artifacts ignored, then creating and pushing
a branch. Base is remote main `2f56c82189653d5a636def0b36cca57ea29f63f6`.
This request authorizes branch publication, not merging or production deployment.
The original dirty checkout remains untouched except scoped ignore rules and this
publication inventory; a separate worktree contains the selected branch.

## Selected scope

- Reusable local Compose capabilities, compatibility presets and repaired legacy
  build/bind paths. Preserve existing image versions, data/project/volume identities,
  app defaults, launcher command behavior, P0 overlays and payment lineage.
- Opt-in local integration launcher, menu 1.8/2.5, synthetic Compose checker/tests,
  truthful migration-readability check and a separate Compose CI job. Preserve the
  release-contracts and Java/MySQL CI jobs and the entire deployed CD workflow.
- Casso Identity-only env mappings with disabled/blank defaults, safe env template,
  explicitly opt-in live test and API usage documentation. No credentials or live
  provider calls in normal tests. No production Java or migration changes.
- Backend quick start/knowledge and annotated historical Compose audit, current
  usage/runbooks, final recovery result and this decision inventory.

## Retain locally / exclude

- Env values, provider credentials, build outputs, XML/coverage/logs, local runtime
  uploads, generated reports and deployment metadata stay ignored.
- Ignore exact known dated scratch-report families rather than all docs or tooling.
  Reusable conclusions are consolidated into runbooks and this inventory.
- Do not include the unrelated trailing backtick in the tracked local layout overlay.
  Use the valid main version in this branch; preserve original working bytes. A
  tracked source change cannot be hidden reliably by .gitignore.
- Release/menu source already present in main is not republished or reverted. In
  particular retain current deploy README/auth warnings and CD digest/lock guards.
- Existing tracked Trello/history tools are outside this publication; no blind
  delete, git rm, broad ignore, reset, stash or volume/container operation.

## Risks, tests and rollback

Configuration extraction can rebase build/mount paths or alter inherited env. Render
all supported scenarios with synthetic env; compare unchanged baseline presets and
allow only documented legacy paths plus five Casso env keys. Test old command plans
with mocked native commands; do not execute a local launcher against retained data.
Run release regression tests to preserve menu cancellation/exit behavior, parse
PowerShell/YAML/Bash and scan all added lines for secrets and broken repo links.
Run Java tests with live-provider mode absent; report MySQL/environment limitations
rather than hiding failures. Validate ignore rules still allow templates, source,
tests and durable handoff docs. Check the pushed branch points at the exact local
commit with a clean worktree. No merge/deploy is part of acceptance.

Rollback is reverting the scoped commits or abandoning this new branch. There are
no DB migrations, runtime env changes or production operations to roll back.

## Results

Selected publication: 37 files, split into a configuration/tool/test commit and a
documentation/ignore commit. Source commit: `293ff0f` (`refactor(infra): share local
Compose presets and checks`). Exact publication head is verified after push and
reported to the user; use `git log origin/main..HEAD` to inspect both commits.

| Check | Observed result |
| --- | --- |
| Compose/local launcher contracts | 18 passed, real synthetic rendering; no containers started. |
| Release/menu regressions on Windows | 47 discovered: 45 passed, 2 Linux-only skipped. |
| Existing rendered baselines | 17 compared; 14 equal after removing the five separately tested Casso keys; only approved legacy build/bind repairs in infra/apps/include. |
| Full Java tests | 960 discovered: 946 passed, 12 skipped, 2 failed; 0 errors. All 54 Gradle tasks executed, no cached task claim. |
| Java failure boundary | Only unchanged IdentitySchemaMigrationTest fresh/upgrade MySQL availability guards (lines 144/197); Docker/MySQL local is unavailable. |
| Casso adapter | 10 passed, live provider case skipped by default; no live mode supplied. |
| Syntax and entry points | YAML, embedded Bash, PowerShell parse and both Compose/migration wrappers from another directory passed. |
| Secrets / docs | Added-content scan and real local Casso value exclusion passed; added repo links resolve. |
| Ignore rules | 13 positive and 8 negative examples passed, including env vs template/source/docs. |
| Protected resources | CD workflow, current deploy helpers/guide, all P0 overlays, P0 launcher/helper and valid main layout overlay remain identical. |

The full Java command was `gradlew.bat test --continue --no-daemon --max-workers=2`
with no live-provider mode and DB constrained to loopback with a dummy test
password. It returned exit 1 in 13m18s because local MySQL was unavailable. Do not
describe the full suite as green. No schema/reset/checksum change was made to hide
those failures, and no Docker daemon/retained stack was started for this task.
Full Java/MySQL and the new Compose Linux CI job must pass on a future PR before
merge. Branch push alone does not trigger the current main/develop/PR-only CI or
main-only CD; this task creates no PR, merge or production rollout.

Local detailed evidence remains ignored under `build/publication-*.json` and
`build/publication-gradle-test.log`; normal consumers need only the reusable guides.
The original invalid layout edit and old deploy README draft remain local and visible
as tracked changes. .gitignore cannot suppress tracked edits; no assume-unchanged,
skip-worktree or git rm was used. Git ignore additions were also applied to the
original checkout without deleting or moving the existing scratch files.


## Per-file decisions from the original checkout

| File | Decision |
| --- | --- |
| `.env.example` | PUSH curated source/doc |
| `.github/workflows/cd-deploy.yml` | ALREADY main; no duplicate |
| `.github/workflows/ci-build.yml` | PUSH curated source/doc |
| `.gitignore` | PUSH curated source/doc |
| `AGENTS.md` | PUSH curated source/doc |
| `README.md` | PUSH curated source/doc |
| `docker/compose/README.md` | PUSH curated source/doc |
| `docker/compose/local/compose.discovery.yaml` | PUSH curated source/doc |
| `docker/compose/local/compose.infra.yaml` | PUSH curated source/doc |
| `docker/compose/local/compose.kafka.yaml` | PUSH curated source/doc |
| `docker/compose/local/compose.layout-filesystem.yaml` | KEEP local tracked edit; invalid backtick excluded |
| `docker/compose/local/compose.local-full.yaml` | PUSH curated source/doc |
| `docker/compose/local/compose.local.yaml` | PUSH curated source/doc |
| `docker/compose/local/compose.monitoring.yaml` | PUSH curated source/doc |
| `docker/compose/local/compose.quality.yaml` | PUSH curated source/doc |
| `docker/compose/local/docker-compose.apps.yml` | PUSH curated source/doc |
| `docker/compose/local/docker-compose.infra.yml` | PUSH curated source/doc |
| `docker/compose/overlays/README.md` | PUSH curated source/doc |
| `docker/compose/vps-cluster/compose.vps1-edge.yml` | PUSH curated source/doc |
| `docs/BACKEND-COMPOSE-AUDIT.md` | PUSH curated source/doc |
| `docs/BACKEND-KNOWLEDGE.md` | PUSH curated source/doc |
| `docs/BACKEND-START-HERE.md` | PUSH curated source/doc |
| `docs/api-contracts/merchant-verification.md` | PUSH curated source/doc |
| `docs/deployment-runbook.md` | PUSH curated source/doc |
| `docs/implementation/casso-env-validation-20261119.md` | IGNORE local scratch report |
| `docs/implementation/compose-maintenance-plan-20261119.md` | IGNORE local scratch report |
| `docs/implementation/dual-source-deploy-plan-20261119.md` | IGNORE local scratch report |
| `docs/implementation/menu-deploy-fix-plan-20261119.md` | IGNORE local scratch report |
| `docs/implementation/menu-progress-plan-20261119.md` | IGNORE local scratch report |
| `docs/implementation/vps-release-audit-20261119.json` | IGNORE local scratch report |
| `docs/implementation/vps-release-audit-20261119.md` | IGNORE local scratch report |
| `docs/implementation/vps2-recovery-20261119.md` | PUSH curated source/doc |
| `identity-service/src/test/java/com/smartparking/identity/infrastructure/client/CassoBusinessTaxRegistryTest.java` | PUSH curated source/doc |
| `menu.bat` | PUSH curated source/doc |
| `scripts/README.md` | PUSH curated source/doc |
| `scripts/build-test/check-compose.bat` | PUSH curated source/doc |
| `scripts/build-test/check-compose.py` | PUSH curated source/doc |
| `scripts/build-test/db-migrate-dry-run.bat` | PUSH curated source/doc |
| `scripts/build-test/tests/mock-local-launcher.ps1` | PUSH curated source/doc |
| `scripts/build-test/tests/test_compose.py` | PUSH curated source/doc |
| `scripts/build-test/tests/test_local_launcher.py` | PUSH curated source/doc |
| `scripts/build-test/verify-all.bat` | PUSH curated source/doc |
| `scripts/deploy/ConsoleStatus.ps1` | ALREADY main; no duplicate |
| `scripts/deploy/DockerDesktop.ps1` | ALREADY main; no duplicate |
| `scripts/deploy/README.md` | KEEP old local draft; use deployed main guide |
| `scripts/deploy/deploy-vps.bat` | ALREADY main; no duplicate |
| `scripts/deploy/deploy-vps.ps1` | ALREADY main; no duplicate |
| `scripts/deploy/release-runtime.py` | ALREADY main; no duplicate |
| `scripts/deploy/tests/mock-deploy.ps1` | ALREADY main; no duplicate |
| `scripts/deploy/tests/test_console_status.py` | ALREADY main; no duplicate |
| `scripts/deploy/tests/test_deploy_launchers.py` | PUSH curated source/doc |
| `scripts/deploy/tests/test_manual_deploy.py` | ALREADY main; no duplicate |
| `scripts/deploy/tests/test_release_runtime.py` | ALREADY main; no duplicate |
| `scripts/deploy/tests/test_verify_vps_release.py` | ALREADY main; no duplicate |
| `scripts/deploy/tests/test_workflow_failure.py` | ALREADY main; no duplicate |
| `scripts/deploy/verify-vps-release.bat` | ALREADY main; no duplicate |
| `scripts/deploy/verify-vps-release.py` | ALREADY main; no duplicate |
| `scripts/dev/start-local-integration.bat` | PUSH curated source/doc |
| `scripts/dev/sync-all.ps1` | PUSH curated source/doc |

The new CI Compose job and curated handoff updates are based on current main,
not a replacement of its release protections. Files already published in main
remain tracked; .gitignore is not used to hide their original-checkout history.
