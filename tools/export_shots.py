"""Screenshots (PNG) as JPEGs with the long side at 1600 px, for the planning document and the README.

    python tools/export_shots.py docs/m4/shots/png docs/m4/shots
"""
import sys
from pathlib import Path

from PIL import Image

src, dst = Path(sys.argv[1]), Path(sys.argv[2])
dst.mkdir(parents=True, exist_ok=True)
for p in sorted(src.glob("*.png")):
    im = Image.open(p).convert("RGB")
    scale = 1600 / max(im.size)
    if scale < 1:
        im = im.resize((round(im.width * scale), round(im.height * scale)), Image.LANCZOS)
    out = dst / (p.stem + ".jpg")
    im.save(out, quality=88, optimize=True)
    print(f"  {out.name} {im.width}x{im.height} {out.stat().st_size // 1024} KB")
