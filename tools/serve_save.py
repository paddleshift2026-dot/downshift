"""Serves ROOT on 127.0.0.1:8799 with HTTP Range (so a browser can seek in a large video) and writes POST /save/NAME
bodies into OUT (browser exports such as tools/caption_burn.html's captioned video).

    python tools/serve_save.py . docs/m4
"""
import http.server
import os
import re
import sys

root, out = sys.argv[1], sys.argv[2]


class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=root, **kwargs)

    def do_GET(self):
        m = re.match(r"bytes=(\d*)-(\d*)$", self.headers.get("Range", ""))
        path = self.translate_path(self.path)
        if not m or not os.path.isfile(path):
            return super().do_GET()
        size = os.path.getsize(path)
        start = int(m.group(1)) if m.group(1) else max(0, size - int(m.group(2)))
        end = int(m.group(2)) if m.group(1) and m.group(2) else size - 1
        end = min(end, size - 1)
        self.send_response(206)
        self.send_header("Content-Type", self.guess_type(path))
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
        self.send_header("Content-Length", str(end - start + 1))
        self.end_headers()
        with open(path, "rb") as f:
            f.seek(start)
            left = end - start + 1
            while left > 0:
                chunk = f.read(min(1 << 20, left))
                if not chunk:
                    break
                try:
                    self.wfile.write(chunk)
                except (BrokenPipeError, ConnectionResetError):
                    return
                left -= len(chunk)

    def do_POST(self):
        n = int(self.headers.get("Content-Length", 0))
        data = self.rfile.read(n)
        name = os.path.basename(self.path) or "upload.bin"
        with open(os.path.join(out, name), "wb") as f:
            f.write(data)
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b"ok")


http.server.ThreadingHTTPServer(("127.0.0.1", 8799), Handler).serve_forever()
