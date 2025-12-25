#!/usr/bin/env python3
"""
Electronic Queue - simple REST server (Raspberry Pi role) with SQLite storage.

Endpoints:
- GET  /health
- POST /queue/join            {"name": "..."} -> {"ticket": int, "position": int, "current_ticket": int}
- GET  /queue/state           -> {"current_ticket": int, "last_ticket": int, "length": int}
- GET  /queue/status?ticket=  -> {"ticket": int, "status": str, "position": int, "current_ticket": int}
- POST /queue/next            -> {"current_ticket": int, "last_ticket": int, "length": int}
- POST /queue/reset           -> clear queue (demo helper)

Notes:
- For demo purposes, an auto-advancer thread can move the queue every N seconds.
- Designed to run inside Raspberry Pi Desktop VM (VirtualBox) and be reached from Android Emulator via
  Host port forwarding (Android Emulator uses http://10.0.2.2:<port> to reach the host).
"""
from __future__ import annotations

import json
import os
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

from db import QueueDb, DbError


HOST = os.environ.get("EQ_HOST", "0.0.0.0")
PORT = int(os.environ.get("EQ_PORT", "8080"))
DB_PATH = os.environ.get("EQ_DB", os.path.join(os.path.dirname(__file__), "queue.db"))

AUTO_ADVANCE = os.environ.get("EQ_AUTO_ADVANCE", "1") == "1"
ADVANCE_SECONDS = int(os.environ.get("EQ_ADVANCE_SECONDS", "10"))


class JsonHandler(BaseHTTPRequestHandler):
    db = QueueDb(DB_PATH)

    def _send_json(self, code: int, payload: dict):
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        # CORS is optional; doesn't hurt (useful if you later add a web page)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET,POST,OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()
        self.wfile.write(data)

    def _read_json(self) -> dict:
        length = int(self.headers.get("Content-Length", "0"))
        if length <= 0:
            return {}
        raw = self.rfile.read(length)
        try:
            return json.loads(raw.decode("utf-8"))
        except json.JSONDecodeError:
            raise DbError("Invalid JSON body")

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET,POST,OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()

    def do_GET(self):
        parsed = urlparse(self.path)
        path = parsed.path

        try:
            if path == "/health":
                self._send_json(200, {"status": "ok"})
                return

            if path == "/queue/state":
                state = self.db.get_state()
                self._send_json(200, state)
                return

            if path == "/queue/status":
                qs = parse_qs(parsed.query or "")
                ticket_str = (qs.get("ticket") or [None])[0]
                if ticket_str is None or not ticket_str.isdigit():
                    self._send_json(400, {"error": "ticket query parameter is required and must be an integer"})
                    return
                ticket = int(ticket_str)
                info = self.db.get_status(ticket)
                self._send_json(200, info)
                return

            self._send_json(404, {"error": "not found"})
        except DbError as e:
            self._send_json(400, {"error": str(e)})
        except Exception as e:
            self._send_json(500, {"error": f"internal error: {e}"})

    def do_POST(self):
        parsed = urlparse(self.path)
        path = parsed.path
        try:
            if path == "/queue/join":
                body = self._read_json()
                name = (body.get("name") or "").strip()
                if not name:
                    self._send_json(400, {"error": "name is required"})
                    return
                if len(name) > 64:
                    self._send_json(400, {"error": "name is too long (max 64)"})
                    return
                ticket, position, current_ticket = self.db.join(name)
                self._send_json(200, {"ticket": ticket, "position": position, "current_ticket": current_ticket})
                return

            if path == "/queue/next":
                state = self.db.advance()
                self._send_json(200, state)
                return

            if path == "/queue/reset":
                state = self.db.reset()
                self._send_json(200, state)
                return

            self._send_json(404, {"error": "not found"})
        except DbError as e:
            self._send_json(400, {"error": str(e)})
        except Exception as e:
            self._send_json(500, {"error": f"internal error: {e}"})

    def log_message(self, fmt, *args):
        # Keep logs readable
        print("[%s] %s - %s" % (self.log_date_time_string(), self.address_string(), fmt % args))


def _auto_advancer(db: QueueDb):
    while True:
        time.sleep(ADVANCE_SECONDS)
        try:
            db.advance_if_possible()
        except Exception as e:
            print("Auto-advance error:", e)


def main():
    JsonHandler.db.init()
    if AUTO_ADVANCE:
        t = threading.Thread(target=_auto_advancer, args=(JsonHandler.db,), daemon=True)
        t.start()
        print(f"Auto-advance enabled: every {ADVANCE_SECONDS}s")
    print(f"DB: {DB_PATH}")
    httpd = ThreadingHTTPServer((HOST, PORT), JsonHandler)
    print(f"Listening on http://{HOST}:{PORT}")
    httpd.serve_forever()


if __name__ == "__main__":
    main()
