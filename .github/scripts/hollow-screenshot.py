"""Photograph each Hollow model with the server resource pack, in Mojang's client against an isolated Paper server.

Run under a virtual display (see the hueco-screenshot workflow). Pictures land in target/hueco-capture/hollows/.
Pass --no-pack to photograph the block-display fallback instead.
"""
import shutil
import struct
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import importlib

capture = importlib.import_module("hueco-screenshot")
_download = capture.download


def cached_download(url, path, digest=None, algorithm="sha1"):
    # Reuse files from an earlier run when their checksum still matches.
    if digest and path.exists():
        import hashlib
        if hashlib.new(algorithm, path.read_bytes()).hexdigest() == digest:
            return path
    return _download(url, path, digest, algorithm)


capture.download = cached_download

ROOT = capture.ROOT
CLIENT = capture.CLIENT
SERVER = capture.SERVER
USER = capture.USER
OUT = ROOT / "hollows"
WORLD = "minecraft:huecomundo"
# A clean floor high above the dunes, lit by day, so the models are easy to judge.
FLOOR_Y = 160
HOLLOWS = [
    # type, x, z, yaw, scale
    ("gillian", 0, 0, 0, 5.5),
    ("adjuchas", 14, 2, 30, 1.15),
    ("vasto_lorde", -12, 3, -25, 1.35),
]
VIEWS = [
    # name, x, y, z, yaw, pitch (camera eye position)
    ("lineup", 1.5, FLOOR_Y + 9, 34.5, 180, 8),
    ("gillian-front", 0.5, FLOOR_Y + 8, 17.5, 180, 0),
    ("gillian-mask", 0.5, FLOOR_Y + 10, 5.5, 180, 0),
    ("gillian-side", 18.5, FLOOR_Y + 9, 0.5, 90, 8),
    ("adjuchas", 15.5, FLOOR_Y + 3, 9.5, 160, 12),
    ("adjuchas-side", 22.5, FLOOR_Y + 3, 1.5, 100, 12),
    ("vasto-lorde", -10.5, FLOOR_Y + 3.4, 9.5, 195, 6),
    ("vasto-lorde-back", -14.5, FLOOR_Y + 3.4, -3.5, -30, 8),
]


def nbt_string(value):
    data = value.encode()
    return struct.pack(">H", len(data)) + data


def write_servers_dat(accept):
    """A saved server entry whose resource pack policy is set, so no prompt appears."""
    entry = (b"\x08" + nbt_string("ip") + nbt_string("127.0.0.1:25565")
             + b"\x08" + nbt_string("name") + nbt_string("Capture")
             + b"\x01" + nbt_string("acceptTextures") + bytes([1 if accept else 0]) + b"\x00")
    servers = b"\x09" + nbt_string("servers") + b"\x0a" + struct.pack(">i", 1) + entry
    (CLIENT / "servers.dat").write_bytes(b"\x0a" + nbt_string("") + servers + b"\x00")


def screenshot(name):
    before = set((CLIENT / "screenshots").glob("*.png")) if (CLIENT / "screenshots").exists() else set()
    windows = subprocess.check_output(["xdotool", "search", "--onlyvisible", "--name", "Minecraft"], text=True)
    subprocess.run(["xdotool", "windowfocus", windows.splitlines()[-1], "key", "F2"], check=True)
    for _ in range(20):
        shots = set((CLIENT / "screenshots").glob("*.png")) - before
        if shots:
            shot = max(shots, key=lambda path: path.stat().st_mtime)
            if shot.stat().st_size > 0:
                time.sleep(1)
                shutil.copy2(shot, OUT / f"{name}.png")
                return
        time.sleep(1)
    raise RuntimeError(f"Minecraft did not save the {name} screenshot")


def main():
    pack = "--no-pack" not in sys.argv
    ROOT.mkdir(parents=True, exist_ok=True)
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)
    import concurrent.futures
    with concurrent.futures.ThreadPoolExecutor(2) as pool:
        client_setup = pool.submit(capture.prepare_client)
        capture.prepare_server()
        args = client_setup.result()
    write_servers_dat(pack)
    server = capture.launch(["java", "-Xmx2G", "-jar", "paper.jar", "--nogui"], SERVER, "paper")
    client = None
    send = lambda command: capture.send(server, command)
    try:
        capture.await_log(server, "paper", "Done (", 180)
        capture.await_log(server, "paper", "BleachSMP enabled", 5)
        send(f"execute in {WORLD} run forceload add -32 -32 32 32")
        send(f"execute in {WORLD} run fill -30 {FLOOR_Y} -24 30 {FLOOR_Y} 40 minecraft:sandstone")
        send(f"execute in {WORLD} run fill -30 {FLOOR_Y + 1} -24 30 {FLOOR_Y + 30} 40 minecraft:air")
        send(f"execute in {WORLD} run time set noon")
        send(f"execute in {WORLD} run kill @e[type=!player]")
        send(f"execute in {WORLD} run kill @e[type=item]")
        for kind, x, z, yaw, scale in HOLLOWS:
            mob = "wither_skeleton" if kind == "vasto_lorde" else "husk"
            send(f"execute in {WORLD} run summon {mob} {x + .5} {FLOOR_Y + 1} {z + .5} "
                 f"{{NoAI:1b,PersistenceRequired:1b,Rotation:[{yaw}f,0f],"
                 f"attributes:[{{id:\"minecraft:scale\",base:{scale}}}],"
                 f"BukkitValues:{{\"bleachsmp:hollow_type\":\"{kind}\"}}}}")
        client = capture.launch(args, CLIENT, "client")
        capture.await_log(server, "paper", f"{USER} joined the game", 180)
        # Spectators see invisible mobs, so stand an adventure-mode camera on barriers instead.
        send(f"gamemode adventure {USER}")
        send(f"effect give {USER} minecraft:resistance infinite 255 true")
        for _, x, y, z, _, _ in VIEWS:
            send(f"execute in {WORLD} run setblock {int(x // 1)} {int((y - 1.62) // 1) - 1} {int(z // 1)} minecraft:barrier")
        first = VIEWS[0]
        send(f"execute in {WORLD} run tp {USER} {first[1]} {(first[2] - 1.62) // 1} {first[3]} {first[4]} {first[5]}")
        capture.await_log(server, "paper", f"Teleported {USER}", 30)
        # Chunks load, and the client downloads the pack and reloads its resources.
        time.sleep(30)
        windows = subprocess.check_output(["xdotool", "search", "--onlyvisible", "--name", "Minecraft"], text=True)
        subprocess.run(["xdotool", "windowfocus", windows.splitlines()[-1], "key", "F1"], check=True)
        for name, x, y, z, yaw, pitch in VIEWS:
            send(f"execute in {WORLD} run tp {USER} {x} {(y - 1.62) // 1} {z} {yaw} {pitch}")
            time.sleep(4)
            screenshot(name)
    finally:
        if client and client.poll() is None:
            client.terminate()
        if server.poll() is None:
            send("stop")
            try:
                server.wait(timeout=20)
            except subprocess.TimeoutExpired:
                server.kill()
                server.wait()


if __name__ == "__main__":
    main()
