"""Render supported Compose scenarios without starting Docker resources or reading .env.

Only Python's standard library and the Docker Compose plugin are required.
Provider credentials are synthetic; rendered environments are never printed.
"""

import argparse
from dataclasses import dataclass
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[2]
LOCAL = "docker/compose/local/"
OVERLAYS = "docker/compose/overlays/"
CLUSTER = "docker/compose/vps-cluster/"
BASE = LOCAL + "compose.yaml"
DEV = [BASE, LOCAL + "compose.local.yaml"]
FULL = DEV + [LOCAL + "compose.local-full.yaml"]
RETAINED = [BASE] + [OVERLAYS + "compose." + name + ".yaml" for name in (
    "restore-p0.local", "schedule.local", "no-request-rate.local"
)]
APPS = {"api-gateway", "identity-service", "parking-lot-service", "booking-service",
        "payment-service", "notification-service", "ai-service"}
CORE_INFRA = {"mysql", "redis"}
MONITORING = {"prometheus", "grafana"}
DISCOVERY = {"discovery-server", "config-server"}


@dataclass(frozen=True)
class Scenario:
    files: list
    services: set
    root_directory: bool = True
    host_binds: bool = False
    expected_error: str = ""


SCENARIOS = {
    "base": Scenario([BASE], APPS),
    "dev": Scenario(DEV, APPS | CORE_INFRA),
    "infra-minimal": Scenario([LOCAL + "compose.infra.yaml"], CORE_INFRA),
    "full": Scenario(FULL, APPS | CORE_INFRA | {"kafka"} | MONITORING | DISCOVERY),
    "integration": Scenario(DEV + [LOCAL + "compose.kafka.yaml"], APPS | CORE_INFRA | {"kafka"}),
    "monitoring": Scenario(DEV + [LOCAL + "compose.monitoring.yaml"], APPS | CORE_INFRA | MONITORING),
    "discovery": Scenario(DEV + [LOCAL + "compose.discovery.yaml"], APPS | CORE_INFRA | DISCOVERY),
    "quality": Scenario([LOCAL + "compose.quality.yaml"], {"sonarqube"}),
    "infra": Scenario([LOCAL + "docker-compose.infra.yml"], CORE_INFRA | {"kafka", "sonarqube"} | MONITORING, False),
    "apps": Scenario([LOCAL + "docker-compose.apps.yml"], APPS, False),
    "include": Scenario([LOCAL + "docker-compose.yml"], APPS | CORE_INFRA | {"kafka", "sonarqube"} | MONITORING, False),
    "otp": Scenario([OVERLAYS + "compose.email.local.yaml"], {"identity-service", "notification-service", "mailpit"}),
    "p0": Scenario(FULL + [OVERLAYS + "compose.p0.local.yaml"], APPS | CORE_INFRA | {"kafka"} | MONITORING | DISCOVERY),
    "retained": Scenario(RETAINED, APPS),
    "smtp": Scenario(RETAINED + [OVERLAYS + "compose.smtp.local.yaml"], APPS),
    "payos-compatible": Scenario(DEV + [OVERLAYS + "compose.payos-local-v6-v7.yaml"], APPS | CORE_INFRA),
    "layout": Scenario(DEV + [LOCAL + "compose.layout-filesystem.yaml"], APPS | CORE_INFRA),
    "edge": Scenario([CLUSTER + "compose.vps1-edge.yml"], {"smart-parking-nginx", "smart-parking-gateway", "smart-parking-identity", "smart-parking-redis-edge"}, False, True),
    "data": Scenario([CLUSTER + "compose.vps2-data.yml"], {"smart-parking-kafka", "smart-parking-payment", "smart-parking-notification", "smart-parking-mysql", "smart-parking-ai"}, False, True),
    "core": Scenario([CLUSTER + "compose.vps3-core.yml"], {"smart-parking-parkinglot", "smart-parking-booking", "smart-parking-redis-core"}, False, True),
    "core-layout": Scenario([CLUSTER + "compose.vps3-core.yml", CLUSTER + "compose.layout-filesystem.yaml"], {"smart-parking-parkinglot", "smart-parking-booking", "smart-parking-redis-core"}, False, True),
    "r4": Scenario([BASE, "docker/compose/vps-single/compose.vps-r4.yaml"], APPS | {"nginx"}),
    "large-unsupported": Scenario([BASE, "docker/compose/vps-single/compose.vps-large.yaml"], set(), expected_error="container_name"),
    "wrong-layout-rejected": Scenario(DEV + [CLUSTER + "compose.layout-filesystem.yaml"], set(), expected_error="smart-parking-parkinglot"),
}


def fixture_environment():
    # --env-file also disables the usual automatic repository .env load.
    variables = set()
    for path in (ROOT / "docker/compose").rglob("*"):
        if path.suffix in {".yaml", ".yml"}:
            variables.update(re.findall(r"(?<!\$)\$\{([A-Za-z_][A-Za-z0-9_]*)", path.read_text(encoding="utf-8")))
    env = {key: value for key, value in os.environ.items()
           if key.upper() not in variables and not key.upper().startswith("COMPOSE_")}
    env.update({
        "COMPOSE_DISABLE_ENV_FILE": "1",
        "APP_INTERNAL_SERVICE_TOKEN": "audit-local-token-not-a-secret",
        "P0_INTERNAL_SERVICE_TOKEN": "audit-local-token-not-a-secret",
        "IDENTITY_TAILNET_IP": "100.64.0.11",
        "BOOKING_IDENTITY_SERVICE_URL": "http://100.64.0.11:8081",
        "BOOKING_PAYMENT_SERVICE_URL": "http://100.64.0.12:8084",
        "PAYMENT_BOOKING_SERVICE_URL": "http://100.64.0.13:8087",
        "MAIL_HOST": "smtp.invalid", "MAIL_PORT": "2525",
        "MAIL_FROM": "audit@example.invalid", "MAIL_USERNAME": "audit",
        "MAIL_PASSWORD": "audit-placeholder",
    })
    return env


def render(scenario, *, overrides=None):
    env = fixture_environment()
    env.update(overrides or {})
    # TemporaryDirectory closes the file before Compose opens it on Windows.
    with tempfile.TemporaryDirectory(prefix="sph-compose-check-") as folder:
        envfile = Path(folder) / "empty.env"
        envfile.write_text("", encoding="utf-8")
        command = ["docker", "compose", "--env-file", str(envfile)]
        if scenario.root_directory:
            command += ["--project-directory", str(ROOT), "-p", "docker"]
        for file in scenario.files:
            command += ["-f", str(ROOT / file)]
        result = subprocess.run(command + ["config", "--format", "json"],
                                cwd=ROOT, env=env, capture_output=True, timeout=60)
    if scenario.expected_error:
        if result.returncode == 0 or scenario.expected_error not in result.stderr.decode("utf-8", errors="replace"):
            raise ValueError("Expected unsupported combination was not rejected for the documented reason")
        return None
    if result.returncode:
        # Do not echo stderr: provider variables may be included by future CLI errors.
        raise ValueError("Compose config failed (exit %d); no rendered environment was logged" % result.returncode)
    return json.loads(result.stdout)


def validate_model(scenario, model):
    if set(model["services"]) != scenario.services:
        raise ValueError("Rendered service set differs from the documented scenario")
    names = set()
    published = set()
    for name, service in model["services"].items():
        container = service.get("container_name")
        if container and container in names:
            raise ValueError("Duplicate container name")
        names.add(container)
        build = service.get("build")
        if build and not (Path(build["context"]) / build.get("dockerfile", "Dockerfile")).is_file():
            raise ValueError(name + ": Dockerfile does not exist")
        for mount in service.get("volumes", []):
            if mount["type"] == "bind" and not scenario.host_binds:
                source = Path(mount["source"])
                if not source.exists():
                    raise ValueError(name + ": bind source does not exist")
                if (mount["target"].endswith((".sql", ".yml", ".yaml", ".conf")) and not source.is_file()):
                    raise ValueError(name + ": expected a bind-mounted file")
        for port in service.get("ports", []):
            if "published" not in port:
                continue
            key = (port.get("host_ip", "0.0.0.0"), str(port["published"]), port.get("protocol", "tcp"))
            if key in published:
                raise ValueError("Duplicate published port")
            published.add(key)
    for volume in model.get("volumes", {}).values():
        if volume.get("external") and not volume.get("name"):
            raise ValueError("External volume must have a name")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scenario", action="append", choices=sorted(SCENARIOS), help="Repeat to select scenarios; default: all")
    args = parser.parse_args()
    failed = 0
    for name in args.scenario or SCENARIOS:
        scenario = SCENARIOS[name]
        try:
            model = render(scenario)
            if model is not None:
                validate_model(scenario, model)
            detail = "expected rejection" if model is None else str(len(model["services"])) + " services"
            if scenario.host_binds:
                detail += "; VPS bind paths require host verification"
            print("PASS " + name + ": " + detail)
        except (ValueError, OSError, subprocess.TimeoutExpired) as error:
            print("FAIL " + name + ": " + str(error))
            failed += 1
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
