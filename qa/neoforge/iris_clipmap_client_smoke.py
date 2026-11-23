#!/usr/bin/env python3
"""Launch a real NeoForge singleplayer client with Iris and capture the clipmap.

A temporary dedicated server creates a valid Natural Automate world with
online-mode enabled. The world is then copied into the client saves folder and
opened locally through quick play; no offline-authentication mode is used.
"""
from __future__ import annotations

import gzip
import json
import os
import pathlib
import shutil
import signal
import subprocess
import sys
import threading
import time
import urllib.parse
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
MODULE = ROOT / "neoforge"
RUN = MODULE / "run"
SERVER = RUN  # runServer uses the module run directory as --gameDir .
CLIENT_SAVES = RUN / "saves"
WORLD_NAME = "iris-clipmap-smoke"
BUILD = MODULE / "build" / "iris-clipmap-smoke"
USER_AGENT = "RealScale-GeometryClipmap-Iris-Smoke/1.0 (https://github.com/lord-navy-crypto/real-world-terrain)"
RCON_PASSWORD = "realscale_ci_probe_only"
RCON_PORT = 25575
SERVER_PORT = 25565


def api_json(url: str):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.loads(response.read().decode("utf-8"))


def download_version_file(version: dict, destination: pathlib.Path) -> pathlib.Path:
    files = version.get("files") or []
    file_info = next((item for item in files if item.get("primary")), files[0] if files else None)
    if file_info is None:
        raise RuntimeError("Modrinth version has no downloadable file: " + str(version.get("id")))
    path = destination / file_info["filename"]
    request = urllib.request.Request(file_info["url"], headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=180) as response, path.open("wb") as output:
        shutil.copyfileobj(response, output)
    if path.stat().st_size < 100_000:
        raise RuntimeError("Downloaded mod/shader file is unexpectedly small: " + str(path))
    print("DOWNLOADED_MODRINTH_FILE:", path.name, "bytes=", path.stat().st_size, flush=True)
    return path


def latest_version(project: str, loaders: list[str] | None = None) -> dict:
    params = {"game_versions": json.dumps(["1.21.1"])}
    if loaders:
        params["loaders"] = json.dumps(loaders)
    url = "https://api.modrinth.com/v2/project/" + project + "/version?" + urllib.parse.urlencode(params)
    versions = api_json(url)
    releases = [v for v in versions if v.get("version_type") == "release"]
    if not releases:
        raise RuntimeError("No release found for Modrinth project " + project)
    return releases[0]


def install_iris_stack() -> str:
    mods = RUN / "mods"
    shaders = RUN / "shaderpacks"
    config = RUN / "config"
    mods.mkdir(parents=True, exist_ok=True)
    shaders.mkdir(parents=True, exist_ok=True)
    config.mkdir(parents=True, exist_ok=True)

    iris = latest_version("iris", ["neoforge"])
    download_version_file(iris, mods)

    # Prefer Iris's exact declared Sodium dependency; otherwise select the
    # latest matching NeoForge 1.21.1 release from Modrinth.
    sodium_dependency = next(
        (dep for dep in iris.get("dependencies", [])
         if dep.get("dependency_type") == "required"
         and dep.get("project_id") == "AANobbMI"),
        None,
    )
    if sodium_dependency and sodium_dependency.get("version_id"):
        sodium = api_json("https://api.modrinth.com/v2/version/" + sodium_dependency["version_id"])
    else:
        sodium = latest_version("sodium", ["neoforge"])
    download_version_file(sodium, mods)

    shader = latest_version("complementary-reimagined")
    shader_file = download_version_file(shader, shaders)
    (config / "iris.properties").write_text(
        "shaderPack=" + shader_file.name + "\n"
        "enableShaders=true\n"
        "allowUnknownShaders=true\n"
        "enableDebugOptions=true\n"
        "disableUpdateMessage=true\n"
        "maxShadowRenderDistance=32\n"
        "colorSpace=SRGB\n",
        encoding="utf-8",
    )
    (RUN / "options.txt").write_text(
        "renderDistance:10\n"
        "simulationDistance:6\n"
        "guiScale:2\n"
        "fullscreen:false\n"
        "maxFps:60\n"
        "lang:en_us\n",
        encoding="utf-8",
    )
    print("IRIS_VERSION:", iris.get("version_number"), iris.get("id"), flush=True)
    print("SODIUM_VERSION:", sodium.get("version_number"), sodium.get("id"), flush=True)
    print("SHADERPACK_VERSION:", shader.get("version_number"), shader.get("id"), flush=True)
    print("SHADERPACK_FILE:", shader_file.name, flush=True)
    return shader_file.name


def drain_process(proc: subprocess.Popen, log_path: pathlib.Path,
                  ready: threading.Event | None = None,
                  draw: threading.Event | None = None) -> list[str]:
    lines: list[str] = []

    def reader() -> None:
        assert proc.stdout is not None
        with log_path.open("w", encoding="utf-8", errors="replace") as output:
            for line in proc.stdout:
                lines.append(line)
                output.write(line)
                output.flush()
                if ready is not None and "Done (" in line and "For help" in line:
                    ready.set()
                if draw is not None and "firstIrisNativeDraw=" in line:
                    draw.set()

    threading.Thread(target=reader, daemon=True).start()
    return lines


def stop_process(proc: subprocess.Popen, timeout: int = 25) -> None:
    if proc.poll() is not None:
        return
    try:
        os.killpg(proc.pid, signal.SIGTERM)
    except ProcessLookupError:
        return
    try:
        proc.wait(timeout=timeout)
    except subprocess.TimeoutExpired:
        try:
            os.killpg(proc.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        proc.wait(timeout=15)


def prepare_saved_world(env: dict[str, str]) -> None:
    from rcon_worldgen_smoke import Rcon

    SERVER.mkdir(parents=True, exist_ok=True)
    (SERVER / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    properties = (
        "level-name=world\n"
        "level-type=realscale:natural_automate_three_realms\n"
        "level-seed=785312\n"
        "generate-structures=true\n"
        "view-distance=6\n"
        "simulation-distance=4\n"
        "max-tick-time=-1\n"
        "online-mode=true\n"
        "server-port=25565\n"
        "server-ip=127.0.0.1\n"
        "max-players=1\n"
        "gamemode=creative\n"
        "spawn-protection=0\n"
        "enable-rcon=true\n"
        "rcon.port=25575\n"
        "rcon.password=" + RCON_PASSWORD + "\n"
        "broadcast-rcon-to-ops=false\n"
        "motd=RealScale Iris clipmap smoke\n"
    )
    (SERVER / "server.properties").write_text(properties, encoding="utf-8")

    log_path = BUILD / "server.log"
    ready = threading.Event()
    proc = subprocess.Popen(
        ["gradle", "--no-daemon", "--console=plain", "runServer", "--stacktrace"],
        cwd=MODULE, env=env, stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        text=True, bufsize=1, start_new_session=True,
    )
    lines = drain_process(proc, log_path, ready=ready)
    rcon = None
    try:
        deadline = time.monotonic() + 570
        while not ready.is_set() and proc.poll() is None and time.monotonic() < deadline:
            time.sleep(1)
        if not ready.is_set():
            raise RuntimeError("Natural Automate server did not reach Done")
        print("NATURAL_AUTOMATE_SERVER_BOOT_OK", flush=True)

        last_error = None
        for _ in range(30):
            try:
                rcon = Rcon(host="127.0.0.1", port=RCON_PORT,
                            password=RCON_PASSWORD, timeout=120)
                break
            except (ConnectionError, OSError, TimeoutError) as exc:
                last_error = exc
                time.sleep(1)
        if rcon is None:
            raise RuntimeError("Could not connect to local test-server RCON: " + repr(last_error))

        rcon.send("save-all flush")
        rcon.send("stop")
        proc.wait(timeout=120)
    finally:
        if rcon is not None:
            rcon.close()
        stop_process(proc)
        (BUILD / "server.log").write_text("".join(lines), encoding="utf-8", errors="replace")

    source_world = SERVER / "world"
    level_dat = source_world / "level.dat"
    if not level_dat.exists():
        raise RuntimeError("Server did not persist a level.dat at " + str(level_dat))
    try:
        level_bytes = gzip.decompress(level_dat.read_bytes())
        if b"natural_automate_three_realms" not in level_bytes:
            print("WARNING: level.dat did not contain the expected preset identifier", flush=True)
    except Exception as exc:
        raise RuntimeError("Could not inspect saved world level.dat: " + repr(exc)) from exc

    destination = CLIENT_SAVES / WORLD_NAME
    if destination.exists():
        shutil.rmtree(destination)
    CLIENT_SAVES.mkdir(parents=True, exist_ok=True)
    shutil.copytree(source_world, destination)
    print("COPIED_SAVED_SINGLEPLAYER_WORLD:", destination, flush=True)


def capture_screenshot(name: str) -> pathlib.Path | None:
    BUILD.mkdir(parents=True, exist_ok=True)
    xwd = BUILD / (name + ".xwd")
    png = BUILD / (name + ".png")
    try:
        subprocess.run(["xwd", "-root", "-silent", "-out", str(xwd)],
                       check=True, timeout=20)
        subprocess.run(["convert", str(xwd), str(png)],
                       check=True, timeout=30)
        print("SCREENSHOT_CAPTURED:", png, "bytes=", png.stat().st_size, flush=True)
        return png
    except (OSError, subprocess.SubprocessError) as exc:
        print("SCREENSHOT_CAPTURE_FAILED:", repr(exc), flush=True)
        return None


def run_client(env: dict[str, str]) -> None:
    if not env.get("DISPLAY"):
        raise RuntimeError("A virtual X display is required for the client render smoke")
    log_path = BUILD / "client-stdout.log"
    draw = threading.Event()
    proc = subprocess.Popen(
        ["gradle", "--no-daemon", "--console=plain", "runClient",
         "--args=--quickPlaySingleplayer " + WORLD_NAME],
        cwd=MODULE, env=env, stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        text=True, bufsize=1, start_new_session=True,
    )
    lines = drain_process(proc, log_path, draw=draw)
    try:
        deadline = time.monotonic() + 480
        while not draw.is_set() and proc.poll() is None and time.monotonic() < deadline:
            time.sleep(1)
        capture_screenshot("iris-clipmap-native")
        if not draw.is_set():
            raise RuntimeError(
                "Client did not submit an Iris-native clipmap draw within the test window"
            )
        time.sleep(8)
        capture_screenshot("iris-clipmap-native-after-stable-frames")
        print("IRIS_NATIVE_RENDER_SMOKE_PASS", flush=True)
    finally:
        stop_process(proc, timeout=35)
        (BUILD / "client-stdout.log").write_text(
            "".join(lines), encoding="utf-8", errors="replace")
        latest = RUN / "logs" / "latest.log"
        if latest.exists():
            shutil.copy2(latest, BUILD / "client-latest.log")
        if proc.poll() is None:
            stop_process(proc, timeout=10)


def main() -> int:
    BUILD.mkdir(parents=True, exist_ok=True)
    if not shutil.which("xwd") or not shutil.which("convert"):
        raise RuntimeError("xwd and ImageMagick convert are required for screenshots")
    env = os.environ.copy()
    env["JAVA_TOOL_OPTIONS"] = (
        env.get("JAVA_TOOL_OPTIONS", "") +
        " -Drealscale.experimental.geometryclipmap=true"
        " -Drealscale.experimental.geometryclipmap.debug=true"
    ).strip()
    # The temporary dedicated server must not see client-only Iris/Sodium mods.
    # Generate and save the world first; install the shader stack only for runClient.
    prepare_saved_world(env)
    install_iris_stack()
    run_client(env)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print("IRIS_NATIVE_RENDER_SMOKE_FAIL:", repr(exc), flush=True)
        raise
