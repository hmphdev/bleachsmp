"""Photograph individual kits' releases and abilities in Mojang's client with the server resource pack.

Each argument picks a kit as faction:playstyle:index (its place in that playstyle's pool, see Canon.java), for
example soul_reaper:marksman:1 for Senbonzakura. For each kit the camera player releases the first form, then the
advanced form, and fires every ability, taking third-person screenshots into target/hueco-capture/kits/.
Run under a virtual display, like the other screenshot scripts.
"""
import importlib
import shutil
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
effects = importlib.import_module("vfx-screenshot")
hollows = effects.hollows
capture = effects.capture

OUT = capture.ROOT / "kits"
WORLD = effects.WORLD
FLOOR_Y = effects.FLOOR_Y
USER = capture.USER
DEFAULT = ["soul_reaper:vanguard:0", "soul_reaper:marksman:0", "soul_reaper:marksman:1", "soul_reaper:artificer:0",
           "hollow:marksman:0", "hollow:vanguard:2", "quincy:marksman:1", "quincy:artificer:1",
           "fullbringer:vanguard:0", "fullbringer:keeper:0"]


def shot(name, delay):
    time.sleep(delay)
    hollows.OUT = OUT
    hollows.screenshot(name)


def main():
    capture.ROOT.mkdir(parents=True, exist_ok=True)
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)
    kits = [arg for arg in sys.argv[1:] if not arg.startswith("-")] or DEFAULT
    import concurrent.futures
    with concurrent.futures.ThreadPoolExecutor(2) as pool:
        client_setup = pool.submit(capture.prepare_client)
        capture.prepare_server()
        args = client_setup.result()
    hollows.write_servers_dat(True)
    server = capture.launch(["java", "-Xmx2G", "-jar", "paper.jar", "--nogui"], capture.SERVER, "paper")
    client = None
    send = lambda command: capture.send(server, command)
    chat = effects.chat
    try:
        capture.await_log(server, "paper", "Done (", 180)
        capture.await_log(server, "paper", "BleachSMP enabled", 5)
        send(f"execute in {WORLD} run forceload add -48 -48 48 48")
        send(f"execute in {WORLD} run fill -40 {FLOOR_Y} -40 40 {FLOOR_Y} 40 minecraft:sandstone")
        send(f"execute in {WORLD} run fill -40 {FLOOR_Y + 1} -40 40 {FLOOR_Y + 30} 40 minecraft:air")
        send(f"execute in {WORLD} run time set 18000")
        send(f"execute in {WORLD} run kill @e[type=!player]")
        send("gamerule sendCommandFeedback false")
        client = capture.launch(args, capture.CLIENT, "client")
        capture.await_log(server, "paper", f"{USER} joined the game", 180)
        send(f"op {USER}")
        send(f"gamemode adventure {USER}")
        send(f"effect give {USER} minecraft:resistance infinite 255 true")
        send(f"execute in {WORLD} run tp {USER} 0.5 {FLOOR_Y + 1} 8.5 180 8")
        capture.await_log(server, "paper", f"Teleported {USER}", 30)
        time.sleep(30)
        effects.keys("F1")
        time.sleep(1)
        effects.keys("F5")
        for spec in kits:
            faction, style, index = spec.split(":")
            tag = f"{faction}-{style}-{index}"
            for advanced in (False, True):
                send(f"execute in {WORLD} run kill @e[type=zombie]")
                send(f"execute in {WORLD} run kill @e[type=item]")
                for x in (-3, 0, 3):
                    send(f"execute in {WORLD} run summon zombie {x + .5} {FLOOR_Y + 1} 2.5 "
                         "{NoAI:1b,Silent:1b,PersistenceRequired:1b,Rotation:[0f,0f]}")
                send(f"execute in {WORLD} run tp {USER} 0.5 {FLOOR_Y + 1} 8.5 180 8")
                time.sleep(1.5)
                form = "advanced" if advanced else "first"
                chat(f"bleach vfx release {form} {faction} {style} {index}")
                if advanced:
                    shot(f"{tag}-a1-entrance", .5)
                    shot(f"{tag}-a2-entrance", .6)
                    shot(f"{tag}-a3-entrance", .8)
                    shot(f"{tag}-a4-aura", 2.0)
                    chat("bleach vfx use ultimate")
                    shot(f"{tag}-a5-special", .5)
                    shot(f"{tag}-a6-special", 1.0)
                    time.sleep(1.5)
                    chat("bleach vfx use signature")
                    shot(f"{tag}-a7-signature", .5)
                    time.sleep(1.5)
                else:
                    shot(f"{tag}-f1-release", .35)
                    shot(f"{tag}-f2-release", .6)
                    time.sleep(1.2)
                    chat("bleach vfx use main")
                    shot(f"{tag}-f3-main", .3)
                    time.sleep(1.4)
                    chat("bleach vfx use second")
                    shot(f"{tag}-f4-second", .35)
                    time.sleep(1.4)
                    chat("bleach vfx use signature")
                    shot(f"{tag}-f5-signature", .5)
                    time.sleep(1.5)
                chat("bleach vfx seal")
                time.sleep(1)
    finally:
        if client and client.poll() is None:
            client.terminate()
        if server.poll() is None:
            send("stop")
            try:
                server.wait(timeout=20)
            except Exception:
                server.kill()
                server.wait()


if __name__ == "__main__":
    main()
