"""Exercise release activation against a fake Docker engine, never a VPS."""
from contextlib import nullcontext
import copy
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("release_runtime", Path(__file__).resolve().parents[1] / "release-runtime.py")
runtime = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(runtime)
SHA = "a" * 40
DIGEST = "sha256:" + "b" * 64
OLD = "sha256:" + "c" * 64
KEY = "smart-parking-payment"
MANIFEST = {"sourceSha": SHA, "services": {"payment-service": {"node": "vps2", "composeService": KEY, "image": "ghcr.io/smart-parking-hub/payment-service", "digest": DIGEST}}}


def item(image=OLD, state="running", service=KEY, project="docker", started="2026-11-19T00:00:00Z"):
    return {"Id": "container-" + state, "Image": image, "State": {"Status": state, "StartedAt": started},
            "Config": {"Image": "ghcr.io/smart-parking-hub/payment-service@" + image, "Labels": {"com.docker.compose.project": project, "com.docker.compose.service": service}}}


class ActivationTest(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory(prefix="sph-runtime-test-")
        self.addCleanup(self.folder.cleanup)
        self.root = Path(self.folder.name)
        self.compose = "docker/compose/vps-cluster/compose.vps2-data.yml"
        target = self.root / self.compose
        target.parent.mkdir(parents=True)
        target.write_text("services: {}")
        (self.root / ".env").write_text("PRIVATE_TEST_VALUE=must-not-appear-in-history")
        self.calls = []
        self.current = [item(), item(DIGEST, "created", started="0001-01-01T00:00:00Z")]
        self.fail_up = False
        self.revision = SHA

    def engine(self, args):
        self.calls.append(args)
        if args[:4] == ["docker", "image", "inspect", "ghcr.io/smart-parking-hub/payment-service@" + DIGEST]:
            return json.dumps([{"Id": DIGEST, "RepoDigests": [], "Config": {"Labels": {"org.opencontainers.image.revision": self.revision}}}])
        if "config" in args:
            return json.dumps({"services": {KEY: {}}})
        if args[:3] == ["docker", "ps", "-aq"]:
            return "\n".join(c["Id"] for c in self.current)
        if args[:2] == ["docker", "inspect"]:
            return json.dumps(self.current)
        if args[:2] == ["docker", "rm"]:
            self.current = [c for c in self.current if c["Id"] != args[2]]
            return ""
        if "pull" in args and "up" not in args:
            return ""
        if "up" in args:
            if self.fail_up:
                raise RuntimeError("simulated compose activation failure")
            self.current = [item(DIGEST)]
            return ""
        raise AssertionError("Unexpected command: " + str(args))

    def activate(self, health=True, manifest=None):
        with patch.object(runtime, "run", side_effect=self.engine), patch.object(runtime, "deploy_lock", return_value=nullcontext()), patch.object(runtime, "healthy", return_value=health):
            return runtime.activate(manifest or MANIFEST, self.root, self.compose, "vps2", timeout=1 if health else 0)

    def test_local_history_records_source_release_and_verified_container(self):
        manifest = {**MANIFEST, "source": "LOCAL", "releaseId": "local-" + SHA + "-unique"}
        result = self.activate(manifest=manifest)
        self.assertEqual("LOCAL", result["source"])
        self.assertEqual(manifest["releaseId"], result["releaseId"])
        saved = json.loads(next((self.root / ".deploy-history").glob("*.json")).read_text())
        self.assertEqual("container-running", saved["verified"][KEY])

    def test_interrupted_created_plus_exited_is_recovered_without_infra_or_network_pull(self):
        self.current = [item(OLD, "exited"), item(DIGEST, "created", started="0001-01-01T00:00:00Z")]
        with patch.object(runtime, "run", side_effect=self.engine), patch.object(runtime, "deploy_lock", return_value=nullcontext()), patch.object(runtime, "healthy", return_value=True):
            result = runtime.activate(MANIFEST, self.root, self.compose, "vps2", cached_images=True)
        self.assertIn(KEY, result["verified"])
        self.assertEqual("exited", result["before"][KEY][0]["state"])
        self.assertFalse(any("pull" in call and "up" not in call for call in self.calls))
        self.assertIn(["docker", "rm", "container-created"], self.calls)
        self.assertFalse(any("mysql" in str(call) or "kafka" in str(call) for call in self.calls))

    def test_missing_cached_digest_fails_before_cleanup_or_up(self):
        def missing(args):
            if args[:3] == ["docker", "image", "inspect"]:
                raise RuntimeError("Exact digest not available locally")
            return self.engine(args)
        with patch.object(runtime, "run", side_effect=missing), patch.object(runtime, "deploy_lock", return_value=nullcontext()):
            with self.assertRaisesRegex(RuntimeError, "Exact digest"):
                runtime.activate(MANIFEST, self.root, self.compose, "vps2", cached_images=True)
        self.assertFalse(any("up" in call or call[:2] == ["docker", "rm"] for call in self.calls))

    def test_ambiguous_or_unowned_containers_fail_before_pull_cleanup_or_up(self):
        unowned = item(DIGEST, "created", started="0001-01-01T00:00:00Z")
        unowned["Config"]["Image"] = "unrelated/image:latest"
        for current in ([item(), item(OLD, "exited")], [item(), item()], [unowned], [item(OLD, "restarting")]):
            with self.subTest(states=[c["State"]["Status"] for c in current]):
                self.current = current
                self.calls.clear()
                with self.assertRaisesRegex(RuntimeError, "ambiguous"):
                    self.activate()
                self.assertFalse(any("up" in call or "pull" in call or call[:2] == ["docker", "rm"] for call in self.calls))

    def test_each_application_gets_a_fresh_health_deadline(self):
        manifest = copy.deepcopy(MANIFEST)
        manifest["services"]["notification-service"] = {"node": "vps2", "composeService": "smart-parking-notification", "image": "ghcr.io/smart-parking-hub/notification-service", "digest": DIGEST}
        def engine(args):
            if "config" in args:
                return json.dumps({"services": {KEY: {}, "smart-parking-notification": {}}})
            if args[:3] == ["docker", "image", "inspect"]:
                return json.dumps([{"Id": DIGEST}])
            self.calls.append(args)
            return ""
        with patch.object(runtime, "run", side_effect=engine), patch.object(runtime, "containers", return_value=[]), patch.object(runtime, "deploy_lock", return_value=nullcontext()), patch.object(runtime.time, "monotonic", side_effect=[10, 100]), patch.object(runtime, "wait_current", return_value="verified") as wait:
            runtime.activate(manifest, self.root, self.compose, "vps2", timeout=180)
        self.assertEqual([190, 280], [call.args[2] for call in wait.call_args_list])

    def test_local_image_revision_mismatch_stops_before_cleanup_or_up(self):
        self.revision = "f" * 40
        with self.assertRaisesRegex(RuntimeError, "revision"):
            self.activate(manifest={**MANIFEST, "source": "LOCAL"})
        self.assertFalse(any("up" in call or call[:2] == ["docker", "rm"] for call in self.calls))

    def test_local_registry_manifest_uses_isolated_source_tag(self):
        config = {"registry": {"imagePrefix": "ghcr.io/smart-parking-hub"},
                  "services": {"payment-service": {"node": "vps2", "composeService": KEY}}}
        tag = "local-" + SHA + "-unique"
        with patch.object(runtime, "run", return_value=json.dumps({"digest": DIGEST})) as read:
            result = runtime.registry_manifest(config, SHA, tag=tag, source="LOCAL")
        self.assertEqual("LOCAL", result["source"])
        self.assertEqual(tag, result["releaseId"])
        self.assertIn("ghcr.io/smart-parking-hub/payment-service:" + tag, read.call_args.args[0])
        with patch.object(runtime, "run") as read:
            for tag in ("latest", "sha-" + SHA, "local-" + "f" * 40 + "-unique"):
                with self.assertRaises(ValueError):
                    runtime.registry_manifest(config, SHA, tag=tag, source="LOCAL")
            read.assert_not_called()

    def test_pins_only_apps_and_recovers_only_never_started_expected_container(self):
        result = self.activate()
        self.assertIn(KEY, result["verified"])
        self.assertEqual(OLD, result["before"][KEY][0]["imageId"])
        self.assertIn(["docker", "rm", "container-created"], self.calls)
        ups = [c for c in self.calls if "up" in c]
        self.assertEqual(1, len(ups))
        self.assertEqual(["up", "-d", "--no-deps", "--pull", "never", KEY], ups[0][-6:])
        self.assertNotIn("--remove-orphans", ups[0])
        for call in self.calls:
            self.assertNotIn("prune", call)
            self.assertNotIn("-f", call[:2])
        history = next((self.root / ".deploy-history").glob("*.json")).read_text()
        self.assertNotIn("must-not-appear-in-history", history)

    def test_compose_failure_is_not_success_and_keeps_previous_image_metadata(self):
        self.fail_up = True
        with self.assertRaises(RuntimeError):
            self.activate()
        history = json.loads(next((self.root / ".deploy-history").glob("*.json")).read_text())
        self.assertEqual({}, history["verified"])
        self.assertEqual(OLD, history["before"][KEY][0]["imageId"])

    def test_health_timeout_fails_even_with_expected_image(self):
        with self.assertRaisesRegex(RuntimeError, "timed out"):
            self.activate(health=False)

    def test_digest_mismatch_never_reports_verified(self):
        with patch.object(runtime, "containers", return_value=[item(OLD)]), patch.object(runtime, "healthy", return_value=True), patch.object(runtime.time, "monotonic", side_effect=[0, 2]), patch.object(runtime.time, "sleep"):
            with self.assertRaisesRegex(RuntimeError, "timed out"):
                runtime.wait_current(KEY, {DIGEST}, 1)

    def test_pending_recovery_does_not_remove_running_old_wrong_project_or_started_containers(self):
        candidates = [item(DIGEST), item(DIGEST, "created"), item(DIGEST, "created", project="other", started="0001-01-01"), item(OLD, "created", started="0001-01-01"), item(DIGEST, "exited", started="0001-01-01")]
        self.assertFalse(any(runtime.recovery_candidate(c, KEY, {DIGEST}) for c in candidates))

    def test_never_started_older_release_of_the_same_owned_repository_can_be_recovered(self):
        pending = item(OLD, "created", started="0001-01-01T00:00:00Z")
        pending["Config"]["Image"] = "ghcr.io/smart-parking-hub/payment-service:latest"
        self.assertTrue(runtime.recovery_candidate(pending, KEY, {DIGEST}, "ghcr.io/smart-parking-hub/payment-service"))
        self.assertFalse(runtime.recovery_candidate(pending, KEY, {DIGEST}, "ghcr.io/smart-parking-hub/ai-service"))

    def test_path_escape_and_bad_manifest_fail_before_docker(self):
        with patch.object(runtime, "run") as command:
            with self.assertRaises(ValueError):
                runtime.activate(MANIFEST, self.root, "../outside.yml", "vps2")
            broken = copy.deepcopy(MANIFEST)
            broken["services"]["payment-service"]["digest"] = "latest"
            with self.assertRaises(ValueError):
                runtime.activate(broken, self.root, self.compose, "vps2")
            infra = copy.deepcopy(MANIFEST)
            row = infra["services"].pop("payment-service")
            row.update(composeService="smart-parking-mysql", image="ghcr.io/smart-parking-hub/mysql")
            infra["services"]["mysql"] = row
            with self.assertRaises(ValueError):
                runtime.activate(infra, self.root, self.compose, "vps2")
            command.assert_not_called()

    @unittest.skipUnless(os.name == "posix", "Linux flock contract; runs in CI")
    def test_second_deployer_cannot_enter_the_same_host_lock(self):
        path = self.root / "deploy.lock"
        with runtime.deploy_lock(path):
            with self.assertRaisesRegex(RuntimeError, "holds this VPS lock"):
                with runtime.deploy_lock(path):
                    self.fail("Second deployer entered")


if __name__ == "__main__":
    unittest.main()
