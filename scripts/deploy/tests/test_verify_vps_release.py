"""Unit tests for the read-only release identity checks; no SSH or registry writes."""

import importlib.util
import contextlib
import io
import json
import os
from pathlib import Path
import sys
import tempfile
import time
import unittest
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location("verify_vps", Path(__file__).resolve().parents[1] / "verify-vps-release.py")
verify = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(verify)
DIGEST = "sha256:" + "a" * 64
CHILD = "sha256:" + "b" * 64
CONFIG = "sha256:" + "c" * 64
OLD = "sha256:" + "d" * 64
SERVICE = "smart-parking-booking"


class FakeTerminal(io.StringIO):
    def isatty(self):
        return True


def container(image=DIGEST, state="running", health="UP"):
    return {"service": SERVICE, "name": "booking", "state": state, "imageId": image, "health": health, "digests": []}


class ReleaseIdentityTest(unittest.TestCase):
    def setUp(self):
        self.registry = {"latestDigest": DIGEST, "acceptedImageIds": [DIGEST, CHILD, CONFIG]}

    def test_live_progress_erases_itself_and_restores_cursor_after_exception(self):
        stdout, stderr = io.StringIO(), FakeTerminal()
        with contextlib.redirect_stdout(stdout), patch.object(verify, "enable_terminal_updates", return_value=lambda: None):
            with self.assertRaisesRegex(RuntimeError, "EXPECTED_FAILURE"):
                with verify.ConsoleProgress(stderr, interval=0.01) as progress:
                    progress.stage(2)
                    print('{"value":42}')
                    time.sleep(0.04)
                    raise RuntimeError("EXPECTED_FAILURE")
            ended = stderr.getvalue()
            time.sleep(0.03)
        self.assertEqual('{"value":42}\n', stdout.getvalue())
        self.assertIn("[1/3]", ended)
        self.assertIn("[2/3]", ended)
        self.assertIn("[3/3]", ended)
        self.assertIn("Thoi gian: 00:00", ended)
        self.assertTrue(ended.endswith("\r\x1b[J\x1b[0m\x1b[?25h"))
        self.assertEqual(ended, stderr.getvalue())

    def test_redirected_progress_is_silent_and_contains_no_escape_sequences(self):
        stream = io.StringIO()
        with patch.object(verify, "enable_terminal_updates") as enable:
            with verify.ConsoleProgress(stream, interval=0.01) as progress:
                progress.stage(3)
                time.sleep(0.02)
        enable.assert_not_called()
        self.assertEqual("", stream.getvalue())

    def test_legacy_console_uses_one_line_and_clears_it_without_ansi(self):
        stream = FakeTerminal()
        with patch.object(verify, "enable_terminal_updates", return_value=None), patch.object(verify.shutil, "get_terminal_size", return_value=os.terminal_size((40, 24))):
            with verify.ConsoleProgress(stream) as progress:
                progress.stage(3)
        rendered = stream.getvalue()
        self.assertNotIn("\x1b", rendered)
        self.assertTrue(rendered.endswith("\r" + " " * 39 + "\r"))
        self.assertTrue(all(len(line) <= 39 for line in rendered.split("\r")))

    def test_cli_clears_progress_before_seven_service_results_and_preserves_exit_one(self):
        config = json.loads(verify.CONFIG.read_text(encoding="utf-8"))
        sha = "e" * 40
        release = {"mainSha": sha, "releaseSha": sha, "runId": 44463217045,
                   "digests": {name: DIGEST for name in config["services"]}}
        snapshots = {node: [] for node in config["nodes"]}
        for name, target in config["services"].items():
            active = container(OLD if target["node"] == "vps2" else DIGEST)
            active["service"] = target["composeService"]
            snapshots[target["node"]].append(active)
            if target["node"] == "vps2":
                pending = container(DIGEST, state="created")
                pending["service"] = target["composeService"]
                snapshots[target["node"]].append(pending)
        terminal = FakeTerminal()
        with contextlib.redirect_stdout(terminal), contextlib.redirect_stderr(terminal), patch.object(sys, "argv", ["verify-vps-release.py"]), patch.object(verify, "enable_terminal_updates", return_value=lambda: None), patch.object(verify, "gh_json", side_effect=[{"nameWithOwner": "example/repo"}, {"sha": sha}]), patch.object(verify, "expected_release", return_value=release), patch.object(verify, "node_snapshot", side_effect=lambda node: snapshots[node["id"]]), patch.object(verify, "registry_identity", return_value=self.registry):
            code = verify.console_main()
        self.assertEqual(1, code)
        output = terminal.getvalue()
        final = output.rsplit("\x1b[?25h", 1)[1]
        self.assertTrue(final.startswith("Current main " + sha + " / CI build 44463217045\n"))
        self.assertEqual(7, sum(line.startswith(("vps1 |", "vps2 |", "vps3 |")) for line in final.splitlines()))
        self.assertEqual(3, final.count("STALE_ARTIFACT | pending=1"))
        self.assertEqual(4, final.count("CURRENT | pending=0"))
        self.assertIn("[KET THUC BUOC] Kiem tra release VPS |", final)
        self.assertNotIn("\x1b", final)
        self.assertNotIn("[1/3]", final)
        self.assertNotIn("[DA DOC]", final)

    def test_registry_index_platform_and_classic_config_ids(self):
        for image in (DIGEST, CHILD, CONFIG):
            with self.subTest(image=image):
                self.assertEqual("CURRENT", verify.assess([container(image)], SERVICE, DIGEST, self.registry)["status"])

    def test_pending_new_image_does_not_make_old_running_container_current(self):
        result = verify.assess([container(OLD), container(DIGEST, "created")], SERVICE, DIGEST, self.registry)
        self.assertEqual("STALE_ARTIFACT", result["status"])
        self.assertEqual("created", result["nonRunning"][0]["state"])

    def test_latest_tag_string_is_not_release_evidence(self):
        active = container(OLD)
        active["imageRef"] = "ghcr.io/example/booking-service:latest"
        self.assertEqual("STALE_ARTIFACT", verify.assess([active], SERVICE, DIGEST, self.registry)["status"])

    def test_missing_and_duplicate_active_containers_fail(self):
        self.assertEqual("MISSING", verify.assess([container(state="created")], SERVICE, DIGEST, self.registry)["status"])
        self.assertEqual("MULTIPLE_RUNNING", verify.assess([container(), container()], SERVICE, DIGEST, self.registry)["status"])

    def test_registry_movement_and_health_are_separate_checks(self):
        moved = {**self.registry, "latestDigest": OLD}
        self.assertEqual("REGISTRY_MOVED", verify.assess([container()], SERVICE, DIGEST, moved)["status"])
        self.assertEqual("HEALTH_UNVERIFIED", verify.assess([container(health="UNKNOWN")], SERVICE, DIGEST, self.registry)["status"])

    def test_ci_log_pairs_each_service_with_its_own_digest(self):
        log = "unrelated latest: digest: " + OLD + "\nBuilding and pushing booking-service...\nlatest: digest: " + DIGEST + " size: 111\nBuilding and pushing payment-service...\nlatest: digest: " + CHILD
        self.assertEqual({"booking-service": DIGEST, "payment-service": CHILD}, verify.published_digests(log))

    def test_main_without_a_successful_build_never_uses_an_older_green_commit(self):
        main_sha = "e" * 40
        old_green = {"headSha": "f" * 40, "headBranch": "main", "databaseId": 1}
        with patch.object(verify, "gh_json", side_effect=[{"sha": main_sha}, [old_green]]) as gh:
            with self.assertRaisesRegex(RuntimeError, "No successful image build"):
                verify.expected_release("example/repo")
        self.assertEqual(2, gh.call_count)

    def test_registry_attestations_are_not_runnable_identities(self):
        import json
        manifests = [
            {"Descriptor": {"digest": CHILD, "platform": {"os": "linux", "architecture": "amd64"}}, "OCIManifest": {"config": {"digest": CONFIG}}},
            {"Descriptor": {"digest": OLD, "platform": {"os": "unknown", "architecture": "unknown"}}, "OCIManifest": {"config": {"digest": OLD}}},
        ]
        with patch.object(verify, "command", side_effect=[json.dumps({"digest": DIGEST}), json.dumps(manifests)]):
            result = verify.registry_identity("ghcr.io/example/booking-service", DIGEST)
        self.assertEqual({DIGEST, CHILD, CONFIG}, set(result["acceptedImageIds"]))


class LocalReleaseTest(unittest.TestCase):
    def setUp(self):
        self.sha = "e" * 40
        self.config = {"registry": {"imagePrefix": "ghcr.io/example"},
                       "services": {"booking-service": {"composeService": SERVICE, "node": "vps3"}},
                       "nodes": {"vps3": {}}}
        self.active = container()
        self.active.update(revision=self.sha, deployment={"source": "LOCAL", "sourceSha": self.sha,
                                                        "image": "ghcr.io/example/booking-service@" + DIGEST})
        self.registry = {"latestDigest": OLD, "acceptedImageIds": [DIGEST, CHILD, CONFIG]}
        self.ci = {"releaseSha": self.sha, "digests": {"booking-service": OLD}}

    def expectation(self, active=None, release=None, force_ci=False):
        return verify.service_expectation([active or self.active], "booking-service", self.config, self.sha, release, force_ci)

    def test_verified_local_does_not_inherit_the_ci_digest_or_latest_tag(self):
        expected = self.expectation(release=self.ci)
        self.assertEqual("LOCAL", expected["source"])
        self.assertEqual(DIGEST, expected["digest"])
        self.assertEqual("CURRENT", verify.assess_release([self.active], SERVICE, expected, self.registry, self.sha)["status"])
        self.assertEqual("CI", self.expectation(release=self.ci, force_ci=True)["source"])

    def test_local_stale_commit_wrong_revision_digest_and_health_fail(self):
        expected = self.expectation()
        cases = (("STALE_SOURCE", self.active, "f" * 40),
                 ("REVISION_MISMATCH", {**self.active, "revision": None}, self.sha),
                 ("STALE_ARTIFACT", {**self.active, "imageId": OLD}, self.sha),
                 ("HEALTH_UNVERIFIED", {**self.active, "health": "UNKNOWN"}, self.sha))
        for status, active, main in cases:
            with self.subTest(status=status):
                self.assertEqual(status, verify.assess_release([active], SERVICE, expected, self.registry, main)["status"])

    def test_incomplete_local_record_cannot_fall_back_to_a_green_ci_digest(self):
        for field, value in (("image", None), ("image", "ghcr.io/other/booking-service@" + DIGEST), ("sourceSha", None)):
            with self.subTest(field=field, value=value):
                active = {**self.active, "deployment": {**self.active["deployment"], field: value}}
                with self.assertRaisesRegex(ValueError, "Invalid verified LOCAL"):
                    self.expectation(active, self.ci)
        without_record = {**self.active, "deployment": None}
        with self.assertRaisesRegex(ValueError, "No verified LOCAL"):
            self.expectation(without_record)

    def test_local_registry_lookup_never_requires_latest(self):
        with patch.object(verify, "command", return_value=json.dumps({"Descriptor": {"digest": CHILD}, "SchemaV2Manifest": {"config": {"digest": CONFIG}}})) as call:
            result = verify.registry_identity("ghcr.io/example/booking-service", DIGEST, False)
        call.assert_called_once()
        self.assertNotIn(":latest", str(call.call_args))
        self.assertIn(CONFIG, result["acceptedImageIds"])

    def test_verified_local_can_pass_without_any_successful_ci_build(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "config.json"
            path.write_text(json.dumps(self.config), encoding="utf-8")
            with patch.object(verify, "CONFIG", path), patch.object(sys, "argv", ["verify-vps-release.py"]), patch.object(verify, "gh_json", side_effect=[{"nameWithOwner": "example/repo"}, {"sha": self.sha}]), patch.object(verify, "expected_release", side_effect=RuntimeError("No build for current main")), patch.object(verify, "node_snapshot", return_value=[self.active]), patch.object(verify, "registry_identity", return_value=self.registry), contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(0, verify.main())

    def test_remote_history_is_bound_to_exact_running_container_and_target_directory(self):
        active = {"Id": "full-current-id", "Image": CONFIG, "Name": "/booking", "State": {"Status": "created"},
                  "Config": {"Image": "ref", "Labels": {"com.docker.compose.service": SERVICE}}}
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            history = root / ".deploy-history"
            history.mkdir()
            record = {"source": "LOCAL", "sourceSha": self.sha, "verified": {SERVICE: "old-id"},
                      "expected": {SERVICE: {"image": self.active["deployment"]["image"]}}}
            path = history / "20261119.json"
            path.write_text(json.dumps(record), encoding="utf-8")
            def read(args):
                return "full-current-id" if args[1:3] == ["ps", "-aq"] else json.dumps([active] if args[1] == "inspect" else [{"Id": CONFIG}])
            namespace = {"__name__": "remote_test"}
            stdout = io.StringIO()
            with patch("pathlib.Path.cwd", return_value=root), patch("subprocess.run") as run, contextlib.redirect_stdout(stdout):
                run.side_effect = lambda args, **kwargs: type("Result", (), {"returncode": 0, "stdout": read(args)})()
                exec(verify.REMOTE, namespace)
            self.assertNotIn("deployment", json.loads(stdout.getvalue())[0])
            record["verified"][SERVICE] = "full-current-id"
            path.write_text(json.dumps(record), encoding="utf-8")
            stdout = io.StringIO()
            with patch("pathlib.Path.cwd", return_value=root), patch("subprocess.run") as run, contextlib.redirect_stdout(stdout):
                run.side_effect = lambda args, **kwargs: type("Result", (), {"returncode": 0, "stdout": read(args)})()
                exec(verify.REMOTE, {})
            self.assertEqual("LOCAL", json.loads(stdout.getvalue())[0]["deployment"]["source"])
        with patch.object(verify, "command", return_value="[]") as call:
            verify.node_snapshot({"keyPath": "~/.ssh/test", "host": "example", "port": 22, "user": "test", "targetDir": "~/smartparking"})
        self.assertEqual("cd $HOME/smartparking && python3 -", call.call_args.args[0][-1])


if __name__ == "__main__":
    unittest.main()
