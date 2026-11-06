#!/usr/bin/env python3
"""Read-only by default. A transient hardware preview requires an exact, explicitly approved token.

status: read device properties and the vendor's mode getter using a one-shot ADB shell process.
draft: read the native editor's pending draft; no hardware writes.
plan-preview: save a local plan for one tiny corner move, or --from-draft geometry.
preview: execute an approved plan, with an on-device 10-second restore; never persist calibration.
"""
import argparse
import base64
import datetime as dt
import hashlib
import json
from pathlib import Path
import re
import shlex
import subprocess
import time

FIRMWARE = "UI_Veng.projector.20241225.164550"
ROOT = Path(__file__).resolve().parent


def adb(serial, *args):
    result = subprocess.run(["adb", "-s", serial, *args], capture_output=True, timeout=30)
    if result.returncode:
        raise RuntimeError((result.stdout + result.stderr).decode(errors="replace"))
    return result.stdout.decode(errors="replace")


def command(serial, *args):
    # Every word is shell-quoted; neither package paths nor draft values become shell syntax.
    return adb(serial, "shell", shlex.join(args))


def apk_path(serial):
    paths = command(serial, "pm", "path", "com.vibehome.launcher").splitlines()
    if len(paths) != 1 or not paths[0].startswith("package:/data/app/"):
        raise RuntimeError("Expected one installed Vibe Home APK")
    return paths[0][8:]


def probe(serial):
    remote = "CLASSPATH=" + shlex.quote(apk_path(serial)) + " app_process /system/bin com.vibehome.launcher.HardwareProbe probe"
    output = adb(serial, "shell", remote)
    line = next((line for line in output.splitlines() if line.startswith("VIBE_RESULT ")), None)
    if line is None:
        raise RuntimeError("Read-only hardware probe failed: " + output[-2000:])
    return json.loads(line[len("VIBE_RESULT "):])


def draft(serial):
    output = command(serial, "content", "call", "--uri", "content://com.vibehome.launcher.projection", "--method", "read_draft")
    match = re.search(r"draft_base64=([A-Za-z0-9+/=]+)", output)
    if not match:
        raise RuntimeError("Draft read failed: " + output)
    return json.loads(base64.b64decode(match[1], validate=True))


def validate(values):
    if len(values) != 8 or any(type(v) is not int or v < 0 or v > 450 for v in values):
        raise ValueError("Eight integer insets, 0–450, are required")
    points = [(values[2], values[3]), (1000-values[4], values[5]),
              (1000-values[6], 1000-values[7]), (values[0], 1000-values[1])]
    for i in range(4):
        a, b, c = points[i], points[(i+1) % 4], points[(i+2) % 4]
        if (b[0]-a[0])*(c[1]-b[1]) - (b[1]-a[1])*(c[0]-b[0]) <= 0:
            raise ValueError("Crossing or inverted corners")
    return ",".join(map(str, values))


def approval_token(before, after):
    return hashlib.sha256(f"{FIRMWARE}\n{before}\n{after}\n10".encode()).hexdigest()


def check_status(status):
    props = status["properties"]
    if props.get("ro.product.model") != "HY320" or props.get("ro.hardware") != "sun50iw12p1" or props.get("ro.build.display.id") != FIRMWARE:
        raise RuntimeError("Exact verified device/firmware required")
    if props.get("persist.sys.tpryauto") != "0":
        raise RuntimeError("Automatic correction must already be off. This tool never disables it.")
    if props.get("vibe.config.path") != "/product/etc/config.ini" or props.get("vibe.config.autoCheckCamera") != "false" or props.get("vibe.config.autoFourCorner") != "false" or props.get("persist.sys.camok") == "1":
        raise RuntimeError("Unverified vendor camera/four-corner configuration; no preview allowed")
    if not status.get("display_service_present"):
        raise RuntimeError("Display service unavailable")
    return props


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["status", "draft", "plan-preview", "preview"])
    parser.add_argument("--serial", default="192.168.10.15:5555")
    parser.add_argument("--from-draft", action="store_true")
    parser.add_argument("--plan", type=Path, default=ROOT / "preview-plan.json")
    parser.add_argument("--approval", help="Exact token from the plan, only after explicit user approval")
    args = parser.parse_args()
    if args.action == "draft":
        print(json.dumps(draft(args.serial), indent=2)); return
    status = probe(args.serial)
    if args.action == "status":
        print(json.dumps(status, indent=2)); return
    props = check_status(status)
    before = validate([int(v) for v in props["persist.sys.zoom.value"].split(",")])
    if args.action == "plan-preview":
        desired = [int(v) for v in before.split(",")]
        if args.from_draft:
            pending = draft(args.serial)
            if pending.get("schema") != 1 or pending.get("kind") not in ["zoom", "keystone"]:
                raise RuntimeError("Choose a size/corner draft. Mode changes are not implemented.")
            if pending.get("baseline") != props:
                raise RuntimeError("Device state changed since this draft; refresh it before review")
            desired = pending["corners"]
        else:
            desired[0] += 1  # 0.1% of screen width at the bottom-left corner.
        after = validate(desired)
        if before == after:
            raise RuntimeError("No picture change in this plan")
        plan = {"schema": 1, "serial": args.serial, "firmware": FIRMWARE, "seconds": 10,
                "before": before, "after": after, "approval_token": approval_token(before, after),
                "properties": props, "created_utc": dt.datetime.now(dt.timezone.utc).isoformat(),
                "scope": "Temporary live geometry only; no persistent property/calibration writes"}
        args.plan.write_text(json.dumps(plan, indent=2) + "\n")
        print(json.dumps(plan, indent=2)); return
    plan = json.loads(args.plan.read_text())
    for key, expected in [("schema", 1), ("serial", args.serial), ("firmware", FIRMWARE), ("seconds", 10)]:
        if plan.get(key) != expected: raise RuntimeError("Plan mismatch: " + key)
    after = validate([int(v) for v in plan["after"].split(",")])
    token = approval_token(before, after)
    if plan.get("before") != before or plan.get("properties") != props:
        raise RuntimeError("Device state changed since review; create and approve a new plan")
    if args.approval != token or plan.get("approval_token") != token:
        raise RuntimeError("Explicit approval of this exact plan is required")
    # Detach the finite preview from the ADB connection so its timer survives disconnection.
    # This branch is NEVER used for status, draft, or plan creation.
    logfile = "/data/local/tmp/vibe-preview-" + token[:16] + ".log"
    invocation = shlex.join(["nohup", "app_process", "/system/bin", "com.vibehome.launcher.HardwareProbe",
                             "preview-geometry", token, before, after])
    remote = "CLASSPATH=" + shlex.quote(apk_path(args.serial)) + " " + invocation + " > " + shlex.quote(logfile) + " 2>&1 < /dev/null &"
    adb(args.serial, "shell", remote)
    print("Approved temporary preview started; on-device restore is scheduled for 10 seconds.", flush=True)
    time.sleep(12)
    log = command(args.serial, "cat", logfile)
    print(log)
    audit = {"plan": plan, "log": log, "finished_utc": dt.datetime.now(dt.timezone.utc).isoformat()}
    audit_file = args.plan.with_name(args.plan.stem + "-result.json")
    audit_file.write_text(json.dumps(audit, indent=2) + "\n")
    if "PREVIEW_RESTORED " + before not in log:
        raise RuntimeError("Preview restore was not verified. Inspect the saved log before any further operation.")


if __name__ == "__main__":
    main()
