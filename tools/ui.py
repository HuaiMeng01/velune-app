"""UI 自动化小工具：dump 一次，按文本/描述定位并点击。

用法：
  python tools/ui.py find 模型与密钥
  python tools/ui.py tap 模型与密钥
  python tools/ui.py tap-xy 540 2237
"""
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ADB = r"C:\Users\zou08\AppData\Local\Android\Sdk\platform-tools\adb.exe"
PKG = "com.veluneagent.app.debug"

# adb shell 会做路径转换，这里禁用
ENV = None


def run(args, capture=False):
    return subprocess.run(args, capture_output=capture, text=True)


def dump():
    run([ADB, "shell", "uiautomator", "dump", "/sdcard/ui.xml"])
    out = run([ADB, "exec-out", "cat", "/sdcard/ui.xml"], capture=True).stdout
    return ET.fromstring(out[out.index("<hierarchy"):])


def nodes(root):
    def walk(n):
        t = (n.get("text") or "").strip()
        d = (n.get("content-desc") or "").strip()
        b = n.get("bounds") or ""
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", b)
        if (t or d) and m:
            x1, y1, x2, y2 = map(int, m.groups())
            yield t or d, x1, y1, x2, y2
        for c in n:
            yield from walk(c)

    yield from walk(root)


def find(root, needle):
    hits = [n for n in nodes(root) if needle in n[0]]
    return hits


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return
    cmd = sys.argv[1]
    root = dump()

    if cmd == "find":
        for t, x1, y1, x2, y2 in find(root, sys.argv[2]):
            print(f"{t!r} center=({(x1+x2)//2},{(y1+y2)//2}) bounds=[{x1},{y1}][{x2},{y2}]")
        return

    if cmd == "tap":
        hits = find(root, sys.argv[2])
        if not hits:
            print(f"NOT FOUND: {sys.argv[2]}")
            sys.exit(2)
        _, x1, y1, x2, y2 = hits[0]
        run([ADB, "shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2)])
        print(f"tapped {sys.argv[2]!r} at ({(x1+x2)//2},{(y1+y2)//2})")
        return

    if cmd == "tap-xy":
        run([ADB, "shell", "input", "tap", sys.argv[2], sys.argv[3]])
        print(f"tapped ({sys.argv[2]},{sys.argv[3]})")
        return


if __name__ == "__main__":
    main()
