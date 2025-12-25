#!/usr/bin/env python3
"""Electronic Queue - REST server, Stage 6 (SQLite)."""

from __future__ import annotations

import json

import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

from db import QueueDb, DbError

HOST = "0.0.0.0"
PORT = 8080
db = QueueDb("queue.db")

class Handler(BaseHTTPRequestHandler):
    def _json(self, code: int, obj) -> None:
        data = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _read_json(self):
        n = int(self.headers.get("Content-Length", "0") or "0")
        raw = self.rfile.read(n).decode("utf-8") if n > 0 else ""
        return json.loads(raw) if raw else {}

    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path == "/health":
            return self._json(200, {"status": "ok"})
        if parsed.path == "/queue/state":
            s = db.state()
            return self._json(200, {"current_ticket": s.current_ticket, "last_ticket": s.last_ticket, "length": s.length})
        if parsed.path == "/queue/status":
            qs = parse_qs(parsed.query)
            try:
                ticket = int(qs.get("ticket", ["0"])[0])
            except Exception:
                return self._json(400, {"error": "bad_ticket"})
            st, pos, current = db.status(ticket)
            return self._json(200, {"ticket": ticket, "status": st, "position": pos, "current_ticket": current})
        return self._json(404, {"error": "not_found"})

    def do_POST(self):
        parsed = urlparse(self.path)
        if parsed.path == "/queue/join":
            try:
                body = self._read_json()
                name = (body.get("name") or "").strip()
                if not name:
                    return self._json(400, {"error": "name_required"})
                ticket, pos, current = db.join(name)
                return self._json(200, {"ticket": ticket, "position": pos, "current_ticket": current})
            except Exception:
                return self._json(400, {"error": "bad_json"})
        if parsed.path == "/queue/next":
            s = db.next()
            return self._json(200, {"current_ticket": s.current_ticket, "last_ticket": s.last_ticket, "length": s.length})
        if parsed.path == "/queue/reset":
            db.reset()
            return self._json(200, {"status": "ok"})
        return self._json(404, {"error": "not_found"})

AUTO_ADVANCE_SECONDS = 10

def _auto_worker():
    while True:
        time.sleep(AUTO_ADVANCE_SECONDS)
        try:
            db.next()
        except Exception:
            pass

def main() -> None:

    t = threading.Thread(target=_auto_worker, daemon=True)
    t.start()
    httpd = ThreadingHTTPServer((HOST, PORT), Handler)
    print(f"Server listening on http://{HOST}:{PORT}")
    httpd.serve_forever()

if __name__ == "__main__":
    main()
