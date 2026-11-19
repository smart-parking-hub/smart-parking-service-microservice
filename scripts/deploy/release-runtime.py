"""Shared manual/CI release activation. Invoked on Linux only after a deploy request.

Normal activation writes app/container state; --registry-manifest only reads registry.
Never restores databases, removes volumes, prunes images or recreates infra services.
"""
import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import time
import urllib.request

APP_TARGETS = {
    "api-gateway": ("smart-parking-gateway", "vps1"),
    "identity-service": ("smart-parking-identity", "vps1"),
    "payment-service": ("smart-parking-payment", "vps2"),
    "notification-service": ("smart-parking-notification", "vps2"),
    "ai-service": ("smart-parking-ai", "vps2"),
    "parking-lot-service": ("smart-parking-parkinglot", "vps3"),
    "booking-service": ("smart-parking-booking", "vps3"),
}


def run(args):
    p = subprocess.run(args, capture_output=True, text=True, timeout=180)
    if p.returncode:
        raise RuntimeError(args[0] + " operation failed (exit " + str(p.returncode) + "); activation not confirmed")
    return p.stdout


def registry_manifest(config, sha, selected=None, tag=None, source="CI"):
    if not re.fullmatch(r"[a-f0-9]{40}", sha):
        raise ValueError("Invalid source SHA")
    prefix = config["registry"]["imagePrefix"]
    tag = tag or "sha-" + sha
    if source not in ("CI", "LOCAL") or not re.fullmatch(r"[a-zA-Z0-9_.-]{1,128}", tag):
        raise ValueError("Invalid release source/tag")
    if source == "LOCAL" and not tag.startswith("local-" + sha + "-"):
        raise ValueError("Local releases require a unique source-SHA tag")
    services = {}
    for name, s in config["services"].items():
        if selected and name not in selected:
            continue
        descriptor = json.loads(run(["docker", "buildx", "imagetools", "inspect",
                                    prefix + "/" + name + ":" + tag, "--format", "{{json .Manifest}}"] ))
        services[name] = {"composeService": s["composeService"], "node": s["node"],
                          "image": prefix + "/" + name, "digest": descriptor["digest"]}
    manifest = {"sourceSha": sha, "source": source, "releaseId": tag, "services": services}
    validate(manifest)
    return manifest


def ci_manifest(config, selected=None):
    # A manual deploy activates the same artifacts as the read-only verifier.
    path = Path(__file__).with_name("verify-vps-release.py")
    spec = importlib.util.spec_from_file_location("release_verifier", path)
    verifier = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(verifier)
    repo = verifier.gh_json("repo", "view", "--json", "nameWithOwner")["nameWithOwner"]
    release = verifier.expected_release(repo)
    services = {}
    for name, s in config["services"].items():
        if selected and name not in selected:
            continue
        services[name] = {"composeService": s["composeService"], "node": s["node"],
                          "image": config["registry"]["imagePrefix"] + "/" + name,
                          "digest": release["digests"][name]}
    manifest = {"sourceSha": release["releaseSha"], "source": "CI", "buildRun": release["runId"], "services": services}
    validate(manifest)
    return manifest


def validate(manifest):
    if manifest.get("source", "CI") not in ("CI", "LOCAL"):
        raise ValueError("Unknown release source")
    if not re.fullmatch(r"[a-f0-9]{40}", manifest.get("sourceSha", "")) or not manifest.get("services"):
        raise ValueError("Missing source SHA or services")
    seen = set()
    for name, s in manifest["services"].items():
        if APP_TARGETS.get(name) != (s.get("composeService"), s.get("node")):
            raise ValueError("Only the seven configured application targets may be activated")
        if (not re.fullmatch(r"[a-z0-9-]+", name)
                or not re.fullmatch(r"smart-parking-[a-z0-9-]+", s.get("composeService", ""))
                or s.get("node") not in ("vps1", "vps2", "vps3")
                or not re.fullmatch(r"ghcr\.io/[a-z0-9._/-]+/" + re.escape(name), s.get("image", ""))
                or not re.fullmatch(r"sha256:[a-f0-9]{64}", s.get("digest", ""))):
            raise ValueError("Invalid release service identity")
        if s["composeService"] in seen:
            raise ValueError("Duplicate service in release")
        seen.add(s["composeService"])


@contextmanager
def deploy_lock(path, inherited_fd=None):
    import fcntl  # Linux VPS; registry manifest generation also works on Windows.
    owned = inherited_fd is None
    fd = os.open(path, os.O_CREAT | os.O_RDWR, 0o600) if owned else inherited_fd
    try:
        if not owned:
            opened, expected = os.fstat(fd), os.stat(path)
            if (opened.st_dev, opened.st_ino) != (expected.st_dev, expected.st_ino):
                raise RuntimeError("Inherited lock does not match the deployment lock")
        try:
            fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise RuntimeError("Another manual or CI deployment holds this VPS lock") from None
        yield
    finally:
        if owned:
            os.close(fd)


def containers(service):
    ids = run(["docker", "ps", "-aq", "--filter", "label=com.docker.compose.project=docker",
               "--filter", "label=com.docker.compose.service=" + service]).split()
    return json.loads(run(["docker", "inspect", *ids])) if ids else []


def image_identities(reference):
    image = json.loads(run(["docker", "image", "inspect", reference]))[0]
    ids = {image["Id"], reference.rsplit("@", 1)[-1]}
    ids.update(x.rsplit("@", 1)[-1] for x in image.get("RepoDigests", []))
    return ids


def recovery_candidate(container, service, identities, repository=None):
    labels = container["Config"].get("Labels") or {}
    state = container["State"]
    reference = container["Config"].get("Image", "")
    owned_image = container["Image"] in identities or (repository is not None and
                  (reference.startswith(repository + ":") or reference.startswith(repository + "@")))
    return (labels.get("com.docker.compose.project") == "docker"
            and labels.get("com.docker.compose.service") == service
            and state["Status"] == "created"
            and state.get("StartedAt", "").startswith("0001-")
            and owned_image)


def healthy(container):
    env = dict(x.split("=", 1) for x in container["Config"].get("Env", []) if "=" in x)
    port = env.get("PORT")
    ports = container["NetworkSettings"].get("Ports", {}).get(str(port) + "/tcp") or []
    base = next(("http://127.0.0.1:" + b["HostPort"] for b in ports
                 if b.get("HostIp") in ("0.0.0.0", "127.0.0.1", "")), None)
    if base is None and port:
        ips = [v["IPAddress"] for v in container["NetworkSettings"].get("Networks", {}).values() if v.get("IPAddress")]
        if ips:
            base = "http://" + ips[0] + ":" + port
    if not base:
        return False
    try:
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        with opener.open(base + "/actuator/health", timeout=3) as response:
            return json.load(response).get("status") == "UP"
    except (OSError, ValueError):
        return False


def wait_current(service, identities, deadline):
    while time.monotonic() < deadline:
        active = [c for c in containers(service) if c["State"]["Status"] == "running"]
        if len(active) == 1 and active[0]["Image"] in identities and healthy(active[0]):
            return active[0]["Id"]
        time.sleep(2)
    raise RuntimeError(service + ": running image/health verification timed out")


def activate(manifest, root, compose_file, node, lock_fd=None, refresh=False, timeout=180, cached_images=False):
    validate(manifest)
    root = root.resolve()
    path = (root / compose_file).resolve()
    if not path.is_relative_to(root) or not str(compose_file).startswith("docker/compose/vps-cluster/"):
        raise ValueError("Compose path must be inside the cluster configuration directory")
    selected = {n: s for n, s in manifest["services"].items() if s["node"] == node}
    if not selected:
        raise ValueError("Release has no services for this VPS")
    with deploy_lock(Path.home() / ".smartparking-deploy.lock", lock_fd):
        if refresh:
            run(["git", "-C", str(root), "fetch", "origin", "main"])
            latest = run(["git", "-C", str(root), "rev-parse", "origin/main"]).strip()
            if latest != manifest["sourceSha"]:
                raise RuntimeError("Main has moved; rebuild/choose the current release before activation")
            # Only approved deployment definitions; preserve env, frontend and unrelated work.
            run(["git", "-C", str(root), "checkout", manifest["sourceSha"], "--", "docker/compose"])
        if not path.is_file() or not (root / ".env").is_file():
            raise ValueError("Missing cluster Compose or provisioned .env")
        base = ["docker", "compose", "-p", "docker", "--env-file", str(root / ".env"), "-f", str(path)]
        model = json.loads(run(base + ["config", "--format", "json"]))
        rows = {}
        previous = {}
        for name, s in selected.items():
            key = s["composeService"]
            if key not in model["services"]:
                raise ValueError("Target service missing in cluster Compose")
            rows[key] = {"image": s["image"] + "@" + s["digest"]}
            existing = containers(key)
            retained = [c for c in existing if not recovery_candidate(c, key, set(), s["image"])]
            if len(retained) > 1 or any(c["State"]["Status"] not in ("running", "exited") for c in retained):
                raise RuntimeError(key + ": ambiguous existing containers; manual investigation required")
            previous[key] = [{"containerId": c["Id"], "imageId": c["Image"], "imageRef": c["Config"]["Image"],
                              "state": c["State"]["Status"]} for c in existing]
        history_dir = root / ".deploy-history"
        history_dir.mkdir(mode=0o700, exist_ok=True)
        history = history_dir / (datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ") + "-" + node + ".json")
        record = {"sourceSha": manifest["sourceSha"], "source": manifest.get("source", "CI"), "releaseId": manifest.get("releaseId"), "buildRun": manifest.get("buildRun"), "composeFile": compose_file, "before": previous, "expected": rows, "removedNeverStarted": [], "verified": {}}
        history.write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
        with tempfile.TemporaryDirectory(prefix="sph-release-") as folder:
            override = Path(folder) / "images.json"
            override.write_text(json.dumps({"services": rows}), encoding="utf-8")
            command = base + ["-f", str(override)]
            # Pin by digest before any recreate, never trust latest during activation.
            if not cached_images:
                run(command + ["pull", *rows])
            # Cached recovery still requires every exact digest to be inspectable
            # before any cleanup/up; never substitute a tag or another image.
            accepted = {key: image_identities(row["image"]) for key, row in rows.items()}
            if manifest.get("source") == "LOCAL":
                for row in rows.values():
                    image = json.loads(run(["docker", "image", "inspect", row["image"]]))[0]
                    revision = (image.get("Config", {}).get("Labels") or {}).get("org.opencontainers.image.revision")
                    if revision != manifest["sourceSha"]:
                        raise RuntimeError("Local image revision does not match the declared source SHA")
            repositories = {s["composeService"]: s["image"] for s in selected.values()}
            for key in rows:
                for c in containers(key):
                    if recovery_candidate(c, key, accepted[key], repositories[key]):
                        run(["docker", "rm", c["Id"]])  # Never started; no -f, no -v.
                        record["removedNeverStarted"].append({"id": c["Id"], "service": key})
                        history.write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
            try:
                for key in rows:
                    run(command + ["up", "-d", "--no-deps", "--pull", "never", key])
                    record["verified"][key] = wait_current(key, accepted[key], time.monotonic() + timeout)
                    history.write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
                    print("VERIFIED " + key + " " + rows[key]["image"], flush=True)
            except Exception:
                print("Activation incomplete; previous image metadata retained at " + str(history), flush=True)
                raise
        print("RELEASE_VERIFIED " + manifest["sourceSha"] + " " + node, flush=True)
        return record


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--registry-manifest", help="Read SHA-tagged registry images for this source SHA; no deployment")
    parser.add_argument("--ci-manifest", action="store_true", help="Read the exact main CI build artifacts; no deployment")
    parser.add_argument("--image-tag", help="Unique published tag, used by Local builds")
    parser.add_argument("--source", choices=("CI", "LOCAL"), default="CI")
    parser.add_argument("--config", type=Path, default=Path(__file__).with_name("deploy.config.json"))
    parser.add_argument("--services", nargs="+")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--compose-file")
    parser.add_argument("--node", choices=("vps1", "vps2", "vps3"))
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--lock-fd", type=int)
    parser.add_argument("--refresh", action="store_true")
    parser.add_argument("--cached-images", action="store_true", help="Recover using already-present exact digest images; fail if absent")
    args = parser.parse_args()
    try:
        if args.registry_manifest or args.ci_manifest:
            config = json.loads(args.config.read_text(encoding="utf-8"))
            manifest = ci_manifest(config, args.services) if args.ci_manifest else registry_manifest(config, args.registry_manifest, args.services, args.image_tag, args.source)
            encoded = json.dumps(manifest, separators=(",", ":"))
            if args.output:
                args.output.parent.mkdir(parents=True, exist_ok=True)
                args.output.write_text(encoded + "\n", encoding="utf-8")
            else:
                print(encoded)
        else:
            manifest = globals().get("RELEASE_INPUT") or json.loads(os.environ["SPH_RELEASE_JSON"])
            if not args.compose_file or not args.node:
                raise ValueError("Activation requires --node and --compose-file")
            activate(manifest, args.root, args.compose_file, args.node, args.lock_fd, args.refresh, cached_images=args.cached_images)
        return 0
    except (RuntimeError, OSError, ValueError, KeyError, subprocess.TimeoutExpired) as exc:
        print("DEPLOY_FAILED: " + str(exc), flush=True)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
