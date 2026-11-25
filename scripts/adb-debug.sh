#!/usr/bin/env bash
# Set up wireless ADB debugging for Longbox development.
#
# Two workflows:
#   1) USB → wireless   Device is on USB. Script switches it to TCP mode, then
#                       you can unplug.
#   2) Already paired   Device is already in wireless debug mode (Developer Options
#                       → Wireless debugging → IP address and port).
#
# Requires: adb in PATH (Android SDK Platform Tools).
set -euo pipefail

have() { command -v "$1" &>/dev/null; }

if ! have adb; then
    echo "adb not found. Install Android SDK Platform Tools and add to PATH." >&2
    echo "  Windows: %LOCALAPPDATA%\\Android\\Sdk\\platform-tools" >&2
    exit 1
fi

echo "=== Longbox — wireless ADB ==="
echo
echo "1) USB → wireless  (device connected via USB; will switch to TCP)"
echo "2) Already paired  (device is already in Wireless debugging mode)"
echo
read -rp "Mode [1/2]: " mode

case "$mode" in
1)
    echo
    echo "Connected USB devices:"
    adb devices
    echo
    read -rp "TCP port for adb to listen on [5555]: " port
    port="${port:-5555}"
    adb tcpip "$port"
    echo "Switched to TCP mode on port ${port}. You can unplug USB now."
    echo
    read -rp "Device IP address: " ip
    ;;
2)
    echo
    read -rp "Device IP address: " ip
    read -rp "Port (from Wireless debugging screen) [5555]: " port
    port="${port:-5555}"
    ;;
*)
    echo "Unknown mode: $mode" >&2
    exit 1
    ;;
esac

echo
echo "Connecting to ${ip}:${port}…"
adb connect "${ip}:${port}"
echo
echo "Devices:"
adb devices
echo

read -rp "Start logcat for com.guturner.longbox? [y/N] " log
if [[ "${log,,}" == "y" ]]; then
    # Try to filter to just the app's process; falls back to tag filter if pidof fails.
    pid=$(adb shell pidof -s com.guturner.longbox 2>/dev/null || true)
    if [[ -n "$pid" ]]; then
        echo "Attaching to PID ${pid}…"
        adb logcat --pid="$pid" -v time
    else
        echo "App not running yet — showing AndroidRuntime crashes and app logs."
        adb logcat -v time AndroidRuntime:E LongboxApp:D "*:S"
    fi
fi
