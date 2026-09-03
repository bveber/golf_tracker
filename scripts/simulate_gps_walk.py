#!/usr/bin/env python3
"""
GPS walk simulator for the Golf Tracker emulator.

Injects a sequence of GPS fixes into a running Android emulator via adb,
simulating a player walking a golf hole at a configurable speed.

Usage:
    python3 simulate_gps_walk.py scenarios/par4_standard.json
    python3 simulate_gps_walk.py scenarios/hole_transition.json --speed 1.5
    python3 simulate_gps_walk.py scenarios/par4_standard.json --list-shots
    python3 simulate_gps_walk.py scenarios/par4_standard.json --serial emulator-5554

The app polls GPS every 5 seconds; this script fires a fix on the same cadence
by default. Waypoints with a "pause_s" field insert a silent gap to simulate
the user setting up a shot or tapping the UI.
"""
import json
import subprocess
import time
import math
import argparse
import sys
from typing import Optional

GPS_INTERVAL_S = 5.0   # matches LocationRequest interval in GpsViewModel


def adb_geo_fix(lat: float, lon: float, serial: Optional[str] = None) -> None:
    """Send a GPS fix to the emulator. Longitude comes before latitude per adb convention."""
    cmd = ["adb"]
    if serial:
        cmd += ["-s", serial]
    else:
        cmd += ["-e"]  # target first available emulator
    cmd += ["emu", "geo", "fix", f"{lon:.7f}", f"{lat:.7f}"]
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        print(f"  [adb error] {result.stderr.strip()}", file=sys.stderr)


def haversine_m(a: tuple, b: tuple) -> float:
    """Great-circle distance between two (lat, lng) tuples in meters."""
    R = 6_371_000.0
    lat1, lat2 = math.radians(a[0]), math.radians(b[0])
    dlat = math.radians(b[0] - a[0])
    dlon = math.radians(b[1] - a[1])
    h = math.sin(dlat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2) ** 2
    return 2 * R * math.asin(math.sqrt(h))


def interpolate(a: tuple, b: tuple, steps: int):
    """Yield 'steps' evenly-spaced (lat, lng) points from a to b (exclusive of b)."""
    for i in range(steps):
        t = i / steps
        yield (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)


def run_scenario(path: str, speed_mph: float = 3.0, serial: Optional[str] = None) -> None:
    with open(path) as f:
        scenario = json.load(f)

    speed_mps = speed_mph * 0.44704
    waypoints = scenario["waypoints"]

    print(f"\nScenario : {scenario['name']}")
    if "notes" in scenario:
        print(f"Notes    : {scenario['notes']}")
    print(f"Par      : {scenario.get('par', '?')}")
    print(f"Speed    : {speed_mph} mph  ({speed_mps:.2f} m/s)")
    print(f"Waypoints: {len(waypoints)}")
    print()

    for i, wp in enumerate(waypoints):
        dest = (wp["lat"], wp["lng"])
        label = wp.get("label", f"waypoint_{i}")
        pause_s = wp.get("pause_s", 0)

        if i == 0:
            print(f"  ▶ {label:35s} ({dest[0]:.6f}, {dest[1]:.6f})  [start]")
            adb_geo_fix(dest[0], dest[1], serial)
            time.sleep(GPS_INTERVAL_S)
        else:
            src = (waypoints[i - 1]["lat"], waypoints[i - 1]["lng"])
            dist_m = haversine_m(src, dest)
            dist_y = dist_m * 1.09361
            walk_s = dist_m / speed_mps
            steps = max(1, int(walk_s / GPS_INTERVAL_S))

            print(f"  → {label:35s} ({dist_y:.0f} yds, ~{walk_s:.0f}s, {steps} pings)")

            for lat, lon in interpolate(src, dest, steps):
                adb_geo_fix(lat, lon, serial)
                time.sleep(GPS_INTERVAL_S)

            # Final fix exactly at the waypoint
            adb_geo_fix(dest[0], dest[1], serial)

        if pause_s > 0:
            print(f"     ⏸  pausing {pause_s}s for user interaction...")
            time.sleep(pause_s)

    print("\n✓ Scenario complete\n")


def list_waypoints(path: str) -> None:
    with open(path) as f:
        scenario = json.load(f)
    print(f"\n{scenario['name']}")
    for i, wp in enumerate(scenario["waypoints"]):
        print(f"  {i:2d}  {wp.get('label', ''):35s}  {wp['lat']:.6f}, {wp['lng']:.6f}"
              + (f"  (pause {wp['pause_s']}s)" if wp.get("pause_s") else ""))
    print()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("scenario", help="Path to scenario JSON file")
    parser.add_argument("--speed", type=float, default=3.0,
                        help="Walking speed in mph (default 3.0)")
    parser.add_argument("--serial", help="adb device serial (default: first emulator)")
    parser.add_argument("--list-shots", action="store_true",
                        help="Print waypoints without running")
    args = parser.parse_args()

    if args.list_shots:
        list_waypoints(args.scenario)
    else:
        run_scenario(args.scenario, args.speed, args.serial)


if __name__ == "__main__":
    main()
