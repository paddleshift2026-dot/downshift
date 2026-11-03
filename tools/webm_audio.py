"""The tracks of a WebM recording: codec, length and bytes per second, to check an emulator recording has its sound.

    python tools/webm_audio.py docs/m4/demo.webm

Walks the EBML structure (Segment / Tracks / Cluster / SimpleBlock | BlockGroup) with the standard library only. A
Vorbis track is there even when nothing plays, so loudness is checked by decoding in a browser (decodeAudioData).
"""
import sys
from collections import defaultdict

data = open(sys.argv[1], "rb").read()

SEGMENT, CLUSTER, TIMECODE, SIMPLEBLOCK, BLOCKGROUP, BLOCK = 0x18538067, 0x1F43B675, 0xE7, 0xA3, 0xA0, 0xA1
TRACKS, TRACKENTRY, TRACKNUMBER, CODECID = 0x1654AE6B, 0xAE, 0xD7, 0x86


def vint(i, mask=True):
    """A variable-length integer at i: (value, length)."""
    b, n, m = data[i], 1, 0x80
    while n <= 8 and not (b & m):
        n += 1
        m >>= 1
    v = b & (m - 1) if mask else b
    for k in range(1, n):
        v = (v << 8) | data[i + k]
    return v, n


def elements(start, end):
    i = start
    while i < end:
        eid, n = vint(i, mask=False)
        i += n
        size, m = vint(i)
        i += m
        if size == (1 << (7 * m)) - 1:  # unknown size: up to the end of the parent
            size = end - i
        yield eid, i, min(i + size, end)
        i += size


per = defaultdict(lambda: defaultdict(int))
codecs, last = {}, 0
for eid, s, e in elements(0, len(data)):
    if eid != SEGMENT:
        continue
    for cid, cs, ce in elements(s, e):
        if cid == TRACKS:
            for tid, ts, te in elements(cs, ce):
                if tid == TRACKENTRY:
                    fields = {fid: data[fs:fe] for fid, fs, fe in elements(ts, te)}
                    codecs[int.from_bytes(fields.get(TRACKNUMBER, b"\0"), "big")] = fields.get(CODECID, b"?").decode()
        if cid != CLUSTER:
            continue
        base = 0
        for bid, bs, be in elements(cs, ce):
            if bid == TIMECODE:
                base = int.from_bytes(data[bs:be], "big")
            blocks = [(bs, be)] if bid == SIMPLEBLOCK else [(x, y) for k, x, y in elements(bs, be) if k == BLOCK] if bid == BLOCKGROUP else []
            for x, y in blocks:
                track, n = vint(x)
                ms = base + int.from_bytes(data[x + n:x + n + 2], "big", signed=True)
                last = max(last, ms)
                per[track][ms // 1000] += y - x

print(f"{sys.argv[1]}: {len(data) / 1e6:.1f} MB, {last / 1000:.1f} s")
for t in sorted(per):
    secs = per[t]
    print(f"track {t} {codecs.get(t, '?')}: {len(secs)} s with data, mean {sum(secs.values()) / max(1, len(secs)) / 1024:.1f} KB/s")
