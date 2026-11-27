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
        rcon.send("gamerule doWeatherCycle false")
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


def _read_window_geometry(window_id: str):
    result = subprocess.run(
        ["xdotool", "getwindowgeometry", "--shell", window_id],
        check=False, capture_output=True, text=True, timeout=10,
    )
    if result.returncode != 0:
        return None
    values = {}
    for line in result.stdout.splitlines():
        if "=" in line:
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    try:
        return tuple(int(values[key]) for key in ("X", "Y", "WIDTH", "HEIGHT"))
    except (KeyError, ValueError):
        return None


def _visible_minecraft_windows():
    candidates = []
    seen = set()
    selectors = (
        ["--name", "Minecraft"],
        ["--class", "Minecraft"],
        ["--class", "net.minecraft.client.main.Main"],
    )
    for selector in selectors:
        search = subprocess.run(
            ["xdotool", "search", "--onlyvisible", *selector],
            check=False, capture_output=True, text=True, timeout=10,
        )
        if search.returncode != 0:
            continue
        for window_id in search.stdout.splitlines():
            if not window_id or window_id in seen:
                continue
            seen.add(window_id)
            geometry = _read_window_geometry(window_id)
            if geometry is not None and geometry[2] >= 640 and geometry[3] >= 360:
                candidates.append((geometry[2] * geometry[3], window_id, geometry))
    return sorted(candidates, reverse=True)


def active_game_window_geometry():
    # Xvfb may not assign an active window when no window manager is running.
    # Prefer a visible Minecraft window over the root window or a stale focus.
    candidates = _visible_minecraft_windows()
    if candidates:
        _, window_id, geometry = candidates[0]
        print("CLIENT_WINDOW_GEOMETRY_FALLBACK:", geometry, flush=True)
        return geometry

    active = subprocess.run(
        ["xdotool", "getactivewindow"],
        check=False, capture_output=True, text=True, timeout=10,
    )
    if active.returncode == 0 and active.stdout.strip():
        geometry = _read_window_geometry(active.stdout.strip())
        if geometry is not None and geometry[2] >= 640 and geometry[3] >= 360:
            print("CLIENT_WINDOW_GEOMETRY:", geometry, flush=True)
            return geometry

    print("CLIENT_WINDOW_GEOMETRY_UNAVAILABLE: no active or visible Minecraft window", flush=True)
    return None


def focus_minecraft_window():
    candidates = _visible_minecraft_windows()
    if not candidates:
        raise RuntimeError("Could not find a visible Minecraft window to receive input")
    _, window_id, geometry = candidates[0]
    subprocess.run(
        ["xdotool", "windowfocus", "--sync", window_id],
        check=True, timeout=10,
    )
    focused = subprocess.run(
        ["xdotool", "getwindowfocus"],
        check=False, capture_output=True, text=True, timeout=10,
    )
    if focused.returncode == 0 and focused.stdout.strip() != window_id:
        raise RuntimeError(
            "Xvfb focus did not move to Minecraft: "
            f"expected={window_id}, actual={focused.stdout.strip()}"
        )
    print("CLIENT_WINDOW_FOCUSED:", window_id, geometry, flush=True)
    return window_id


def ocr_game_window(name: str) -> str:
    image = capture_screenshot(name)
    if image is None:
        raise RuntimeError("Could not capture the Minecraft window for OCR")
    geometry = active_game_window_geometry()
    crop = BUILD / (name + "-game-window-upscaled.png")
    if geometry is None:
        # OCR the captured desktop rather than crashing before the world-ready
        # gate can determine whether Minecraft is still loading terrain.
        print("OCR_USING_FULL_DISPLAY_FALLBACK:", name, flush=True)
        subprocess.run(
            ["convert", str(image), "-resize", "200%", str(crop)],
            check=True, timeout=30,
        )
    else:
        x, y, width, height = geometry
        subprocess.run(
            ["convert", str(image), "-crop", f"{width}x{height}+{x}+{y}",
             "+repage", "-resize", "200%", str(crop)],
            check=True, timeout=30,
        )
    result = subprocess.run(
        ["tesseract", str(crop), "stdout", "--psm", "11"],
        check=True, capture_output=True, text=True, timeout=60,
    )
    return re.sub(r"[^a-z0-9]+", " ", result.stdout.lower()).strip()


def wait_for_pause_screen(lines: list[str], start: int, name: str,
                         expected_state: str, timeout: float = 12.0) -> str:
    """Wait for the real PauseScreen event after its three custom buttons exist."""
    deadline = time.monotonic() + timeout
    marker = "CLIENT_PAUSE_MENU_UI_INITIALIZED:"
    while time.monotonic() < deadline:
        for line in lines[start:]:
            if marker in line:
                if f"enabled={expected_state}" not in line:
                    raise RuntimeError(
                        "PauseScreen opened with an unexpected clipmap state: " + line
                    )
                # Let Minecraft render at least one complete frame after Init.Post
                # before capturing evidence of the visible pause menu.
                time.sleep(0.5)
                capture_screenshot(name)
                print("PAUSE_SCREEN_VERIFIED:", line.strip(), flush=True)
                return line
        time.sleep(0.25)
    raise RuntimeError(
        "Minecraft did not initialize the actual PauseScreen with the RealScale "
        "buttons; expected state=" + expected_state
    )


def click_pause_menu_toggle(click_x: int, click_y: int, *, position_pointer: bool = False) -> None:
    """Position the pointer once, then click without waiting for redundant motion."""
    if position_pointer:
        subprocess.run(
            ["xdotool", "mousemove", "--sync", str(click_x), str(click_y)],
            check=True, timeout=10,
        )
    subprocess.run(["xdotool", "click", "1"], check=True, timeout=5)


def wait_for_pause_menu_toggle(lines: list[str], start: int, expected_state: str,
                               timeout: float = 12.0) -> None:
    """Verify the real toggle callback updated its button label."""
    deadline = time.monotonic() + timeout
    marker = f"CLIENT_PAUSE_MENU_TOGGLE_UPDATED: enabled={expected_state}"
    while time.monotonic() < deadline:
        if marker in "".join(lines[start:]):
            print("PAUSE_MENU_BUTTON_UPDATED:", expected_state, flush=True)
            return
        time.sleep(0.25)
    raise RuntimeError("Pause-menu button callback did not update to " + expected_state)


def wait_for_toggle_status(lines, marker: str, start: int, timeout: float = 12.0) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if marker in "".join(lines[start:]):
            print("CLIPMAP_TOGGLE_STATUS_CONFIRMED:", marker, flush=True)
            return
        time.sleep(0.25)
    raise RuntimeError("Client did not report the pause-menu toggle: " + marker)


def ocr_loading_overlay(name: str) -> str:
    """OCR only Minecraft's centered Loading terrain label, not the whole scene."""
    image = capture_screenshot(name)
    if image is None:
        raise RuntimeError("Could not capture Minecraft while checking its loading overlay")
    geometry = active_game_window_geometry()
    if geometry is None:
        dimensions = subprocess.run(
            ["identify", "-format", "%w %h", str(image)],
            check=True, capture_output=True, text=True, timeout=10,
        ).stdout.split()
        window_x = window_y = 0
        window_width, window_height = (int(dimensions[0]), int(dimensions[1]))
        print("LOADING_OCR_USING_FULL_DISPLAY_FALLBACK", flush=True)
    else:
        window_x, window_y, window_width, window_height = geometry

    # Minecraft draws "Loading terrain..." in a narrow band around 30% of
    # the game viewport height. A tight crop avoids unrelated blurred scenery.
    crop_x = window_x + int(window_width * 0.30)
    crop_y = window_y + int(window_height * 0.22)
    crop_width = max(1, int(window_width * 0.40))
    crop_height = max(1, int(window_height * 0.18))
    crop = BUILD / (name + "-loading-label.png")
    subprocess.run(
        ["convert", str(image), "-crop",
         f"{crop_width}x{crop_height}+{crop_x}+{crop_y}", "+repage",
         "-colorspace", "Gray", "-contrast-stretch", "1%x1%",
         "-resize", "300%", str(crop)],
        check=True, timeout=30,
    )
    result = subprocess.run(
        ["tesseract", str(crop), "stdout", "--psm", "7"],
        check=True, capture_output=True, text=True, timeout=30,
    )
    return re.sub(r"[^a-z0-9]+", " ", result.stdout.lower()).strip()


def wait_until_world_rendered(timeout: float = 180.0) -> None:
    """Wait until the actual Loading terrain overlay disappears for 3 frames."""
    deadline = time.monotonic() + timeout
    consecutive_clear = 0
    latest = ""
    while time.monotonic() < deadline:
        latest = ocr_loading_overlay("iris-client-loading-gate")
        if "loading terrain" in latest:
            consecutive_clear = 0
            print("CLIENT_LOADING_OVERLAY_VISIBLE", flush=True)
        else:
            consecutive_clear += 1
            print(
                "CLIENT_LOADING_OVERLAY_ABSENT:",
                consecutive_clear, "/ 3",
                "ocr=", latest,
                flush=True,
            )
            if consecutive_clear >= 3:
                capture_screenshot("iris-client-world-ready-probe")
                print("CLIENT_WORLD_RENDER_READY_CONFIRMED", flush=True)
                return
        time.sleep(1.0)
    raise RuntimeError(
        "Minecraft's Loading terrain overlay did not disappear for 3 consecutive "
        "samples; pause-menu interaction was not attempted. Last OCR: " + latest
    )


def test_pause_menu_geometry_clipmap_toggle(lines) -> None:
    """Verify default OFF, then exercise ON -> OFF -> ON through the pause-menu button."""
    # Derive the click point from the actual game window. The CI options set
    # GUI scale 2; the custom pause-menu row contains Studio, toggle and range.
    window_geometry = active_game_window_geometry()
    if window_geometry is None:
        raise RuntimeError("Could not locate the visible Minecraft window for pause-menu clicking")
    window_x, window_y, window_width, window_height = window_geometry
    gui_scale = 2
    button_width = 154
    gap = 4
    logical_width = window_width // gui_scale
    logical_height = window_height // gui_scale
    row_width = button_width * 3 + gap * 2
    button_x = (logical_width - row_width) // 2 + button_width + gap
    button_y = max(4, logical_height - 28)
    click_x = window_x + (button_x + button_width // 2) * gui_scale
    click_y = window_y + (button_y + 10) * gui_scale
    print("CLIPMAP_TOGGLE_CLICK_POINT:", (click_x, click_y), flush=True)
    focus_minecraft_window()
    before_pause = len(lines)
    subprocess.run(["xdotool", "key", "Escape"], check=True, timeout=10)
    initial = wait_for_pause_screen(
        lines, before_pause, "iris-clipmap-pause-menu-default-off", "OFF"
    )

    # Move the pointer once. Repeating "mousemove --sync" at the same location
    # can wait forever because XTest emits no new motion event for identical
    # coordinates; subsequent state changes only need an ordinary click.
    before_on = len(lines)
    click_pause_menu_toggle(click_x, click_y, position_pointer=True)
    wait_for_toggle_status(lines, "[RealScale] Clipmap: ON", before_on)
    wait_for_pause_menu_toggle(lines, before_on, "ON")

    before_off = len(lines)
    click_pause_menu_toggle(click_x, click_y)
    wait_for_toggle_status(lines, "[RealScale] Clipmap: OFF", before_off)
    wait_for_pause_menu_toggle(lines, before_off, "OFF")

    before_on_again = len(lines)
    click_pause_menu_toggle(click_x, click_y)
    wait_for_toggle_status(lines, "[RealScale] Clipmap: ON", before_on_again)
    wait_for_pause_menu_toggle(lines, before_on_again, "ON")

    subprocess.run(["xdotool", "key", "Escape"], check=True, timeout=10)
    time.sleep(1)
    print("CLIPMAP_PAUSE_MENU_TOGGLE_PASS", flush=True)


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


def terrain_contrast(path: pathlib.Path) -> float:
    # Fixed 854x480 centered client viewport in the 1280x720 CI display.
    # Exclude desktop margins and upper sky; neither proves visible terrain.
    result = subprocess.run(
        ["convert", str(path), "-gravity", "center", "-crop", "854x480+0+0", "+repage",
         "-gravity", "south", "-crop", "854x240+0+0", "+repage", "-colorspace", "Gray",
         "-format", "%[fx:standard_deviation]", "info:"],
        check=True, capture_output=True, text=True, timeout=30,
    )
    contrast = float(result.stdout.strip())
    print(f"TERRAIN_VIEWPORT_CONTRAST: {path.name} {contrast:.5f}", flush=True)
    return contrast


def terrain_high_frequency_metrics(path: pathlib.Path) -> tuple[float, float]:
    # Compare the terrain viewport with a 2px low-pass copy. Record absolute
    # high-pass energy as well as its contrast-normalized ratio: the ratio
    # alone can falsely improve when overall terrain contrast increases.
    args = [
        "convert", str(path), "-gravity", "center", "-crop", "854x480+0+0", "+repage",
        "-gravity", "south", "-crop", "854x240+0+0", "+repage", "-colorspace", "Gray",
        "(", "+clone", "-blur", "0x2", ")", "-compose", "difference", "-composite",
        "-format", "%[fx:standard_deviation]", "info:",
    ]
    high = float(subprocess.run(
        args, check=True, capture_output=True, text=True, timeout=30
    ).stdout.strip())
    contrast = terrain_contrast(path)
    ratio = high / max(contrast, 1.0e-6)
    print(f"TERRAIN_HIGH_FREQUENCY_RMS: {path.name} {high:.5f}", flush=True)
    print(f"TERRAIN_HIGH_FREQUENCY_RATIO: {path.name} {ratio:.5f}", flush=True)
    return ratio, high


def latest_camera_position(output: str) -> tuple[float, float, float] | None:
    matches = re.findall(
        r"CLIENT_CAMERA_POSITION:\s*x=([-+0-9.]+)\s+y=([-+0-9.]+)\s+z=([-+0-9.]+)",
        output,
    )
    if not matches:
        return None
    return tuple(float(value) for value in matches[-1])


def move_camera_until_horizontal_distance(
        proc, lines, start: int, baseline: tuple[float, float, float],
        target_distance: float = 320.0, timeout_seconds: float = 90.0) -> float:
    """Strafe at a fixed camera altitude until a new snapped clipmap centre is unavoidable."""
    started = time.monotonic()
    deadline = started + timeout_seconds
    reached_distance = 0.0
    subprocess.run(["xdotool", "keydown", "Control_L"], check=True, timeout=10)
    subprocess.run(["xdotool", "keydown", "d"], check=True, timeout=10)
    try:
        while proc.poll() is None and time.monotonic() < deadline:
            current = latest_camera_position("".join(lines[start:]))
            if current is not None:
                dx = current[0] - baseline[0]
                dz = current[2] - baseline[2]
                reached_distance = (dx * dx + dz * dz) ** 0.5
                if reached_distance >= target_distance:
                    break
            time.sleep(0.5)
    finally:
        subprocess.run(["xdotool", "keyup", "d"], check=False, timeout=10)
        subprocess.run(["xdotool", "keyup", "Control_L"], check=False, timeout=10)

    if reached_distance < target_distance:
        capture_screenshot("iris-clipmap-camera-movement-distance-timeout")
        raise RuntimeError(
            "Smoke camera did not complete the required horizontal movement: "
            f"{reached_distance:.1f}/{target_distance:.1f} blocks"
        )
    print(
        f"CLIENT_CAMERA_MOVEMENT_VERIFIED: horizontal={reached_distance:.1f} "
        f"target={target_distance:.1f} elapsed={time.monotonic()-started:.1f}s",
        flush=True,
    )
    return reached_distance


def wait_for_new_complete_center(proc, lines, start: int,
                                 previous_centres: set[str],
                                 timeout_seconds: float = 240.0) -> str:
    """Wait for the server/client to publish a six-level window at a new snapped centre."""
    deadline = time.monotonic() + timeout_seconds
    last_centres: list[str] = []
    while proc.poll() is None and time.monotonic() < deadline:
        output = "".join(lines[start:])
        active = re.findall(
            r"CLIENT_LOD_ACTIVE levels=6 center=\(([^)]+)\)", output
        )
        last_centres = [centre for centre in active if centre not in previous_centres]
        if last_centres:
            centre = last_centres[-1]
            print("CLIENT_MOVEMENT_NEW_SIX_LOD_CENTER:", centre, flush=True)
            return centre
        time.sleep(1)
    capture_screenshot("iris-clipmap-new-centre-timeout")
    raise RuntimeError(
        "Camera moved far enough to cross a clipmap snap boundary, but no new "
        "complete six-LOD centre was published within "
        f"{timeout_seconds:.0f}s: {last_centres!r}"
    )


def wait_for_settled_window(proc, lines, start: int, minimum_seconds: float = 30.0) -> str:
    """Wait for the latest complete height and proxy snapshot and stable draw evidence."""
    started = time.monotonic()
    deadline = started + 240
    stable_since = None
    last_state = None
    while proc.poll() is None and time.monotonic() < deadline:
        output = "".join(lines[start:])
        centres = re.findall(r"CLIENT_LOD_ACTIVE levels=6 center=\(([^)]+)\)", output)
        if centres:
            centre = centres[-1]
            escaped = re.escape(centre)
            proxy_counts = re.findall(r"CLIENT_PROXY_RECEIVED center=\(" + escaped + r"\) count=(\d+)", output)
            mesh = re.findall(r"nativeMeshesBuilt=.*?levels=6.*?center=\(" + escaped + r"\)", output)
            samples = re.findall(r"FAR_(?:SOLID|WATER)_VISIBLE_SAMPLES=(\d+).*?levels=6 center=\(" + escaped + r"\)", output)
            proxy_ready = bool(proxy_counts) and (int(proxy_counts[-1]) == 0 or bool(re.search(
                r"nativeProxyMeshesBuilt=.*?proxies=" + proxy_counts[-1] + r" center=\(" + escaped + r"\)", output)))
            state = (centre, len(mesh), len(proxy_counts), proxy_counts[-1] if proxy_counts else None)
            ready = bool(mesh) and any(int(value) > 0 for value in samples) and proxy_ready
            if state != last_state or not ready:
                stable_since = time.monotonic() if ready else None
                last_state = state
            if (ready and stable_since is not None and time.monotonic() - stable_since >= 15
                    and time.monotonic() - started >= minimum_seconds):
                print(f"CLIENT_RENDER_SETTLED center=({centre}) proxies={proxy_counts[-1]} "
                      f"elapsed={time.monotonic()-started:.1f}s", flush=True)
                return centre
        time.sleep(1)
    capture_screenshot("iris-client-settle-timeout")
    raise RuntimeError("Latest complete height/proxy window did not settle within 240s: " + repr(last_state))


def terrain_color_count(path: pathlib.Path) -> int:
    result = subprocess.run(
        ["convert", str(path), "-gravity", "center", "-crop", "854x480+0+0", "+repage",
         "-gravity", "south", "-crop", "854x240+0+0", "+repage", "-colors", "64",
         "-format", "%k", "info:"],
        check=True, capture_output=True, text=True, timeout=30,
    )
    count = int(result.stdout.strip())
    print(f"TERRAIN_VIEWPORT_COLOR_COUNT: {path.name} {count}", flush=True)
    return count


def wait_for_complete_surface_assets(proc, lines, start: int,
                                     minimum_seconds: float = 60.0) -> str:
    """Wait for six height LODs and their matching proxy mesh before eye-level capture."""
    started = time.monotonic()
    deadline = started + 240
    stable_since = None
    last_state = None
    while proc.poll() is None and time.monotonic() < deadline:
        output = "".join(lines[start:])
        active = re.findall(r"CLIENT_LOD_ACTIVE levels=6 center=\(([^)]+)\)", output)
        meshes = re.findall(r"nativeMeshesBuilt=.*?\blevels=6\b.*?center=\(([^)]+)\)", output)
        if active and meshes and active[-1] == meshes[-1]:
            centre = active[-1]
            escaped = re.escape(centre)
            proxy_counts = re.findall(
                r"CLIENT_PROXY_RECEIVED center=\(" + escaped + r"\) count=(\d+)", output)
            proxy_ready = True
            count = 0
            if proxy_counts:
                count = int(proxy_counts[-1])
                proxy_ready = count == 0 or bool(re.search(
                    r"nativeProxyMeshesBuilt=.*?proxies=" + str(count)
                    + r" center=\(" + escaped + r"\)", output))
            state = (centre, len(meshes), count)
            if state != last_state or not proxy_ready:
                stable_since = time.monotonic() if proxy_ready else None
                last_state = state
            if (proxy_ready and stable_since is not None
                    and time.monotonic() - stable_since >= 15
                    and time.monotonic() - started >= minimum_seconds):
                print(f"NATURAL_VIEW_ASSETS_SETTLED center=({centre}) proxies={count} "
                      f"elapsed={time.monotonic()-started:.1f}s", flush=True)
                return centre
        time.sleep(1)
    capture_screenshot("iris-clipmap-natural-view-settle-timeout")
    raise RuntimeError("Natural eye-level view did not receive a complete six-LOD/proxy snapshot "
                       "within 240 seconds: " + repr(last_state))


def capture_natural_surface_view(proc, lines) -> None:
    # Ignore snapshots from before the OFF -> ON test; the toggle clears them.
    start = len(lines)
    wait_for_complete_surface_assets(proc, lines, start, minimum_seconds=60)
    subprocess.run(["xdotool", "key", "F1"], check=True, timeout=10)
    time.sleep(2)
    image = capture_screenshot("iris-clipmap-natural-eye-level")
    validate_screenshot(image)
    contrast = terrain_contrast(image)
    colors = terrain_color_count(image)
    if contrast < 0.06 or colors < 16:
        raise RuntimeError("Natural eye-level terrain lacks visible surface detail: "
                           f"contrast={contrast:.5f}, colors={colors}")


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
        # duration used to overshoot the intended high-altitude view by more than 500 blocks.
        subprocess.run(
            ["xdotool", "click", "--repeat", "24", "--delay", "20", "4"],
            check=True, timeout=15,
        )
        subprocess.run(["xdotool", "keydown", "Control_L"], check=True, timeout=10)
        subprocess.run(["xdotool", "keydown", "space"], check=True, timeout=10)
        try:
            ascent_deadline = time.monotonic() + 120
            while time.monotonic() < ascent_deadline:
                output = "".join(client_stdout_lines)
                camera_match = re.search(
                    r"CLIENT_CAMERA_VANTAGE_READY:\s*x=([-+0-9.]+)\s+y=([-+0-9.]+)\s+z=([-+0-9.]+)\s+pitch=([-+0-9.]+)\s+yaw=([-+0-9.]+)",
                    output,
                )
                if camera_match and float(camera_match.group(2)) >= 2300.0:
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

        if camera_match and float(camera_match.group(2)) >= 2300.0:
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

    if not camera_match or float(camera_match.group(2)) < 2300.0:
        raise RuntimeError(
            "Spectator flight did not reach the verified high-altitude camera; "
            "render-thread telemetry marker was not observed"
        )

    camera_x, camera_y, camera_z, camera_pitch, camera_yaw = (
        float(camera_match.group(index)) for index in range(1, 6)
    )
    expected_pose = (0.5, 2304.9, 1.5, 35.0, 45.0)
    actual_pose = (camera_x, camera_y, camera_z, camera_pitch, camera_yaw)
    if any(abs(actual - expected) > 0.11
           for actual, expected in zip(actual_pose, expected_pose)):
        raise RuntimeError(
            "CI smoke camera did not reach its deterministic world-space pose: "
            f"actual={actual_pose!r}, expected={expected_pose!r}"
        )
    print(
        "CLIENT_CAMERA_POSE_VERIFIED: "
        f"x={camera_x:.1f} y={camera_y:.1f} z={camera_z:.1f} "
        f"pitch={camera_pitch:.1f} yaw={camera_yaw:.1f}",
        flush=True,
    )
    time.sleep(3)
    print(
        "CLIENT_CAMERA_VANTAGE_READY_FOR_CAPTURE: "
        f"y={camera_y:.1f}, looking toward distant terrain",
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
                int(m.group(1)) > 0 and 2200.0 <= float(m.group(2)) <= 2500.0
                for m in re.finditer(
                    r"FAR_(?:SOLID|WATER)_VISIBLE_SAMPLES=(\d+).*?cameraY=([-+0-9.]+)", output)
            )

        def full_visible_ready() -> bool:
            output = "".join(lines)
            far_ready = "IRIS_CAMERA_FAR_EXTENDED" in output
            fog_ready = "IRIS_FOG_RANGE original=" in output
            return (
                bool(re.search(r"nativeMeshesBuilt=.*\blevels=6\b", output))
                and far_ready and fog_ready
                and any(
                    int(m.group(1)) > 0 and 2200.0 <= float(m.group(2)) <= 2500.0
                    for m in re.finditer(
                        r"FAR_(?:SOLID|WATER)_VISIBLE_SAMPLES=(\d+).*?cameraY=([-+0-9.]+).*?\blevels=6\b",
                        output,
                    )
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
                    wait_until_world_rendered()
                    test_pause_menu_geometry_clipmap_toggle(lines)
                    prepare_open_rendering_view(client_latest, lines)
                    capture_natural_surface_view(proc, lines)
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
            raise RuntimeError("No verified depth-passing far solid or water surface samples at the high-altitude camera")

        # The marker is emitted only after six-level native mesh submission.
        stdout_text = "".join(lines)
        if not re.search(r"nativeMeshesBuilt=.*\blevels=6\b", stdout_text):
            raise RuntimeError("Iris native meshes were not built for all six LOD levels")
        if "firstIrisNativeDraw=" not in stdout_text:
            raise RuntimeError("The Iris-native terrain draw marker is missing from client output")

        first = capture_screenshot("iris-clipmap-native")
        validate_screenshot(first)
        wait_for_settled_window(proc, lines, 0, minimum_seconds=30)
        stable = capture_screenshot("iris-clipmap-native-after-stable-frames")
        validate_screenshot(stable)
        initial_contrast = terrain_contrast(stable)
        initial_high_frequency, initial_high_frequency_rms = terrain_high_frequency_metrics(stable)
        # Move far enough to cross a known clipmap snap boundary, while
        # preserving altitude so the far-field depth probe remains active.
        # Then wait for the replacement six-LOD window instead of requiring
        # the server's expensive density sampler to finish during the key hold.
        motion_start = len(lines)
        old_centres = set(re.findall(
            r"CLIENT_LOD_ACTIVE levels=6 center=\(([^)]+)\)", "".join(lines)
        ))
        baseline_position = latest_camera_position("".join(lines))
        if baseline_position is None:
            raise RuntimeError("No baseline CLIENT_CAMERA_POSITION telemetry before movement")
        move_camera_until_horizontal_distance(
            proc, lines, motion_start, baseline_position, target_distance=320.0
        )
        if "CLIENT_CAMERA_PIN_RELEASED_FOR_MOVEMENT" not in "".join(lines[motion_start:]):
            raise RuntimeError("CI smoke camera remained pinned when horizontal movement began")
        new_centre = wait_for_new_complete_center(
            proc, lines, motion_start, old_centres, timeout_seconds=240.0
        )
        moving_output = "".join(lines[motion_start:])
        centres = set(re.findall(
            r"CLIENT_LOD_ACTIVE levels=6 center=\(([^)]+)\)", moving_output
        )) - old_centres
        moving_centres = {new_centre}
        if new_centre not in centres:
            raise RuntimeError("New-centre marker was not retained in movement telemetry")
        early = capture_screenshot("iris-clipmap-after-motion-early")
        validate_screenshot(early)
        terrain_contrast(early)
        settled_centre = wait_for_settled_window(proc, lines, motion_start, minimum_seconds=90)
        # A fast spectator flight can cross more than one snapped centre before
        # the first replacement window finishes. Accept the final stable centre,
        # provided it is new and has its own complete mesh, proxy and visible-pixel
        # evidence (all required by wait_for_settled_window).
        settled_output = "".join(lines[motion_start:])
        settled_centres = set(re.findall(
            r"CLIENT_LOD_ACTIVE levels=6 center=\(([^)]+)\)", settled_output
        )) - old_centres
        if settled_centre in old_centres or settled_centre not in settled_centres:
            raise RuntimeError(
                f"Settled centre {settled_centre} is not a newly published movement centre: "
                f"{sorted(settled_centres)}"
            )
        centres = settled_centres
        moving_centres = settled_centres
        print("CLIENT_MOVEMENT_SETTLED_CENTER:", settled_centre, flush=True)
        moving = capture_screenshot("iris-clipmap-after-motion")
        validate_screenshot(moving)
        settled_contrast = terrain_contrast(moving)
        settled_high_frequency, settled_high_frequency_rms = terrain_high_frequency_metrics(moving)
        time.sleep(30)
        final_frame = capture_screenshot("iris-clipmap-after-motion-final")
        validate_screenshot(final_frame)
        final_contrast = terrain_contrast(final_frame)
        final_high_frequency, final_high_frequency_rms = terrain_high_frequency_metrics(final_frame)
        print("IRIS_CLIPMAP_MOVEMENT_REFRESH_PASS", sorted(centres), settled_centre, flush=True)
        # The fixed initial camera is the controlled A/B location. Its absolute
        # high-pass RMS must remain below the pre-macro baseline with a small
        # tolerance; this prevents a high-contrast or missing-surface image from
        # passing merely because the normalized ratio got smaller.
        if initial_high_frequency_rms > 0.030:
            raise RuntimeError(
                "Fixed-camera terrain has excessive absolute high-frequency energy: "
                f"rms={initial_high_frequency_rms:.5f}, limit=0.03000"
            )
        print("IRIS_TERRAIN_ABSOLUTE_TEXTURE_ENERGY_GATE_PASS", flush=True)
        # Keep the normalized movement gate as a second check across the refreshed window.
        if max(settled_high_frequency, final_high_frequency) > 0.18:
            raise RuntimeError("Distant terrain still has excessive high-frequency texture aliasing: "
                               f"settled={settled_high_frequency:.5f}, final={final_high_frequency:.5f}; "
                               "the compact neutral UV patch must reduce repeated PBR detail")
        print("IRIS_TERRAIN_TEXTURE_ALIASING_GATE_PASS", flush=True)
        if initial_contrast < 0.055:
            raise RuntimeError(f"Initial terrain viewport has insufficient contrast: {initial_contrast:.5f}")
        if max(settled_contrast, final_contrast) < max(0.055, initial_contrast * 0.4):
            raise RuntimeError("Persistent post-motion terrain contrast collapse after complete proxy/LOD settlement: "
                               f"initial={initial_contrast:.5f}, settled={settled_contrast:.5f}, final={final_contrast:.5f}")
        print("IRIS_TERRAIN_CONTRAST_GATE_PASS", flush=True)
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
