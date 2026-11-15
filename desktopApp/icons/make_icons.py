"""Builds the app icons from one source picture: square crop, smooth upscale, rounded corners."""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

source, project = Path(sys.argv[1]), Path(sys.argv[2])
icons = project / "desktopApp" / "icons"
resources = project / "desktopApp" / "src" / "main" / "resources"
icons.mkdir(parents=True, exist_ok=True)
resources.mkdir(parents=True, exist_ok=True)

im = Image.open(source).convert("RGB")
w, h = im.size
side = min(w, h)
left = (w - side) // 2
top = (h - side) // 2
square = im.crop((left, top, left + side, top + side))

MASTER = 1024
master = square.resize((MASTER, MASTER), Image.Resampling.LANCZOS).convert("RGBA")

# Rounded corners, drawn at 4x and scaled down so the edge is anti-aliased.
radius = int(MASTER * 0.18)
mask_big = Image.new("L", (MASTER * 4, MASTER * 4), 0)
ImageDraw.Draw(mask_big).rounded_rectangle((0, 0, MASTER * 4 - 1, MASTER * 4 - 1), radius=radius * 4, fill=255)
master.putalpha(mask_big.resize((MASTER, MASTER), Image.Resampling.LANCZOS))

master.save(icons / "app.png")  # Linux packaging
master.save(icons / "app.ico", sizes=[(s, s) for s in (16, 24, 32, 48, 64, 128, 256)])  # Windows
master.save(icons / "app.icns")  # macOS
master.resize((256, 256), Image.Resampling.LANCZOS).save(resources / "icon.png")  # window/taskbar at runtime

for name in ("app.png", "app.ico", "app.icns"):
    print(name, (icons / name).stat().st_size, "bytes")
print("icon.png", (resources / "icon.png").stat().st_size, "bytes; source", im.size, "-> crop", square.size)
