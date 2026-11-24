"""Exercise the three Android surfaces on a fresh CI emulator."""
import re
import subprocess
import time
from pathlib import Path
import xml.etree.ElementTree as ET

PACKAGE = "jp.personal.yattalog.preview"

def adb(*args):
    return subprocess.check_output(["adb", *map(str, args)], text=True, encoding="utf-8")

def locate(value):
    for _ in range(8):
        adb("shell", "uiautomator", "dump", "/sdcard/yattalog-ui.xml")
        xml = adb("shell", "cat", "/sdcard/yattalog-ui.xml")
        for node in ET.fromstring(xml).iter("node"):
            if value in (node.get("text"), node.get("content-desc"), node.get("class")):
                x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.attrib["bounds"]))
                return ((x1 + x2) // 2, (y1 + y2) // 2)
        time.sleep(0.4)
    raise AssertionError(f"Missing {value}: {xml}")

def tap(value):
    adb("shell", "input", "tap", *locate(value))

def standby_center():
    # A non-focusable overlay leaves the underlying app as the active
    # accessibility window. Read the actual overlay bounds from WindowManager.
    for _ in range(20):
        windows = adb("shell", "dumpsys", "window", "windows")
        for block in re.split(r"(?m)^\s*Window #\d+ ", windows)[1:]:
            if PACKAGE not in block.splitlines()[0] or "APPLICATION_OVERLAY" not in block:
                continue
            match = re.search(r"\b(?:frame|mFrame)=\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", block)
            if match:
                left, top, right, bottom = map(int, match.groups())
                if right > left and bottom > top:
                    return ((left + right) // 2, (top + bottom) // 2)
        time.sleep(0.3)
    raise AssertionError("No standby window in WindowManager: " + windows)

apk, = Path("android/build/outputs/apk/debug").glob("*.apk")
adb("install", "-r", "-g", apk)
adb("shell", "appops", "set", PACKAGE, "SYSTEM_ALERT_WINDOW", "allow")
adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/jp.personal.yattalog.MainActivity")
tap("android.widget.EditText")
adb("shell", "input", "text", "draft")
adb("shell", "input", "keyevent", "KEYCODE_BACK")
tap("待機タイルへ")
adb("shell", "input", "tap", *standby_center())
locate("draft")
tap("待機タイルへ")
x, y = standby_center()
adb("shell", "input", "swipe", x, y, x, y, 900)
locate("draft")
locate("待機タイルへ")
print("Normal → tile → panel → tile → normal, draft retained")
