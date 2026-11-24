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
import re
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
CLIENT_RUN = RUN / "iris-clipmap-client"
CLIENT_SAVES = CLIENT_RUN / "saves"
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
    CLIENT_RUN.mkdir(parents=True, exist_ok=True)
    mods = CLIENT_RUN / "mods"
    shaders = CLIENT_RUN / "shaderpacks"
    config = CLIENT_RUN / "config"
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
    (CLIENT_RUN / "options.txt").write_text(
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
                  draw: threading.Event | None = None) -> tuple[list[str], threading.Thread]:
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

    reader_thread = threading.Thread(target=reader, daemon=True)
    reader_thread.start()
    return lines, reader_thread


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
    old_world = SERVER / "world"
    if old_world.exists():
        shutil.rmtree(old_world)
    server_latest = SERVER / "logs" / "latest.log"
    if server_latest.exists():
        server_latest.unlink()
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
        "gamemode=spectator\n"
        "force-gamemode=true\n"
        "allow-flight=true\n"
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
    lines, reader_thread = drain_process(proc, log_path, ready=ready)
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

        rcon.send("time set day")
        rcon.send("weather clear")
        rcon.send("gamerule doDaylightCycle false")
        # Keep the test spawn centered. The camera is raised after joining
        # through spectator flight, because Minecraft's safe-spawn search can
        # relocate the local player to the terrain surface regardless of Y.
        rcon.send("setworldspawn 0 1200 0")
        rcon.send("save-all flush")
        rcon.send("stop")
        proc.wait(timeout=120)
    finally:
        if rcon is not None:
            rcon.close()
        stop_process(proc)
        reader_thread.join(timeout=10)
        (BUILD / "server.log").write_text("".join(lines), encoding="utf-8", errors="replace")
        if server_latest.exists():
            shutil.copy2(server_latest, BUILD / "server-latest.log")

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

    CLIENT_RUN.mkdir(parents=True, exist_ok=True)
    client_latest = CLIENT_RUN / "logs" / "latest.log"
    if client_latest.exists():
        client_latest.unlink()
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


def experimental_warning_visible() -> bool:
    """OCR the virtual display for Minecraft's experimental-world confirmation."""
    xwd = BUILD / ".experimental-warning-probe.xwd"
    png = BUILD / ".experimental-warning-probe.png"
    try:
        subprocess.run(
            ["xwd", "-root", "-silent", "-out", str(xwd)],
            check=True, capture_output=True, timeout=20,
        )
        subprocess.run(
            ["convert", str(xwd), str(png)],
            check=True, capture_output=True, timeout=30,
        )
        result = subprocess.run(
            ["tesseract", str(png), "stdout", "--psm", "6"],
            check=True, capture_output=True, text=True, timeout=20,
        )
        recognized = re.sub(r"[^a-z]+", " ", result.stdout.lower())
        return (
            "experimental settings" in recognized
            and "this world uses" in recognized
        )
    finally:
        xwd.unlink(missing_ok=True)
        png.unlink(missing_ok=True)


def dismiss_experimental_warning() -> bool:
    """Click Minecraft's explicit 'I know what I'm doing!' confirmation.

    The CI display is fixed at 1280x720 with GUI scale 2. The confirmation
    button is centered at (800, 430) in the captured display. OCR gates the
    click so we never send it blindly to an unrelated screen.
    """
    deadline = time.monotonic() + 120
    while time.monotonic() < deadline:
        if experimental_warning_visible():
            print("EXPERIMENTAL_SETTINGS_WARNING_DETECTED", flush=True)
            for attempt in range(1, 4):
                subprocess.run(
                    ["xdotool", "mousemove", "--sync", "800", "430", "click", "1"],
                    check=True, capture_output=True, timeout=10,
                )
                time.sleep(1.5)
                if not experimental_warning_visible():
                    print(
                        "EXPERIMENTAL_SETTINGS_WARNING_DISMISSED: "
                        "clicked 'I know what I'm doing!'",
                        flush=True,
                    )
                    return True
                print(
                    f"EXPERIMENTAL_SETTINGS_WARNING_STILL_VISIBLE: attempt={attempt}",
                    flush=True,
                )
            raise RuntimeError(
                "Minecraft experimental-world warning remained visible after "
                "three confirmation clicks"
            )
        time.sleep(2)
    print("EXPERIMENTAL_SETTINGS_WARNING_NOT_PRESENT", flush=True)
    return False


def validate_screenshot(path: pathlib.Path | None) -> None:
    if path is None or not path.is_file():
        raise RuntimeError("Minecraft screenshot was not captured")
    if path.stat().st_size < 5_000:
        raise RuntimeError(
            f"Screenshot is too small to prove a rendered game frame: {path.stat().st_size} bytes"
        )
    result = subprocess.run(
        ["identify", "-format", "%w %h %[fx:mean] %[fx:standard_deviation]", str(path)],
        check=True, capture_output=True, text=True, timeout=30,
    )
    fields = result.stdout.strip().split()
    if len(fields) != 4:
        raise RuntimeError("ImageMagick returned incomplete screenshot statistics: " + result.stdout)
    width, height = int(fields[0]), int(fields[1])
    mean, deviation = float(fields[2]), float(fields[3])
    print(
        f"SCREENSHOT_STATS: {path.name} {width}x{height} "
        f"mean={mean:.5f} stddev={deviation:.5f} bytes={path.stat().st_size}",
        flush=True,
    )
    if width < 1280 or height < 720 or mean < 0.005 or deviation < 0.005:
        raise RuntimeError(
            "Screenshot appears blank, incomplete, or below the expected display size: "
            f"{width}x{height}, mean={mean:.5f}, stddev={deviation:.5f}"
        )


def prepare_open_rendering_view(
        client_latest: pathlib.Path, client_stdout_lines: list[str]) -> None:
    """Use spectator flight and render-thread telemetry to verify the camera height."""
    deadline = time.monotonic() + 180
    spawn_y = None
    while time.monotonic() < deadline:
        if client_latest.exists():
            log_text = client_latest.read_text(encoding="utf-8", errors="replace")
            match = re.search(
                r"logged in with entity id \d+ at \([-+0-9.]+,\s*([-+0-9.]+),\s*[-+0-9.]+\)",
                log_text,
            )
            if "Loaded 2 advancements" in log_text and match:
                spawn_y = float(match.group(1))
                break
        time.sleep(1)
    else:
        raise RuntimeError("Singleplayer world did not finish joining or report its spawn position")

    camera_match = None
    for attempt in range(1, 4):
        # Increase spectator speed, then release as soon as render-thread
        # telemetry confirms the target altitude. Holding ascent for a fixed
        # duration used to overshoot 1100 by more than 500 blocks.
        subprocess.run(
            ["xdotool", "click", "--repeat", "24", "--delay", "20", "4"],
            check=True, timeout=15,
        )
        subprocess.run(["xdotool", "keydown", "Control_L"], check=True, timeout=10)
        subprocess.run(["xdotool", "keydown", "space"], check=True, timeout=10)
        try:
            ascent_deadline = time.monotonic() + 25
            while time.monotonic() < ascent_deadline:
                output = "".join(client_stdout_lines)
                camera_match = re.search(
                    r"CLIENT_CAMERA_VANTAGE_READY:\s*x=([-+0-9.]+)\s+y=([-+0-9.]+)\s+z=([-+0-9.]+)",
                    output,
                )
                if camera_match and float(camera_match.group(2)) >= 1100.0:
                    print(
                        f"CLIENT_CAMERA_TARGET_REACHED: y={float(camera_match.group(2)):.1f}; "
                        "releasing ascent controls",
                        flush=True,
                    )
                    break
                time.sleep(0.1)
        finally:
            subprocess.run(["xdotool", "keyup", "space"], check=False, timeout=10)
            subprocess.run(["xdotool", "keyup", "Control_L"], check=False, timeout=10)

        if camera_match and float(camera_match.group(2)) >= 1100.0:
            print(
                f"CLIENT_CAMERA_VERIFIED: spawn_y={spawn_y:.1f}, "
                f"camera_y={float(camera_match.group(2)):.1f}",
                flush=True,
            )
            break
        print(
            f"CLIENT_CAMERA_ASCENT: attempt={attempt}, "
            f"vantage_marker={'seen' if camera_match else 'not yet seen'}",
            flush=True,
        )

    if not camera_match or float(camera_match.group(2)) < 1100.0:
        raise RuntimeError(
            "Spectator flight did not reach the verified high-altitude camera; "
            "render-thread telemetry marker was not observed"
        )

    # The CI-only Java camera hook applies a deterministic downward pitch.
    time.sleep(2)
    subprocess.run(["xdotool", "key", "F1"], check=True, timeout=10)
    time.sleep(1)
    print(
        "CLIENT_CAMERA_VANTAGE_READY_FOR_CAPTURE: "
        f"y={float(camera_match.group(2)):.1f}, looking toward distant terrain",
        flush=True,
    )

def run_client(env: dict[str, str]) -> None:
    if not env.get("DISPLAY"):
        raise RuntimeError("A virtual X display is required for the client render smoke")
    CLIENT_RUN.mkdir(parents=True, exist_ok=True)
    client_latest = CLIENT_RUN / "logs" / "latest.log"
    if client_latest.exists():
        client_latest.unlink()
    log_path = BUILD / "client-stdout.log"
    draw = threading.Event()
    proc = subprocess.Popen(
        ["gradle", "--no-daemon", "--console=plain", "--stacktrace", "runIrisClipmapSmoke"],
        cwd=MODULE, env=env, stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        text=True, bufsize=1, start_new_session=True,
    )
    lines, reader_thread = drain_process(proc, log_path, draw=draw)
    try:
        deadline = time.monotonic() + 480
        warning_check_deadline = time.monotonic() + 120
        world_ready_deadline = time.monotonic() + 180
        warning_checked = False
        camera_prepared = False
        def visible_samples_ready() -> bool:
            output = "".join(lines)
            return any(
                int(m.group(1)) > 0 and 1000.0 <= float(m.group(2)) <= 1250.0
                for m in re.finditer(
                    r"FAR_SOLID_VISIBLE_SAMPLES=(\d+).*?cameraY=([-+0-9.]+)", output)
            )

        def full_visible_ready() -> bool:
            output = "".join(lines)
            return bool(re.search(r"nativeMeshesBuilt=.*\blevels=6\b", output)) and any(
                int(m.group(1)) > 0 and 1000.0 <= float(m.group(2)) <= 1250.0
                for m in re.finditer(
                    r"FAR_SOLID_VISIBLE_SAMPLES=(\d+).*?cameraY=([-+0-9.]+).*?\blevels=6\b",
                    output,
                )
            )

        while (not draw.is_set() or not camera_prepared or not full_visible_ready()) \
                and proc.poll() is None and time.monotonic() < deadline:
            if not warning_checked and time.monotonic() < warning_check_deadline:
                if experimental_warning_visible():
                    dismiss_experimental_warning()
                    warning_checked = True
                else:
                    time.sleep(2)
            elif not camera_prepared:
                if client_latest.exists() and "Loaded 2 advancements" in client_latest.read_text(
                        encoding="utf-8", errors="replace"):
                    prepare_open_rendering_view(client_latest, lines)
                    camera_prepared = True
                elif time.monotonic() >= world_ready_deadline:
                    raise RuntimeError("Singleplayer world did not become ready for camera positioning")
                else:
                    time.sleep(1)
            else:
                time.sleep(1)
        if not draw.is_set():
            try:
                diagnostic = capture_screenshot("iris-client-before-native-draw")
                print(
                    "CLIENT_DIAGNOSTIC_SCREENSHOT_CAPTURED:",
                    diagnostic,
                    flush=True,
                )
            except Exception as screenshot_error:
                print(
                    "CLIENT_DIAGNOSTIC_SCREENSHOT_FAILED:",
                    repr(screenshot_error),
                    flush=True,
                )
            raise RuntimeError(
                "Client did not submit an Iris-native clipmap draw within the test window; "
                f"Gradle exit code={proc.poll()}"
            )

        if not camera_prepared or not visible_samples_ready() or not full_visible_ready():
            capture_screenshot("iris-client-no-visible-clipmap-samples")
            raise RuntimeError("No verified depth-passing solid clipmap samples at the high-altitude camera")

        # The marker is emitted only after six-level native mesh submission.
        stdout_text = "".join(lines)
        if not re.search(r"nativeMeshesBuilt=.*\blevels=6\b", stdout_text):
            raise RuntimeError("Iris native meshes were not built for all six LOD levels")
        if "firstIrisNativeDraw=" not in stdout_text:
            raise RuntimeError("The Iris-native terrain draw marker is missing from client output")

        first = capture_screenshot("iris-clipmap-native")
        validate_screenshot(first)
        time.sleep(8)
        stable = capture_screenshot("iris-clipmap-native-after-stable-frames")
        validate_screenshot(stable)
        # Real horizontal spectator flight, not a synthetic scheduler unit test.
        # Require a fresh complete window during motion and another visible
        # window after release. The initial stationary result cannot satisfy it.
        motion_start = len(lines)
        old_centres = set(re.findall(r"CLIENT_LOD_ACTIVE levels=6 center=\(([^)]+)\)", "".join(lines)))
        subprocess.run(["xdotool", "keydown", "w"], check=True, timeout=10)
        try:
            time.sleep(30)
            moving_output = "".join(lines[motion_start:])
        finally:
            subprocess.run(["xdotool", "keyup", "w"], check=False, timeout=10)
        moving_centres = set(re.findall(r"CLIENT_LOD_ACTIVE levels=6 center=\(([^)]+)\)", moving_output)) - old_centres
        if not moving_centres:
            capture_screenshot("iris-clipmap-moving-no-window")
            raise RuntimeError("No new complete clipmap centre published during 30s continuous movement")
        move_deadline = time.monotonic() + 150
        while proc.poll() is None and time.monotonic() < move_deadline:
            output = "".join(lines[motion_start:])
            centres = set(re.findall(r"CLIENT_LOD_ACTIVE levels=6 center=\(([^)]+)\)", output)) - old_centres
            positive = any(int(m.group(1)) > 0 for m in re.finditer(
                r"FAR_SOLID_VISIBLE_SAMPLES=(\d+).*\blevels=6\b", output))
            # One fresh complete centre during a real 30-second flight is
            # sufficient to prove camera movement refreshes the clipmap.
            # Requiring two new centres here was impossible after key release:
            # the harness did not issue a second movement segment.
            if len(centres) >= 1 and positive:
                break
            time.sleep(1)
        else:
            capture_screenshot("iris-clipmap-moving-failed")
            raise RuntimeError("Continuous movement did not produce two fresh complete centres and visible samples")
        moving = capture_screenshot("iris-clipmap-after-motion")
        validate_screenshot(moving)
        print("IRIS_CLIPMAP_MOVEMENT_REFRESH_PASS", sorted(centres), flush=True)
        final_stdout = "".join(lines)
        if "SAMPLER STALLED" in final_stdout:
            raise RuntimeError(
                "The Geometry Clipmap sampler stalled during real client movement; "
                "inspect the last-density-sample and proxy-cancellation diagnostics"
            )
        if "GL_INVALID_OPERATION in glUniform(program not linked)" in final_stdout:
            raise RuntimeError(
                "Iris-native draw emitted GL_INVALID_OPERATION while uploading "
                "ChunkOffset; the shader program was not bound/linked"
            )
        print("IRIS_NATIVE_RENDER_SMOKE_PASS", flush=True)
    finally:
        stop_process(proc, timeout=35)
        reader_thread.join(timeout=10)
        (BUILD / "client-stdout.log").write_text(
            "".join(lines), encoding="utf-8", errors="replace")
        if client_latest.exists():
            shutil.copy2(client_latest, BUILD / "client-latest.log")
        crash_reports = CLIENT_RUN / "crash-reports"
        if crash_reports.exists():
            shutil.copytree(
                crash_reports, BUILD / "client-crash-reports", dirs_exist_ok=True
            )
        for fatal_log in CLIENT_RUN.glob("hs_err_pid*.log"):
            shutil.copy2(fatal_log, BUILD / fatal_log.name)
        if proc.poll() is None:
            stop_process(proc, timeout=10)


def main() -> int:
    BUILD.mkdir(parents=True, exist_ok=True)
    if not all(shutil.which(tool) for tool in ("xwd", "convert", "identify")):
        raise RuntimeError("xwd and ImageMagick convert/identify are required for screenshots")
    env = os.environ.copy()
    env["JAVA_TOOL_OPTIONS"] = (
        env.get("JAVA_TOOL_OPTIONS", "") +
        " -Drealscale.experimental.geometryclipmap=true"
        " -Drealscale.experimental.geometryclipmap.debug=true"
        " -Drealscale.experimental.geometryclipmap.smokeCamera=true"
    ).strip()
    # The temporary dedicated server must not see client-only Iris/Sodium mods.
    # Generate the world in the server run directory, then copy it into the
    # isolated ModDevGradle Iris client run directory before launching the client.
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
