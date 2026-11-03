"""Turns the engine turntable renders and the part photos into the app's WebP assets.

usage: python -I tools/assets_build.py RENDERS_DIR PARTS_DIR ASSETS_DIR
       python -I tools/assets_build.py --selftest

The render tool leaves <set>_hero.png, <set>_turn_00..23.png and <set>_anchors.json for every set. Each set becomes
ASSETS_DIR/engine3d/<variant>/ (i4; v8 from v8plain; v8sc from v8, which has the supercharger; v12; v12t with the twin
turbos) with hero.webp at 640 px, turn_KK.webp at 512 px and anchors.json as it is. The part photos PARTS_DIR/<id>.png
become ASSETS_DIR/parts/<id>.webp at 512 px, and PARTS_DIR/출처.md is copied to docs/m2/parts_source.md so the
images keep their provenance. The PNG originals stay where they are; only the WebP copies go into git.
"""
import argparse
import io
import os
import re
import shutil
import sys

from PIL import Image

FRAMES = 24
VARIANTS = ["i4", "v8", "v8sc", "v12", "v12t"]
PARTS = ["na", "t1", "t2", "t3", "sc", "stock", "sport", "track", "at", "dct", "mt"]
SETS = {"i4": "i4", "v8plain": "v8", "v8": "v8sc", "v12": "v12", "v12t": "v12t"}
RENDER_RE = re.compile(r"^(i4|v8plain|v8|v12t|v12)_(hero\.png|turn_(\d\d)\.png|anchors\.json)$")
TURN_PX = 512
HERO_PX = 640
PART_PX = 512
QUALITY = 80
SOURCE_DOC = "출처.md"
REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE_COPY = os.path.join(REPO, "docs", "m2", "parts_source.md")


def render_target(name):
    """A render file name to (variant, output name), or None when it is not part of a set."""
    m = RENDER_RE.match(name)
    if not m:
        return None
    variant, kind = SETS[m.group(1)], m.group(2)
    if kind == "anchors.json":
        return variant, "anchors.json"
    if kind == "hero.png":
        return variant, "hero.webp"
    return variant, f"turn_{m.group(3)}.webp"


def fit(im, px):
    """Scales down so the longer side is px, keeping the aspect ratio; never scales up."""
    w, h = im.size
    s = px / max(w, h)
    if s >= 1:
        return im
    return im.resize((max(1, round(w * s)), max(1, round(h * s))), Image.LANCZOS)


def webp(im, px):
    """WebP bytes at quality 80 with the longer side at most px; an image with alpha keeps it."""
    im = fit(im, px)
    if im.mode not in ("RGB", "RGBA"):
        im = im.convert("RGBA" if "A" in im.getbands() or "transparency" in im.info else "RGB")
    buf = io.BytesIO()
    im.save(buf, "WEBP", quality=QUALITY, method=6)
    return buf.getvalue()


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(data)
    return len(data)


def build(renders, parts, assets):
    jobs = []
    found = {v: set() for v in VARIANTS}
    for name in sorted(os.listdir(renders)):
        t = render_target(name)
        if t:
            jobs.append((os.path.join(renders, name), t))
            found[t[0]].add(t[1])
    need = {"hero.webp", "anchors.json"} | {f"turn_{k:02d}.webp" for k in range(FRAMES)}
    problems = [f"{v}: missing {', '.join(sorted(need - found[v]))}" for v in VARIANTS if need - found[v]]
    missing = [p for p in PARTS if not os.path.isfile(os.path.join(parts, p + ".png"))]
    if missing:
        problems.append("parts: missing " + ", ".join(missing))
    if not os.path.isfile(os.path.join(parts, SOURCE_DOC)):
        problems.append(f"parts: missing {SOURCE_DOC}")
    if problems:
        sys.exit("error: " + "; ".join(problems))

    sizes = {}
    for src, (variant, out) in jobs:
        dst = os.path.join(assets, "engine3d", variant, out)
        if out == "anchors.json":
            with open(src, "rb") as f:
                n = write(dst, f.read())
        else:
            with Image.open(src) as im:
                n = write(dst, webp(im, HERO_PX if out == "hero.webp" else TURN_PX))
        files, total = sizes.get(variant, (0, 0))
        sizes[variant] = (files + 1, total + n)
    for p in PARTS:
        with Image.open(os.path.join(parts, p + ".png")) as im:
            n = write(os.path.join(assets, "parts", p + ".webp"), webp(im, PART_PX))
        files, total = sizes.get("parts", (0, 0))
        sizes["parts"] = (files + 1, total + n)
    os.makedirs(os.path.dirname(SOURCE_COPY), exist_ok=True)
    shutil.copyfile(os.path.join(parts, SOURCE_DOC), SOURCE_COPY)

    print(f"{'set':<8} {'files':>5} {'KB':>8}")
    for k, (files, total) in sizes.items():
        print(f"{k:<8} {files:>5} {total / 1024:8.0f}")
    grand = sum(t for _, t in sizes.values())
    print(f"{'total':<8} {sum(f for f, _ in sizes.values()):>5} {grand / 1024:8.0f}  ({grand / 1024 / 1024:.1f} MB)")
    print(f"copied {SOURCE_DOC} to {os.path.relpath(SOURCE_COPY, REPO)}")


def selftest():
    cases = {
        "i4_turn_07.png": ("i4", "turn_07.webp"), "v8plain_hero.png": ("v8", "hero.webp"),
        "v8_turn_23.png": ("v8sc", "turn_23.webp"), "v12_anchors.json": ("v12", "anchors.json"),
        "v12t_turn_00.png": ("v12t", "turn_00.webp"), "v8plain_turn_3.png": None,
    }
    for name, want in cases.items():
        got = render_target(name)
        assert got == want, f"{name}: {got} != {want}"
    assert fit(Image.new("RGB", (1024, 768)), 512).size == (512, 384)
    assert fit(Image.new("RGB", (300, 200)), 512).size == (300, 200)
    src = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    src.paste((255, 122, 48, 255), (16, 16, 48, 48))
    out = Image.open(io.BytesIO(webp(src, 32)))
    assert out.mode == "RGBA" and out.size == (32, 32), (out.mode, out.size)
    assert out.getpixel((1, 1))[3] < 16 and out.getpixel((16, 16))[3] > 240, (out.getpixel((1, 1)), out.getpixel((16, 16)))
    print("selftest ok")


def main():
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    if sys.argv[1:] == ["--selftest"]:
        selftest()
        return
    ap = argparse.ArgumentParser(description="Build the garage's WebP assets from the turntable renders and part photos.")
    ap.add_argument("renders")
    ap.add_argument("parts")
    ap.add_argument("assets")
    a = ap.parse_args()
    build(a.renders, a.parts, a.assets)


if __name__ == "__main__":
    main()
