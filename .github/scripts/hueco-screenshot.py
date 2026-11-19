"""Capture the Severed Gate using Mojang's Linux x86-64 client and an isolated Paper server."""
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import time
import urllib.request
import uuid

VERSION = "1.21.11"
BUILD = 132
USER = "HuecoCamera"
ROOT = Path("target/hueco-capture").resolve()
CLIENT = ROOT / "client"
SERVER = ROOT / "server"
AGENT = "BleachSMP screenshot CI (https://github.com/hmphdev/bleachsmp)"


def download(url, path, digest=None, algorithm="sha1"):
    path.parent.mkdir(parents=True, exist_ok=True)
    for attempt in range(3):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": AGENT})
            with urllib.request.urlopen(request, timeout=45) as response:
                blob = response.read()
            if digest and hashlib.new(algorithm, blob).hexdigest() != digest:
                raise ValueError(f"Checksum mismatch: {url}")
            path.write_bytes(blob)
            return path
        except (OSError, ValueError):
            if attempt == 2:
                raise
            time.sleep(attempt + 1)


def metadata(url, name, digest=None):
    return json.loads(download(url, ROOT / name, digest).read_text())


def prepare_client():
    versions = metadata("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json", "versions.json")
    entry = next(v for v in versions["versions"] if v["id"] == VERSION)
    version = metadata(entry["url"], "version.json", entry["sha1"])
    storage = CLIENT / "assets"
    asset = version["assetIndex"]
    index = json.loads(download(asset["url"], storage / "indexes" / f"{asset['id']}.json", asset["sha1"]).read_text())
    jobs = []
    for lib in version["libraries"]:
        allowed = not lib.get("rules")
        for rule in lib.get("rules", []):
            if not rule.get("os") or rule["os"] == {"name": "linux"}:
                allowed = rule["action"] == "allow"
        name = lib["name"].split(":")[-1]
        if not allowed or (name.startswith("natives-") and name != "natives-linux"):
            continue
        artifact = lib["downloads"]["artifact"]
        jobs.append((artifact["url"], CLIENT / "libraries" / artifact["path"], artifact["sha1"]))
    artifact = version["downloads"]["client"]
    jobs.append((artifact["url"], CLIENT / "client.jar", artifact["sha1"]))
    classpath = os.pathsep.join(str(job[1]) for job in jobs)
    for digest in {value["hash"] for value in index["objects"].values()}:
        relative = f"{digest[:2]}/{digest}"
        jobs.append((f"https://resources.download.minecraft.net/{relative}", storage / "objects" / relative, digest))
    with concurrent.futures.ThreadPoolExecutor(max_workers=24) as pool:
        list(pool.map(lambda job: download(*job), jobs))
    (CLIENT / "options.txt").write_text(
        "onboardAccessibility:false\nskipMultiplayerWarning:true\ntutorialStep:none\n"
        "renderDistance:8\nmaxFps:30\nvsync:false\nguiScale:2\ngamma:1.0\n"
    )
    return ["java", "-Xmx2G", "-cp", classpath, version["mainClass"], "--username", USER,
            "--version", VERSION, "--accessToken", "0", "--userType", "legacy",
            "--gameDir", str(CLIENT), "--assetsDir", str(storage), "--assetIndex", asset["id"],
            "--width", "1152", "--height", "720", "--quickPlayMultiplayer", "127.0.0.1:25565"]


def prepare_server():
    build = metadata(f"https://fill.papermc.io/v3/projects/paper/versions/{VERSION}/builds/{BUILD}", "paper.json")
    jar = build["downloads"]["server:default"]
    download(jar["url"], SERVER / "paper.jar", jar["checksums"]["sha256"], "sha256")
    (SERVER / "plugins").mkdir(exist_ok=True)
    shutil.copy2("target/bleach-smp-0.1.0.jar", SERVER / "plugins")
    camera_id = uuid.UUID(bytes=hashlib.md5(f"OfflinePlayer:{USER}".encode()).digest(), version=3)
    data = SERVER / "plugins/BleachSMP"
    data.mkdir(exist_ok=True)
    (data / "players.yml").write_text(f"players:\n  {camera_id}:\n    faction: SOUL_REAPER\n")
    (SERVER / "eula.txt").write_text("eula=true\n")
    (SERVER / "server.properties").write_text(
        "server-ip=127.0.0.1\nonline-mode=false\nallow-nether=false\n"
        "pause-when-empty-seconds=-1\nview-distance=8\nsimulation-distance=3\n"
        "level-seed=33971\ngenerate-structures=false\n"
    )


def launch(args, directory, name):
    with (ROOT / f"{name}.log").open("w") as log:
        return subprocess.Popen(args, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT,
                                cwd=directory, text=True)


def await_log(process, name, text, seconds):
    path = ROOT / f"{name}.log"
    for _ in range(seconds):
        if text in path.read_text(errors="replace"):
            return
        if process.poll() is not None:
            break
        time.sleep(1)
    raise RuntimeError(f"{name} did not reach {text!r}; see {path}")


def send(server, command):
    server.stdin.write(command + "\n")
    server.stdin.flush()


def main():
    ROOT.mkdir(parents=True, exist_ok=True)
    with concurrent.futures.ThreadPoolExecutor(2) as pool:
        client_setup = pool.submit(prepare_client)
        prepare_server()
        args = client_setup.result()
    server = launch(["java", "-Xmx2G", "-jar", "paper.jar", "--nogui"], SERVER, "paper")
    client = None
    try:
        await_log(server, "paper", "Done (", 180)
        await_log(server, "paper", "BleachSMP enabled", 5)
        client = launch(args, CLIENT, "client")
        await_log(server, "paper", f"{USER} joined the game", 180)
        send(server, f"gamemode spectator {USER}")
        send(server, f"execute in minecraft:huecomundo run tp {USER} 13.5 76.0 25.5 151 4")
        await_log(server, "paper", f"Teleported {USER}", 30)
        time.sleep(12)
        windows = subprocess.check_output(["xdotool", "search", "--onlyvisible", "--name", "Minecraft"], text=True)
        subprocess.run(["xdotool", "windowfocus", windows.splitlines()[-1], "key", "F1", "sleep", "1", "key", "F2"], check=True)
        for _ in range(20):
            shots = sorted((CLIENT / "screenshots").glob("*.png"))
            if shots and shots[-1].stat().st_size > 0:
                time.sleep(1)
                shutil.copy2(shots[-1], ROOT / "hueco-mundo.png")
                return
            time.sleep(1)
        raise RuntimeError("Minecraft did not save an F2 screenshot")
    finally:
        if client and client.poll() is None:
            client.terminate()
        if server.poll() is None:
            send(server, "stop")
            try:
                server.wait(timeout=20)
            except subprocess.TimeoutExpired:
                server.kill()
                server.wait()


if __name__ == "__main__":
    main()
