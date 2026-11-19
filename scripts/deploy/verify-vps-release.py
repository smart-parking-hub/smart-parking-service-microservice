"""Read-only release check: current main + CI/verified Local digests -> VPS.

Requires Python 3.10+, gh, ssh, Docker buildx/manifest commands and pretrusted SSH hosts.
Never pulls images, starts/stops containers, edits VPS files or reads secret env files.
"""

import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import threading
import time


ROOT = Path(__file__).resolve().parents[2]
CONFIG = ROOT / "scripts/deploy/deploy.config.json"


def enable_terminal_updates(stream):
    """Enable ANSI on Windows consoles; return cleanup or None if unsupported."""
    if os.name != "nt":
        return lambda: None
    try:
        import ctypes
        from ctypes import wintypes
        import msvcrt
        kernel = ctypes.WinDLL("kernel32", use_last_error=True)
        kernel.GetConsoleMode.argtypes = (wintypes.HANDLE, ctypes.POINTER(wintypes.DWORD))
        kernel.SetConsoleMode.argtypes = (wintypes.HANDLE, wintypes.DWORD)
        handle = msvcrt.get_osfhandle(stream.fileno())
        mode = wintypes.DWORD()
        if kernel.GetConsoleMode(handle, ctypes.byref(mode)) and kernel.SetConsoleMode(handle, mode.value | 0x0004):
            return lambda: kernel.SetConsoleMode(handle, mode.value)
    except (AttributeError, OSError, ValueError):
        pass
    return None


class ConsoleProgress:
    """Transient CLI-only display. Redirected logs contain results, not animation."""
    steps = (
        "[1/3] Doc main va artifact CI tren GitHub...",
        "[2/3] Doc image, deployment history va health tren tung VPS...",
        "[3/3] Doi chieu digest registry va container dang chay...",
    )

    def __init__(self, stream=None, interval=0.1):
        self.stream = stream if stream is not None else sys.stderr
        self.interval = interval
        self.started = time.monotonic()
        self.current = 1
        self.frame = 0
        self.rendered_stage = None
        self.stopped = threading.Event()
        self.lock = threading.Lock()
        self.worker = None
        self.restore = None
        self.enabled = False
        self.closed = False

    @property
    def elapsed(self):
        return int(time.monotonic() - self.started)

    def __enter__(self):
        self.enabled = self.stream.isatty()
        if self.enabled:
            self.restore = enable_terminal_updates(self.stream)
            if self.restore is not None:
                self.stream.write("\x1b[2J\x1b[H\x1b[?25l")
            self.render()
            self.worker = threading.Thread(target=self.animate, daemon=True)
            self.worker.start()
        return self

    def stage(self, number):
        with self.lock:
            self.current = number
        self.render()

    def render(self):
        with self.lock:
            if not self.enabled or self.closed:
                return
            width = max(1, shutil.get_terminal_size((100, 24)).columns - 1)
            fit = lambda text: text if len(text) <= width else text[:max(0, width - 3)] + "." * min(3, width)
            elapsed = self.elapsed
            spinner = "|/-\\"[self.frame % 4]
            self.frame += 1
            timer = f"  [ {spinner} ] Buoc {self.current}/3   |   Thoi gian: {elapsed // 60:02d}:{elapsed % 60:02d}"
            if self.restore is not None:
                if self.rendered_stage == self.current:
                    # Only the timer row changes between phases; avoid flicker.
                    self.stream.write("\x1b[4B\r\x1b[96m" + fit(timer) + "\x1b[0m\x1b[K\r\x1b[4A")
                else:
                    lines = []
                    for number, text in enumerate(self.steps, 1):
                        color = "32" if number < self.current else "96" if number == self.current else "90"
                        lines.append("\x1b[" + color + "m" + fit(text) + "\x1b[0m")
                    lines += ["", "\x1b[96m" + fit(timer) + "\x1b[0m", fit("  Vui long cho; khong can nhan Enter.")]
                    # Leave the cursor at the block's start for repaint/erase.
                    self.stream.write("\r" + "\x1b[K\n".join(lines) + "\x1b[K\n\x1b[6A")
                    self.rendered_stage = self.current
            else:
                # Legacy console: one changing line, removable without ANSI.
                self.stream.write("\r" + fit(f"[{self.current}/3] Dang kiem tra VPS | {elapsed // 60:02d}:{elapsed % 60:02d}").ljust(width))
            self.stream.flush()

    def animate(self):
        while not self.stopped.wait(self.interval):
            self.render()

    def close(self):
        self.stopped.set()
        if self.worker is not None:
            self.worker.join()
        with self.lock:
            if self.closed:
                return
            self.closed = True
            if self.enabled:
                if self.restore is not None:
                    self.stream.write("\r\x1b[J\x1b[0m\x1b[?25h")
                else:
                    width = max(1, shutil.get_terminal_size((100, 24)).columns - 1)
                    self.stream.write("\r" + " " * width + "\r")
                self.stream.flush()
                if self.restore is not None:
                    self.restore()

    def __exit__(self, *_):
        self.close()

# Only metadata and anonymous health GETs leave the server. Raw inspect/env stays in memory.
REMOTE = r'''
import json,subprocess,urllib.request
from pathlib import Path
def read(args):
 p=subprocess.run(args,capture_output=True,text=True,timeout=25)
 if p.returncode:raise RuntimeError('Docker metadata read failed')
 return p.stdout
ids=read(['docker','ps','-aq','--filter','label=com.docker.compose.project=docker']).split()
containers=json.loads(read(['docker','inspect',*ids])) if ids else []
history=[]
for path in sorted((Path.cwd()/'.deploy-history').glob('*.json'),reverse=True):
 try:
  record=json.loads(path.read_text(encoding='utf-8'))
  if isinstance(record,dict):history.append(record)
 except (OSError,ValueError):pass
out=[]
for d in containers:
 labels=d['Config'].get('Labels') or {}
 service=labels.get('com.docker.compose.service','')
 if not service.startswith('smart-parking-'):continue
 im=json.loads(read(['docker','image','inspect',d['Image']]))[0]
 state=d['State']['Status']
 item={'service':service,'name':d['Name'].lstrip('/'),'state':state,'containerId':d['Id'][:12],'imageId':d['Image'],'inspectedImageId':im.get('Id'),'imageRef':d['Config']['Image'],'digests':im.get('RepoDigests',[]),'startedAt':d['State'].get('StartedAt'),'imageCreated':im.get('Created'),'revision':(im.get('Config',{}).get('Labels') or {}).get('org.opencontainers.image.revision')}
 for record in history:
  verified=record.get('verified') or {}
  expected=record.get('expected') or {}
  if not isinstance(verified,dict) or not isinstance(expected,dict):continue
  entry=expected.get(service) or {}
  if verified.get(service)==d['Id'] and isinstance(entry,dict):
   item['deployment']={'source':record.get('source','CI'),'sourceSha':record.get('sourceSha'),'image':entry.get('image'),'buildRun':record.get('buildRun'),'releaseId':record.get('releaseId')}
   break
 if state=='running':
  env=dict(x.split('=',1) for x in d['Config'].get('Env',[]) if '=' in x)
  port=env.get('PORT')
  bindings=d['NetworkSettings'].get('Ports',{}).get(str(port)+'/tcp') or []
  target=next(('http://127.0.0.1:'+b['HostPort'] for b in bindings if b.get('HostIp') in ('0.0.0.0','127.0.0.1','')),None)
  if target is None and port:
   ips=[n.get('IPAddress') for n in d['NetworkSettings'].get('Networks',{}).values() if n.get('IPAddress')]
   if ips:target='http://'+ips[0]+':'+port
  if target:
   item['healthUrl']=target+'/actuator/health'
   try:
    opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
    with opener.open(item['healthUrl'],timeout=8) as response:item['health']=json.load(response).get('status','UNKNOWN')
   except Exception as exc:item['health']='UNKNOWN';item['healthError']=type(exc).__name__
 out.append(item)
print(json.dumps(out))
'''


def command(args, *, input_text=None, timeout=60):
    result = subprocess.run(args, input=input_text.encode() if input_text else None,
                            cwd=ROOT, capture_output=True, timeout=timeout)
    if result.returncode:
        # Avoid emitting credentials or full provider responses from stderr.
        raise RuntimeError(args[0] + " command failed (exit " + str(result.returncode) + ")")
    return result.stdout.decode("utf-8")


def published_digests(log):
    current = None
    result = {}
    for line in log.splitlines():
        service = re.search(r"Building and pushing ([a-z-]+)\.\.\.", line)
        if service:
            current = service.group(1)
        digest = re.search(r"latest: digest: (sha256:[a-f0-9]{64})", line)
        if digest and current:
            result[current] = digest.group(1)
    return result


def gh_json(*args):
    return json.loads(command(["gh", *args]))


def expected_release(repo, run_id=None):
    head = gh_json("api", "repos/" + repo + "/commits/main")["sha"]
    if run_id:
        runs = [gh_json("run", "view", str(run_id), "--repo", repo,
                        "--json", "databaseId,headSha,headBranch,workflowName,url,status")]
    else:
        runs = gh_json("run", "list", "--repo", repo, "--workflow", "cd-deploy.yml",
                       "--branch", "main", "--limit", "20", "--json", "databaseId,headSha,headBranch,workflowName,url,status")
        runs = [r for r in runs if r["headSha"] == head]
    for run in runs:
        if run.get("headBranch") != "main":
            raise RuntimeError("Expected release must be built from main")
        jobs = gh_json("run", "view", str(run["databaseId"]), "--repo", repo, "--json", "jobs")["jobs"]
        build = next((j for j in jobs if j["name"] == "Build JARs & Push Docker Images" and j["conclusion"] == "success"), None)
        if build:
            log = command(["gh", "run", "view", str(run["databaseId"]), "--repo", repo,
                           "--job", str(build["databaseId"]), "--log"])
            return {"mainSha": head, "releaseSha": run["headSha"], "runId": run["databaseId"],
                    "runUrl": run["url"], "digests": published_digests(log)}
    raise RuntimeError("No successful image build for the requested main release was found")


def registry_identity(image, expected, check_latest=True):
    latest = json.loads(command(["docker", "buildx", "imagetools", "inspect", image + ":latest",
                                "--format", "{{json .Manifest}}"], timeout=60)) if check_latest else {}
    manifests = json.loads(command(["docker", "manifest", "inspect", "--verbose", image + "@" + expected]))
    if not isinstance(manifests, list):
        manifests = [manifests]
    identities = {expected}
    for entry in manifests:
        descriptor = entry.get("Descriptor", {})
        platform = descriptor.get("platform", {})
        if platform.get("os") == "unknown" or platform.get("architecture") == "unknown":
            continue  # An attestation is not a runnable image.
        manifest = entry.get("OCIManifest") or entry.get("SchemaV2Manifest") or {}
        for digest in (descriptor.get("digest"), manifest.get("config", {}).get("digest")):
            if digest:
                identities.add(digest)
    return {"latestDigest": latest.get("digest"), "acceptedImageIds": sorted(identities)}


def node_snapshot(node):
    key = str(Path(node["keyPath"]).expanduser())
    directory = node.get("targetDir", "~/smartparking")
    if not re.fullmatch(r"~?/[a-zA-Z0-9/_-]+", directory):
        raise ValueError("Unsupported remote target path")
    directory = "$HOME/" + directory[2:] if directory.startswith("~/") else directory
    args = ["ssh", "-i", key, "-p", str(node["port"]), "-o", "BatchMode=yes",
            "-o", "StrictHostKeyChecking=yes", "-o", "ConnectTimeout=8",
            node["user"] + "@" + node["host"], "cd " + directory + " && python3 -"]
    return json.loads(command(args, input_text=REMOTE, timeout=150))


def assess(containers, compose_service, expected, registry, require_latest=True):
    candidates = [c for c in containers if c["service"] == compose_service]
    running = [c for c in candidates if c["state"] == "running"]
    result = {"expectedDigest": expected, "latestDigest": registry["latestDigest"],
              "nonRunning": [{"name": c["name"], "state": c["state"], "imageId": c["imageId"]}
                             for c in candidates if c["state"] != "running"]}
    if len(running) != 1:
        result["status"] = "MISSING" if not running else "MULTIPLE_RUNNING"
        return result
    active = running[0]
    identities = {active["imageId"], active.get("inspectedImageId")}
    identities.update(d.rsplit("@", 1)[1] for d in active.get("digests", []) if "@" in d)
    result.update({"container": active, "artifactMatches": bool(identities & set(registry["acceptedImageIds"]))})
    if not result["artifactMatches"]:
        result["status"] = "STALE_ARTIFACT"
    elif require_latest and registry["latestDigest"] != expected:
        result["status"] = "REGISTRY_MOVED"
    elif active.get("health") != "UP":
        result["status"] = "HEALTH_UNVERIFIED"
    else:
        result["status"] = "CURRENT"
    return result


def service_expectation(containers, service, config, main_sha, release=None, force_ci=False):
    target = config["services"][service]
    active = [c for c in containers if c["service"] == target["composeService"] and c["state"] == "running"]
    local = active[0].get("deployment") if len(active) == 1 else None
    if not force_ci and local and local.get("source") == "LOCAL":
        prefix = config["registry"]["imagePrefix"] + "/" + service
        image = local.get("image") or ""
        if not isinstance(image, str) or not isinstance(local.get("sourceSha"), str) or not re.fullmatch(re.escape(prefix) + r"@sha256:[a-f0-9]{64}", image) or not re.fullmatch(r"[a-f0-9]{40}", local["sourceSha"]):
            raise ValueError("Invalid verified LOCAL deployment metadata")
        return {"source": "LOCAL", "sourceSha": local["sourceSha"], "digest": image.rsplit("@", 1)[1], "image": prefix}
    if release is None or service not in release.get("digests", {}):
        raise ValueError("No verified LOCAL record or successful CI artifact for the requested main release")
    return {"source": "CI", "sourceSha": release["releaseSha"], "digest": release["digests"][service],
            "image": config["registry"]["imagePrefix"] + "/" + service}


def assess_release(containers, compose_service, expected, registry, main_sha):
    row = assess(containers, compose_service, expected["digest"], registry, expected["source"] == "CI")
    row.update(source=expected["source"], sourceSha=expected["sourceSha"])
    if expected["sourceSha"] != main_sha:
        row["status"] = "STALE_SOURCE"
    elif expected["source"] == "LOCAL" and row.get("artifactMatches") and row["container"].get("revision") != expected["sourceSha"]:
        row["status"] = "REVISION_MISMATCH"
    return row


def main(progress=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-id", type=int, help="Expected CI build; default: latest completed image build for main HEAD")
    parser.add_argument("--output", type=Path, help="Save a local JSON report containing selected metadata only")
    args = parser.parse_args()
    config = json.loads(CONFIG.read_text(encoding="utf-8"))
    report = {"checkedAtUtc": datetime.now(timezone.utc).isoformat(), "services": {}, "errors": {}, "warnings": {}}
    if progress is not None:
        progress.stage(1)
    try:
        repo = gh_json("repo", "view", "--json", "nameWithOwner")["nameWithOwner"]
        main_sha = gh_json("api", "repos/" + repo + "/commits/main")["sha"]
        release = None
        try:
            release = expected_release(repo, args.run_id)
            main_sha = release["mainSha"]
        except (RuntimeError, OSError, ValueError, subprocess.TimeoutExpired) as exc:
            if args.run_id:
                raise
            report["warnings"]["ciBuild"] = str(exc)
        report["release"] = {"mainSha": main_sha, "releaseSha": main_sha,
                             "runId": release["runId"] if release else None}
        if progress is not None:
            progress.stage(2)
        with ThreadPoolExecutor(max_workers=4) as pool:
            node_tasks = {n: pool.submit(node_snapshot, node) for n, node in config["nodes"].items()}
            snapshots = {}
            for n, task in node_tasks.items():
                try:
                    snapshots[n] = task.result()
                except (RuntimeError, OSError, ValueError, subprocess.TimeoutExpired) as exc:
                    report["errors"][n] = str(exc)
            plans = {}
            if progress is not None:
                progress.stage(3)
            for name, target in config["services"].items():
                try:
                    if target["node"] not in snapshots:
                        raise RuntimeError("VPS snapshot is unavailable")
                    plans[name] = service_expectation(snapshots[target["node"]], name, config, main_sha, release, bool(args.run_id))
                except (RuntimeError, OSError, ValueError) as exc:
                    report["errors"][name] = str(exc)
            registry_tasks = {name: pool.submit(registry_identity, e["image"], e["digest"], e["source"] == "CI")
                              for name, e in plans.items()}
            for name, task in registry_tasks.items():
                target = config["services"][name]
                try:
                    row = assess_release(snapshots[target["node"]], target["composeService"], plans[name], task.result(), main_sha)
                    report["services"][name] = {"node": target["node"], **row}
                except (RuntimeError, OSError, ValueError, subprocess.TimeoutExpired) as exc:
                    report["errors"][name] = str(exc)
    except (RuntimeError, OSError, ValueError, subprocess.TimeoutExpired) as exc:
        report["errors"]["release"] = str(exc)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    if progress is not None:
        progress.close()  # Erase the display before printing any permanent results.
    if "release" in report:
        print("Current main " + report["release"]["mainSha"] + " / CI build " + str(report["release"]["runId"]))
    for s, row in report["services"].items():
        print(row["node"] + " | " + s + " | " + row["source"] + " | " + row["status"] + " | pending=" + str(len(row["nonRunning"])))
    for key, error in report["errors"].items():
        print("UNVERIFIED " + key + ": " + error)
    for key, warning in report["warnings"].items():
        print("WARNING " + key + ": " + warning)
    current = len(report["services"]) == len(config["services"]) and all(r["status"] == "CURRENT" for r in report["services"].values())
    return 0 if current and not report["errors"] else 1


def console_main():
    with ConsoleProgress() as progress:
        try:
            return main(progress)
        finally:
            progress.close()
            sys.stdout.flush()
            print("[KET THUC BUOC] Kiem tra release VPS | " + str(progress.elapsed) + "s.", flush=True)


if __name__ == "__main__":
    raise SystemExit(console_main())
