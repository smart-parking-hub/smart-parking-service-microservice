"""Render production RPC wiring using synthetic addresses; never contact a VPS."""

import importlib.util
from pathlib import Path
import sys
import unittest
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location(
    "cluster_compose_check", Path(__file__).resolve().parents[1] / "check-compose.py")
compose = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = compose
SPEC.loader.exec_module(compose)

ROUTES = {
    "IDENTITY_TAILNET_IP": "100.64.0.11",
    "BOOKING_IDENTITY_SERVICE_URL": "http://100.64.0.11:8081",
    "BOOKING_PAYMENT_SERVICE_URL": "http://100.64.0.12:8084",
    "PAYMENT_BOOKING_SERVICE_URL": "http://100.64.0.13:8087",
    "APP_INTERNAL_SERVICE_TOKEN": "routing-test-token-not-a-secret",
}


class ClusterRoutingTest(unittest.TestCase):
    def render(self, node, **overrides):
        return compose.render(compose.SCENARIOS[node], overrides=ROUTES | overrides)["services"]

    def test_booking_uses_configured_identity_and_payment_routes(self):
        env = self.render("core")["smart-parking-booking"]["environment"]
        self.assertEqual(ROUTES["BOOKING_IDENTITY_SERVICE_URL"], env.get("IDENTITY_SERVICE_URL"))
        self.assertEqual(ROUTES["BOOKING_PAYMENT_SERVICE_URL"], env.get("PAYMENT_SERVICE_URL"))
        self.assertEqual("http://smart-parking-parkinglot:8082", env["PARKING_LOT_SERVICE_URL"])

    def test_payment_uses_configured_booking_route(self):
        env = self.render("data")["smart-parking-payment"]["environment"]
        self.assertEqual(ROUTES["PAYMENT_BOOKING_SERVICE_URL"], env.get("BOOKING_SERVICE_URL"))

    def test_internal_callers_receive_the_same_required_token(self):
        core, data = self.render("core"), self.render("data")
        for services, name in ((core, "smart-parking-booking"),
                               (core, "smart-parking-parkinglot"),
                               (data, "smart-parking-payment")):
            with self.subTest(service=name):
                self.assertEqual(ROUTES["APP_INTERNAL_SERVICE_TOKEN"],
                                 services[name]["environment"].get("APP_INTERNAL_SERVICE_TOKEN"))
        self.assertEqual("http://smart-parking-booking:8083",
                         core["smart-parking-parkinglot"]["environment"].get("BOOKING_SERVICE_URL"))

    def test_identity_preserves_loopback_and_adds_only_the_private_bind(self):
        ports = self.render("edge")["smart-parking-identity"]["ports"]
        self.assertEqual({("127.0.0.1", "8081", 8081),
                          (ROUTES["IDENTITY_TAILNET_IP"], "8081", 8081)},
                         {(p.get("host_ip"), str(p["published"]), p["target"]) for p in ports})

    def test_core_rejects_blank_identity_and_payment_routes(self):
        for name in ("BOOKING_IDENTITY_SERVICE_URL", "BOOKING_PAYMENT_SERVICE_URL"):
            with self.subTest(variable=name), self.assertRaises(ValueError):
                self.render("core", **{name: ""})

    def test_payment_rejects_blank_booking_route(self):
        with self.assertRaises(ValueError):
            self.render("data", PAYMENT_BOOKING_SERVICE_URL="")

    def test_identity_rejects_blank_private_bind(self):
        with self.assertRaises(ValueError):
            self.render("edge", IDENTITY_TAILNET_IP="")

    def test_core_rejects_blank_internal_token(self):
        with self.assertRaises(ValueError):
            self.render("core", APP_INTERNAL_SERVICE_TOKEN="")

    def test_checker_never_inherits_real_private_routes(self):
        with patch.dict(compose.os.environ, {key: "private-sentinel" for key in ROUTES}):
            fixture = compose.fixture_environment()
        for name in ROUTES:
            with self.subTest(variable=name):
                self.assertIn(name, fixture)
                self.assertNotEqual("private-sentinel", fixture[name])


if __name__ == "__main__":
    unittest.main()
