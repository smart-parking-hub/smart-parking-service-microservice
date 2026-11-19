"""Configuration contracts: real Compose rendering, synthetic credentials, no up/exec."""

import importlib.util
import os
from pathlib import Path
import sys
import unittest
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location("compose_check", Path(__file__).resolve().parents[1] / "check-compose.py")
compose = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = compose
SPEC.loader.exec_module(compose)


class ComposeScenariosTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.models = {name: compose.render(scenario) for name, scenario in compose.SCENARIOS.items()}

    def test_supported_and_rejected_scenarios(self):
        for name, scenario in compose.SCENARIOS.items():
            with self.subTest(scenario=name):
                model = self.models[name]
                if scenario.expected_error:
                    self.assertIsNone(model)
                else:
                    compose.validate_model(scenario, model)

    def test_legacy_app_build_paths_resolve_to_existing_modules(self):
        for name in ("base", "dev", "full", "apps", "include", "integration"):
            for service in compose.APPS:
                with self.subTest(scenario=name, service=service):
                    context = self.models[name]["services"][service]["build"]["context"]
                    self.assertEqual(compose.ROOT / service, Path(context))

    def test_infra_bind_sources_resolve_to_repo_assets(self):
        expected = {"mysql": "docker/mysql/init.sql", "prometheus": "docker/prometheus/prometheus.yml", "grafana": "docker/grafana/provisioning"}
        for name in ("infra", "include"):
            for service, path in expected.items():
                mounts = self.models[name]["services"][service]["volumes"]
                self.assertIn(compose.ROOT / path, [Path(m["source"]) for m in mounts if m["type"] == "bind"])

    def test_presets_reuse_capabilities_without_changing_existing_apps(self):
        dev, full, integration = (self.models[n] for n in ("dev", "full", "integration"))
        for name in compose.APPS | compose.CORE_INFRA:
            self.assertEqual(dev["services"][name], full["services"][name])
            self.assertEqual(dev["services"][name], integration["services"][name])
        self.assertNotIn("kafka", dev["services"])
        self.assertEqual(full["services"]["kafka"], integration["services"]["kafka"])
        self.assertFalse(compose.MONITORING & integration["services"].keys())

    def test_payment_and_security_defaults_are_not_silently_merged(self):
        modern = self.models["base"]["services"]
        legacy = self.models["apps"]["services"]
        self.assertEqual("true", modern["payment-service"]["environment"]["APP_DEMO_PAYMENT_ENABLED"])
        self.assertEqual("false", legacy["payment-service"]["environment"]["APP_DEMO_PAYMENT_ENABLED"])
        self.assertIn("APP_SECURITY_API_KEY_ENABLED", modern["api-gateway"]["environment"])
        self.assertNotIn("APP_SECURITY_API_KEY_ENABLED", legacy["api-gateway"]["environment"])

    def test_project_and_storage_names_are_preserved(self):
        for name in ("dev", "full", "infra", "apps", "retained"):
            self.assertEqual("docker", self.models[name]["name"])
        # The historical include entry point had its own implicit project name.
        self.assertEqual("local", self.models["include"]["name"])
        for name in ("dev", "full", "infra", "include"):
            prefix = "local" if name == "include" else "docker"
            self.assertEqual(prefix + "_mysql_data", self.models[name]["volumes"]["mysql_data"]["name"])
            self.assertEqual("smart-parking-net", self.models[name]["networks"]["smart-parking-net"]["name"])
        self.assertEqual("docker_parking_lot_images", self.models["dev"]["volumes"]["parking_lot_images"]["name"])
        self.assertEqual("docker_parking_images", self.models["retained"]["volumes"]["parking_images"]["name"])
        self.assertEqual("docker_payment_uploads", self.models["dev"]["volumes"]["payment_uploads"]["name"])

    def test_retained_artifacts_lineage_and_schema_identity(self):
        retained = self.models["retained"]["services"]
        for service, image in {
            "identity-service": "smart-parking/identity-service:withdrawal-notifications-20261113",
            "parking-lot-service": "smart-parking/parking-lot-service:schedule-local-20261117",
            "booking-service": "smart-parking/booking-service:no-request-rate-local-20261117",
        }.items():
            self.assertEqual(image, retained[service]["image"])
        for service, schema in {"identity-service": "identity", "parking-lot-service": "lot", "booking-service": "booking", "payment-service": "payment"}.items():
            self.assertIn("/smart_parking_" + schema + "_p0_20261106?", retained[service]["environment"]["DB_URL"])
        self.assertEqual("classpath:db/local-v6-v7-compatible", retained["payment-service"]["environment"]["SPRING_FLYWAY_LOCATIONS"])
        self.assertNotIn("SPRING_FLYWAY_LOCATIONS", self.models["dev"]["services"]["payment-service"]["environment"])
        self.assertEqual("mailpit", retained["notification-service"]["environment"]["MAIL_HOST"])

    def test_smtp_changes_only_mail_environment(self):
        retained = self.models["retained"]["services"]
        smtp = self.models["smtp"]["services"]
        for service in compose.APPS:
            if service != "notification-service":
                self.assertEqual(retained[service], smtp[service])
        left = dict(retained["notification-service"])
        right = dict(smtp["notification-service"])
        left["environment"] = {k: v for k, v in left["environment"].items() if not k.startswith("MAIL_")}
        right["environment"] = {k: v for k, v in right["environment"].items() if not k.startswith("MAIL_")}
        self.assertEqual(left, right)
        with self.assertRaises(ValueError):
            compose.render(compose.SCENARIOS["smtp"], overrides={"MAIL_PASSWORD": ""})

    def test_internal_credentials_are_required_on_cluster_nodes(self):
        for name in ("edge", "data"):
            with self.subTest(scenario=name), self.assertRaises(ValueError):
                compose.render(compose.SCENARIOS[name], overrides={"APP_INTERNAL_SERVICE_TOKEN": ""})

    def test_layout_volume_remains_external_and_flags_default_off(self):
        for name, service in (("layout", "parking-lot-service"), ("core-layout", "smart-parking-parkinglot")):
            model = self.models[name]
            volume = model["volumes"]["parking-layout-files"]
            self.assertTrue(volume["external"])
            self.assertEqual("smartparking-layout-files", volume["name"])
            env = model["services"][service]["environment"]
            self.assertEqual("false", env["APP_PARKING_LAYOUT_ENABLED"])
            self.assertEqual("false", env["APP_PARKING_LAYOUT_PHYSICAL_BOOKING_ENABLED"])

    def test_legacy_kafka_persistence_and_transaction_overrides_are_retained(self):
        old = self.models["infra"]["services"]["kafka"]
        full = self.models["full"]["services"]["kafka"]
        self.assertEqual("1", old["environment"]["KAFKA_TRANSACTION_STATE_LOG_MIN_ISR"])
        self.assertNotIn("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", full["environment"])
        self.assertEqual("/tmp/kraft-combined-logs", old["environment"]["KAFKA_LOG_DIRS"])
        self.assertIn("kafka_data", [m["source"] for m in old["volumes"]])
        self.assertNotIn("volumes", full)


class CheckerFailureTest(unittest.TestCase):
    def test_environment_excludes_inherited_credentials_and_compose_controls(self):
        with patch.dict(os.environ, {"DB_PASSWORD": "private-sentinel", "COMPOSE_FILE": "unwanted.yml", "MAIL_PASSWORD": "private-sentinel"}):
            fixture = compose.fixture_environment()
        self.assertNotIn("DB_PASSWORD", fixture)
        self.assertNotIn("COMPOSE_FILE", fixture)
        self.assertEqual("audit-placeholder", fixture["MAIL_PASSWORD"])

    def test_missing_build_and_bind_paths_fail(self):
        for service in ({"build": {"context": str(compose.ROOT / "nonexistent-module")}}, {"volumes": [{"type": "bind", "source": str(compose.ROOT / "nonexistent-asset"), "target": "/etc/test.conf"}]}):
            with self.subTest(service=service), self.assertRaises(ValueError):
                compose.validate_model(compose.Scenario([], {"test"}), {"services": {"test": service}})

    def test_directory_cannot_replace_a_config_file(self):
        service = {"volumes": [{"type": "bind", "source": str(compose.ROOT / "docker"), "target": "/etc/test.conf"}]}
        with self.assertRaises(ValueError):
            compose.validate_model(compose.Scenario([], {"test"}), {"services": {"test": service}})

    def test_duplicate_ports_and_unexpected_services_fail(self):
        port = {"ports": [{"published": "8088", "target": 8088}]}
        with self.assertRaises(ValueError):
            compose.validate_model(compose.Scenario([], {"a", "b"}), {"services": {"a": port, "b": port}})
        with self.assertRaises(ValueError):
            compose.validate_model(compose.Scenario([], {"test"}), {"services": {"other": {}}})


if __name__ == "__main__":
    unittest.main()
