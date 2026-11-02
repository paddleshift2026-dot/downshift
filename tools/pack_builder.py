"""Turns a folder of licensed engine WAVs into a Downshift engine pack: <name>.dsa clips plus manifest.json.

usage: python -I tools/pack_builder.py SRC_DIR OUT_DIR --engine I4 [--variant interior|exterior] [--estimate-rpm CYL] [--source TEXT]
       python -I tools/pack_builder.py --selftest

The .dsa format is the one ClipCodec.kt reads: "DSA1", sample rate, frames, CRC32 of the plain PCM, then 16-bit
PCM XORed with a keystream seeded by the clip name. It stops the recordings being lifted out of the APK as WAVs,
as the licence asks; it is obfuscation, not DRM. Neither the WAVs nor the output belong in git.
"""
import argparse
import json
import math
import os
import re
import struct
import sys
import zlib

LICENSE = "GameDev Market Pro Licence"
BANDS = ["idle_low", "low", "low_med", "med", "med_high", "high", "very_high"]
BAND_RE = re.compile(r"(?<![a-z])(very_high|med_high|low_med|idle_low|high|med|low)_(on|off)(?![a-z])")
IDLE_RE = re.compile(r"(?<![a-z])idle(?!_low)(?![a-z])")
MAXRPM_RE = re.compile(r"max_?rpm")


def fnv1a32(text):
    h = 0x811C9DC5
    for b in text.encode("utf-8"):
        h ^= b
        h = (h * 0x01000193) & 0xFFFFFFFF
    return h


def keystream(name, n):
    s = fnv1a32(name) or 0x9E3779B9
    out = bytearray()
    while len(out) < n:
        s ^= (s << 13) & 0xFFFFFFFF
        s ^= s >> 17
        s ^= (s << 5) & 0xFFFFFFFF
        out += struct.pack("<I", s)
    return bytes(out[:n])


def pcm16(x):
    x = max(-1.0, min(1.0, x))
    return math.floor(x * 32767 + 0.5)


def encode(name, sample_rate, samples):
    plain = struct.pack(f"<{len(samples)}h", *(pcm16(v) for v in samples))
    ks = keystream(name, len(plain))
    payload = (int.from_bytes(plain, "little") ^ int.from_bytes(ks, "little")).to_bytes(len(plain), "little") if plain else b""
    return b"DSA1" + struct.pack("<iiI", sample_rate, len(samples), zlib.crc32(plain) & 0xFFFFFFFF) + payload


def read_wav(path):
    """16- or 24-bit PCM WAV (plain or WAVE_FORMAT_EXTENSIBLE) to (rate, mono floats); stereo is averaged."""
    data = open(path, "rb").read()
    if data[:4] != b"RIFF" or data[8:12] != b"WAVE":
        raise ValueError("not a RIFF/WAVE file")
    pos, fmt, body = 12, None, None
    while pos + 8 <= len(data):
        cid, size = data[pos:pos + 4], struct.unpack_from("<I", data, pos + 4)[0]
        chunk = data[pos + 8:pos + 8 + size]
        if cid == b"fmt ":
            fmt = struct.unpack_from("<HHIIHH", chunk)
        elif cid == b"data":
            body = chunk
        pos += 8 + size + (size & 1)
    if fmt is None or body is None:
        raise ValueError("missing fmt or data chunk")
    tag, ch, rate, _, _, bits = fmt
    if tag not in (1, 0xFFFE) or bits not in (16, 24) or ch not in (1, 2):
        raise ValueError(f"only 16/24-bit PCM mono/stereo is supported (format={tag} bits={bits} channels={ch})")
    width = bits // 8
    step = width * ch
    scale = float(1 << (bits - 1))
    out = []
    for f in range(0, len(body) - step + 1, step):
        s = 0
        for c in range(ch):
            o = f + c * width
            s += struct.unpack_from("<h", body, o)[0] if width == 2 else int.from_bytes(body[o:o + 3], "little", signed=True)
        out.append(s / scale / ch)
    return rate, out


def clip_name(rel_path):
    """Maps a pack file to (clip name, is_interior), or (None, _) for files the app does not use."""
    p = rel_path.replace("\\", "/").lower()
    stem = os.path.splitext(os.path.basename(p))[0]
    interior = "interior" in p
    if "aggressiveness" in stem:
        return None, interior
    m = BAND_RE.search(stem)
    if m:
        return f"{m.group(2)}_{BANDS.index(m.group(1)) + 1}", interior
    if "engine_off" in stem:
        return "engine_off", interior
    if "startup" in stem:
        return "startup", interior
    if MAXRPM_RE.search(stem):
        return "maxRPM", interior
    if IDLE_RE.search(stem):
        return "idle", interior
    return None, interior


def estimate_rpm(rate, x, cylinders):
    """Firing frequency by autocorrelation of the middle 0.5 s (after a 4x box downsample), searched in 20-400 Hz."""
    k = 4
    mid = len(x) // 2
    half = min(len(x) // 2, rate // 4)
    seg = x[mid - half:mid + half]
    y = [sum(seg[i:i + k]) / k for i in range(0, len(seg) - k + 1, k)]
    mean = sum(y) / len(y)
    y = [v - mean for v in y]
    fs = rate / k
    lo, hi = int(fs / 400), int(fs / 20)
    if len(y) <= hi * 2:
        return None
    energy = sum(v * v for v in y) or 1e-12
    r = {}
    for lag in range(max(lo, 1), hi + 1):
        r[lag] = sum(y[i] * y[i + lag] for i in range(len(y) - lag)) / energy
    best = max(r.values())
    if best <= 0:
        return None
    # The shortest lag that is nearly as strong as the best one, so a whole engine cycle is not mistaken for one firing.
    lag = min(l for l, v in r.items() if v >= 0.9 * best and (l == lo or v >= r.get(l - 1, -1)) and v >= r.get(l + 1, -1))
    f0 = fs / lag
    return f0 * 120.0 / cylinders


def build(src, out, engine, variant, cylinders, source):
    picked = {}
    for dirpath, _, names in os.walk(src):
        for n in sorted(names):
            if not n.lower().endswith(".wav"):
                continue
            rel = os.path.relpath(os.path.join(dirpath, n), src)
            name, interior = clip_name(rel)
            if name is None or interior != (variant == "interior"):
                continue
            if name in picked:
                sys.exit(f"error: both {picked[name]} and {rel} map to {name}")
            picked[name] = rel
    if not picked:
        other = "exterior" if variant == "interior" else "interior"
        sys.exit(f"error: no {variant} clips found in {src}; try --variant {other}")
    os.makedirs(out, exist_ok=True)
    rate0, clips, rows = None, [], []
    order = ["idle"] + [f"{p}_{i}" for p in ("on", "off") for i in range(1, 8)] + ["startup", "engine_off", "maxRPM"]
    for name in sorted(picked, key=lambda c: order.index(c)):
        rate, x = read_wav(os.path.join(src, picked[name]))
        if rate0 is None:
            rate0 = rate
        elif rate != rate0:
            sys.exit(f"error: {picked[name]} is {rate} Hz but the pack is {rate0} Hz")
        with open(os.path.join(out, f"{name}.dsa"), "wb") as f:
            f.write(encode(name, rate, x))
        entry = {"name": name, "file": f"{name}.dsa"}
        rpm = estimate_rpm(rate, x, cylinders) if cylinders and (name == "idle" or name[:3] in ("on_", "off")) else None
        if rpm:
            entry["rpm"] = round(rpm)
        clips.append(entry)
        rows.append((name, picked[name], len(x) / rate, entry.get("rpm")))
    manifest = {"engine": engine, "source": source, "license": LICENSE, "sampleRate": rate0, "clips": clips}
    with open(os.path.join(out, "manifest.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2)
    print(f"{'clip':<12} {'sec':>6} {'rpm':>6}  source")
    for name, rel, sec, rpm in rows:
        print(f"{name:<12} {sec:6.2f} {rpm if rpm else '-':>6}  {rel}")
    for prefix in ("on", "off"):
        rpms = [e["rpm"] for e in clips if e["name"].startswith(prefix + "_") and "rpm" in e]
        if any(b <= a for a, b in zip(rpms, rpms[1:])):
            print(f"warning: estimated {prefix} rpms do not rise with the band: {rpms} (fix them in manifest.json by ear)")
    missing = [c for c in order if c not in picked]
    if missing:
        print("warning: missing clips:", ", ".join(missing))
    print(f"wrote {len(clips)} clips and manifest.json to {out}")


def selftest():
    enc = encode("idle", 44100, [0.0, 0.5, -0.5, 1.0])
    assert enc.hex() == "4453413144ac000004000000dae75c47aa8f89e495e71345", enc.hex()
    cases = {
        "idle_low_on.wav": ("on_1", False), "very_high_off.wav": ("off_7", False), "med_on.wav": ("on_4", False),
        "Interior/high_on.wav": ("on_6", True), "idle.wav": ("idle", False), "aggressiveness_on_fx.wav": (None, False),
        "I4_low_med_off.wav": ("off_3", False), "engine_off.wav": ("engine_off", False), "maxRPM.wav": ("maxRPM", False),
    }
    for path, want in cases.items():
        got = clip_name(path)
        assert got == want, f"{path}: {got} != {want}"
    rate = 44100
    pulses = [1.0 if i % (rate // 30) < 40 else 0.0 for i in range(rate)]
    rpm = estimate_rpm(rate, pulses, 4)
    assert rpm and abs(rpm - 900) <= 45, rpm
    print("selftest ok")


def main():
    if sys.argv[1:] == ["--selftest"]:
        selftest()
        return
    ap = argparse.ArgumentParser(description="Build a Downshift engine pack from licensed WAV loops.")
    ap.add_argument("src")
    ap.add_argument("out")
    ap.add_argument("--engine", required=True, choices=["I4", "V8", "V12"])
    ap.add_argument("--variant", default="interior", choices=["interior", "exterior"])
    ap.add_argument("--estimate-rpm", type=int, metavar="CYL", help="estimate each loop's rpm for this many cylinders")
    ap.add_argument("--source", help="where the recordings came from (default: the source folder name)")
    a = ap.parse_args()
    build(a.src, a.out, a.engine, a.variant, a.estimate_rpm, a.source or os.path.basename(os.path.normpath(a.src)))


if __name__ == "__main__":
    main()
