"""Lists engine-sound candidates: format, length, level and whether the loop seam is clean.

usage: python -I tools/audio_inventory.py AUDIO_DIR OUT_MD
       python -I tools/audio_inventory.py --selftest
"""
import math
import os
import struct
import sys
import wave


def read_mono(path):
    with wave.open(path, "rb") as w:
        ch, width, rate, n = w.getnchannels(), w.getsampwidth(), w.getframerate(), w.getnframes()
        raw = w.readframes(n)
    if width not in (2, 3):
        raise ValueError(f"{width * 8}-bit not supported")
    scale = float(1 << (width * 8 - 1))
    step = width * ch
    vals = []
    for f in range(0, len(raw) - step + 1, step):
        s = 0.0
        for c in range(ch):
            o = f + c * width
            v = struct.unpack_from("<h", raw, o)[0] if width == 2 else int.from_bytes(raw[o:o + 3], "little", signed=True)
            s += v / scale
        vals.append(s / ch)
    return rate, ch, width * 8, vals


def seam_ratio(x):
    """Jump between the last and the first sample, relative to the peak: large values click when looped."""
    peak = max((abs(v) for v in x), default=0.0)
    if peak == 0.0 or len(x) < 2:
        return 0.0
    return abs(x[-1] - x[0]) / peak


def db(v):
    return 20 * math.log10(v) if v > 0 else float("-inf")


def describe(path):
    rate, ch, bits, x = read_mono(path)
    peak = max((abs(v) for v in x), default=0.0)
    rms = math.sqrt(sum(v * v for v in x) / len(x)) if x else 0.0
    seam = seam_ratio(x)
    return {"file": path, "rate": rate, "ch": ch, "bits": bits, "sec": len(x) / rate if rate else 0.0,
            "peak_db": db(peak), "rms_db": db(rms), "seam": seam, "loop_ok": seam < 0.05}


def main(root, out):
    rows = []
    for dirpath, _, names in os.walk(root):
        for n in sorted(names):
            if n.lower().endswith(".wav"):
                p = os.path.join(dirpath, n)
                try:
                    rows.append(describe(p))
                except Exception as e:  # unreadable or unsupported file: list it with the reason
                    rows.append({"file": p, "error": str(e)})
    with open(out, "w", encoding="utf-8") as f:
        f.write("| 파일 | Hz | ch | bit | 길이(s) | 피크 dBFS | RMS dBFS | 이음새 | 루프 |\n|---|---|---|---|---|---|---|---|---|\n")
        for r in rows:
            name = os.path.relpath(r["file"], root).replace("\\", "/")
            if "error" in r:
                f.write(f"| {name} | 오류: {r['error']} | | | | | | | |\n")
                continue
            f.write(f"| {name} | {r['rate']} | {r['ch']} | {r['bits']} | {r['sec']:.2f} | {r['peak_db']:.1f} | "
                    f"{r['rms_db']:.1f} | {r['seam']:.3f} | {'OK' if r['loop_ok'] else '튐'} |\n")
    print(f"{len(rows)} files -> {out}")


def selftest():
    n = 4800
    whole = [math.sin(2 * math.pi * 10 * i / n) for i in range(n)]    # 10 whole cycles: clean seam
    cut = [math.sin(2 * math.pi * 10.25 * i / n) for i in range(n)]   # ends on a peak: clicks when looped
    assert seam_ratio(whole) < 0.05, seam_ratio(whole)
    assert seam_ratio(cut) > 0.5, seam_ratio(cut)
    assert seam_ratio([]) == 0.0
    print("selftest ok")


if __name__ == "__main__":
    if sys.argv[1:] == ["--selftest"]:
        selftest()
    elif len(sys.argv) == 3:
        main(sys.argv[1], sys.argv[2])
    else:
        print(__doc__)
        sys.exit(1)
