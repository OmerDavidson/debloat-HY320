#!/usr/bin/env python3
"""Derive local summaries from collector output; never invokes ADB."""
import argparse
from collections import Counter
import csv
import hashlib
import json
from pathlib import Path
import re


def csv_cell(value):
    """Keep device-supplied metadata as text in spreadsheet imports.

    CSV quoting protects delimiters, not formulas. Preserve the original evidence
    and neutralize only the exported representation, including leading whitespace.
    """
    if isinstance(value, str):
        start = value.lstrip()
        if (start.startswith(("=", "+", "-", "@", "＝", "＋", "－", "＠"))
                or value.startswith(("\t", "\r", "\n"))):
            return "'" + value
    return value


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("baseline", type=Path)
    parser.add_argument("--logs", type=Path, help="Optional run containing logcat_extended.txt")
    args = parser.parse_args()
    base = args.baseline

    def read(name):
        return (base / (name + ".txt")).read_text(errors="replace")

    manifest = json.loads((base / "manifest.json").read_text())
    mismatches = []
    for query in manifest["commands"]:
        for suffix, key in [(".txt", "stdout_sha256"), (".stderr.txt", "stderr_sha256")]:
            path = base / (query["name"] + suffix)
            if hashlib.sha256(path.read_bytes()).hexdigest() != query[key]:
                mismatches.append(path.name)
    if mismatches:
        raise SystemExit("Evidence hash mismatch: " + ", ".join(mismatches))

    props = dict(re.findall(r"^\[([^\]]+)\]: \[([^\n]*)\]$", read("properties"), re.M))
    mem = {k: int(v) for k, v in re.findall(r"^(\w+):\s+(\d+) kB$", read("memory"), re.M)}
    endmem = {k: int(v) for k, v in re.findall(r"^(\w+):\s+(\d+) kB$", read("memory_end"), re.M)}
    pss_section = read("meminfo").split("Total PSS by process:\n", 1)[1].split("Total PSS by OOM adjustment:", 1)[0]
    pss = [{"process": name, "pss_kib": int(k.replace(",", ""))}
           for k, name in re.findall(r"^\s+([\d,]+)K: (.*?) \(pid", pss_section, re.M)]
    gfx = []
    for block in re.split(r"(?=\*\* Graphics info for pid)", read("graphics_frames")):
        header = re.match(r"\*\* Graphics info for pid \d+ \[(.*?)\]", block)
        frames = re.search(r"Total frames rendered: (\d+)", block)
        jank = re.search(r"Janky frames: (\d+) \(([\d.]+)%\)", block)
        if header and frames and jank:
            gfx.append({"process": header[1], "frames": int(frames[1]), "janky_frames": int(jank[1]), "jank_percent": float(jank[2])})

    logpath = args.logs / "logcat_extended.txt" if args.logs else base / "logcat_recent.txt"
    log = logpath.read_text(errors="replace")
    kills = re.findall(r"lowmemorykiller: Kill '([^']+)'", log)
    sysapps = set(re.findall(r"=([^=\s]+)$", read("packages_system"), re.M))
    userapps = set(re.findall(r"=([^=\s]+)$", read("packages_third_party"), re.M))
    disabled = set(re.findall(r"^package:(.+)$", read("packages_disabled"), re.M))
    blocks = {}
    for match in re.finditer(r"^  Package \[([^\]]+)\].*?(?=^  Package \[|\Z)", read("package_details"), re.M | re.S):
        blocks.setdefault(match[1], match[0])
    rows = []
    for line in read("packages").splitlines():
        # Updated/data apps have '=' characters inside their encoded directory names.
        # The package delimiter is the final '=' before the version field.
        match = re.match(r"package:(.*)=([^=\s]+) versionCode:(\d+)\s+installer=(\S+) uid:(\d+)", line)
        if not match:
            raise SystemExit("Unrecognized package inventory line: " + line)
        apk, package, version, installer, uid = match.groups()
        detail = blocks.get(package, "")
        versionname = re.search(r"^\s+versionName=(.*)$", detail, re.M)
        state = re.search(r"^\s+User 0: (.*)$", detail, re.M)
        rows.append({"package": package, "category": "system" if package in sysapps else "user" if package in userapps else "unknown",
                     "disabled": package in disabled, "apk": apk, "uid": uid,
                     "version_code": version, "version_name": versionname[1] if versionname else "",
                     "installer": installer, "user_0_state": state[1] if state else ""})
    if {row["package"] for row in rows} != sysapps | userapps:
        raise SystemExit("Inventory differs from system/user package lists; inspect evidence before summarizing")
    with (base / "applications.csv").open("w", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows({key: csv_cell(value) for key, value in row.items()} for row in rows)
    summary = {"baseline": str(base), "evidence_hashes_verified": True,
               "model": props.get("ro.product.model"), "firmware": props.get("ro.build.display.id"),
               "kernel": read("identity").splitlines()[1],
               "memory_start_kib": {k: mem[k] for k in ["MemTotal", "MemAvailable", "MemFree", "SwapTotal", "SwapFree"]},
               "memory_end_kib": {k: endmem[k] for k in ["MemAvailable", "MemFree", "SwapTotal", "SwapFree"]},
               "package_counts": {"total": len(rows), "system": len(sysapps), "user": len(userapps), "disabled": len(disabled)},
               "pss_by_process_kib": pss, "gfx_cumulative_first_summary_per_process": gfx,
               "low_memory_kills_in_retained_log": len(kills), "low_memory_kills_by_process": dict(Counter(kills)),
               "log_source": str(logpath), "log_sha256": hashlib.sha256(logpath.read_bytes()).hexdigest(),
               "query_exit_codes": {q["name"]: q["exit_code"] for q in manifest["commands"] if q["exit_code"] != 0}}
    (base / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps({k: summary[k] for k in ["evidence_hashes_verified", "package_counts", "low_memory_kills_in_retained_log", "query_exit_codes"]}, indent=2))


if __name__ == "__main__":
    main()
