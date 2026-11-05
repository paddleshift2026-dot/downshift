"""Synthetic stand-ins for the five voice takes, for `bash tools/show.sh voice` (no microphone needed).

    python tools/voice_samples.py OUT_DIR   ->  engine.wav whistle.wav blowoff.wav pop.wav shift.wav (48 kHz mono 16-bit, 2.5 s)

Each has silence around a sound shaped like the take the record sheet asks for: a lip trill, a hiss, a "pshh", a
"pang" and a "chi-ki-cha". They only have to pass VoiceCheck and exercise the pipeline, not sound like a person.
"""
import math
import random
import struct
import sys
import wave
from pathlib import Path

RATE = 48_000


def silence(sec):
    return [0.0] * int(sec * RATE)


def lowpass(x, hz):
    k = 1 - math.exp(-2 * math.pi * hz / RATE)
    y, v = [], 0.0
    for s in x:
        v += k * (s - v)
        y.append(v)
    return y


def bandpass(x, hz, q=4.0):
    w = 2 * math.pi * hz / RATE
    alpha = math.sin(w) / (2 * q)
    a0, a1, a2 = 1 + alpha, -2 * math.cos(w), 1 - alpha
    y, x1, x2, y1, y2 = [], 0.0, 0.0, 0.0, 0.0
    for s in x:
        v = (alpha * s - alpha * x2 - a1 * y1 - a2 * y2) / a0
        x2, x1, y2, y1 = x1, s, y1, v
        y.append(v)
    return y


def noise(sec, seed):
    rnd = random.Random(seed)
    return [2 * rnd.random() - 1 for _ in range(int(sec * RATE))]


def trill(sec, f0=110.0, flutter=24.0):
    out, phase = [], 0.0
    for i in range(int(sec * RATE)):
        phase += f0 / RATE
        out.append((2.0 * (phase % 1.0) - 1.0) * (0.55 + 0.45 * math.sin(2 * math.pi * flutter * i / RATE)))
    return lowpass(out, 1800.0)


def envelope(x, attack, decay):
    return [s * min(1.0, i / max(1.0, attack * RATE)) * math.exp(-i / (decay * RATE)) for i, s in enumerate(x)]


def scale(x, peak):
    m = max(abs(s) for s in x) or 1.0
    return [s * peak / m for s in x]


def write(path, x):
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1.0, min(1.0, s)) * 32767)) for s in x))


def main(out):
    out.mkdir(parents=True, exist_ok=True)
    pang = [math.sin(2 * math.pi * 90 * i / RATE) + 0.6 * n for i, n in enumerate(noise(0.15, 3))]
    chikicha = []
    for k, hz in enumerate((5000.0, 3000.0, 4200.0)):
        chikicha += scale(envelope(bandpass(noise(0.06, 4 + k), hz, 3.0), 0.003, 0.03), 0.5) + silence(0.08)
    takes = {
        "engine": silence(0.3) + scale(trill(1.8), 0.5),
        "whistle": silence(0.3) + scale(bandpass(noise(1.8, 1), 3500.0), 0.4),
        "blowoff": silence(0.4) + scale(envelope(bandpass(noise(0.6, 2), 2500.0, 1.5), 0.01, 0.15), 0.5),
        "pop": silence(0.5) + scale(envelope(pang, 0.002, 0.04), 0.6),
        "shift": silence(0.5) + chikicha,
    }
    for name, x in takes.items():
        x = (x + silence(2.5))[: int(2.5 * RATE)]
        write(out / f"{name}.wav", x)
        print(f"{name}.wav  peak {max(abs(s) for s in x):.2f}")


if __name__ == "__main__":
    main(Path(sys.argv[1] if len(sys.argv) > 1 else "docs/m5/samples"))
