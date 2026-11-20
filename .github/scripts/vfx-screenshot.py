"""Photograph the ability effects in Mojang's client with the server resource pack, at night in Hueco Mundo.

The camera player releases each faction's advanced form with the admin preview command (/bleach vfx), fires
its techniques, and takes third-person screenshots. Pictures land in target/hueco-capture/vfx/.
Run under a virtual display, like the other screenshot scripts.
"""
import importlib
import shutil
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
hollows = importlib.import_module("hollow-screenshot")
capture = hollows.capture

ROOT = capture.ROOT
CLIENT = capture.CLIENT
SERVER = capture.SERVER
USER = capture.USER
OUT = ROOT / "vfx"
WORLD = "minecraft:huecomundo"
FLOOR_Y = 160


def window():
    windows = subprocess.check_output(["xdotool", "search", "--onlyvisible", "--name", "Minecraft"], text=True)
    return windows.splitlines()[-1]


def keys(*names):
    subprocess.run(["xdotool", "windowfocus", window(), "key", *names], check=True)


def chat(command):
    """Runs a command as the camera player, by typing it into chat."""
    keys("slash")
    time.sleep(.4)
    subprocess.run(["xdotool", "type", "--delay", "15", command], check=True)
    time.sleep(.2)
    keys("Return")


def shot(name, delay):
    time.sleep(delay)
    hollows.OUT = OUT
    hollows.screenshot(name)


def main():
    ROOT.mkdir(parents=True, exist_ok=True)
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)
    import concurrent.futures
    with concurrent.futures.ThreadPoolExecutor(2) as pool:
        client_setup = pool.submit(capture.prepare_client)
        capture.prepare_server()
        args = client_setup.result()
    hollows.write_servers_dat(True)
    server = capture.launch(["java", "-Xmx2G", "-jar", "paper.jar", "--nogui"], SERVER, "paper")
    client = None
    send = lambda command: capture.send(server, command)
    try:
        capture.await_log(server, "paper", "Done (", 180)
        capture.await_log(server, "paper", "BleachSMP enabled", 5)
        send(f"execute in {WORLD} run forceload add -32 -32 32 32")
        send(f"execute in {WORLD} run fill -30 {FLOOR_Y} -30 30 {FLOOR_Y} 30 minecraft:sandstone")
        send(f"execute in {WORLD} run fill -30 {FLOOR_Y + 1} -30 30 {FLOOR_Y + 30} 30 minecraft:air")
        send(f"execute in {WORLD} run time set 18000")
        send(f"execute in {WORLD} run kill @e[type=!player]")
        send(f"execute in {WORLD} run kill @e[type=item]")
        send("gamerule sendCommandFeedback false")
        client = capture.launch(args, CLIENT, "client")
        capture.await_log(server, "paper", f"{USER} joined the game", 180)
        send(f"op {USER}")
        send(f"gamemode adventure {USER}")
        send(f"effect give {USER} minecraft:resistance infinite 255 true")
        send(f"execute in {WORLD} run tp {USER} 0.5 {FLOOR_Y + 1} 8.5 180 10")
        capture.await_log(server, "paper", f"Teleported {USER}", 30)
        # Chunks load, and the client downloads the pack and reloads its resources.
        time.sleep(30)
        keys("F1")
        time.sleep(1)
        keys("F5")
        factions = [arg for arg in sys.argv[1:] if not arg.startswith("-")]
        for faction in factions or ["soul_reaper", "hollow", "quincy", "fullbringer"]:
            send(f"execute in {WORLD} run kill @e[type=zombie]")
            for x in (-3, 0, 3):
                send(f"execute in {WORLD} run summon zombie {x + .5} {FLOOR_Y + 1} -2.5 "
                     "{NoAI:1b,Silent:1b,PersistenceRequired:1b,Rotation:[0f,0f]}")
            send(f"execute in {WORLD} run tp {USER} 0.5 {FLOOR_Y + 1} 8.5 180 10")
            time.sleep(2)
            chat(f"bleach vfx release advanced {faction}")
            shot(f"{faction}-1-bolt", .25)
            shot(f"{faction}-2-pillar", .8)
            shot(f"{faction}-3-shockwave", .25)
            shot(f"{faction}-4-aura", 2.5)
            chat("bleach vfx use main")
            shot(f"{faction}-5-main", .35)
            time.sleep(1.5)
            chat("bleach vfx use second")
            shot(f"{faction}-6-second", .4)
            time.sleep(1.5)
            chat("bleach vfx use ultimate")
            shot(f"{faction}-7-ultimate", .6)
            shot(f"{faction}-8-ultimate-late", 1.2)
            time.sleep(2)
            chat("bleach vfx seal")
            time.sleep(1)
        send(f"execute in {WORLD} run kill @e[type=zombie]")
        send(f"execute in {WORLD} run tp {USER} 0.5 {FLOOR_Y + 1} 14.5 180 15")
        time.sleep(2)
        keys("F5", "F5")
        chat("bleach vfx demo")
        shot("demo-1", .5)
        shot("demo-2", .7)
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
