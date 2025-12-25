#!/usr/bin/env python3
"""Electronic Queue - minimal server skeleton (Stage 1)."""

from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HOST = "0.0.0.0"
PORT = 8080

class Handler(BaseHTTPRequestHandler):
    def _send(self, code: int, body: str) -> None:
        data = body.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path == "/health":
            return self._send(200, '{"status":"ok"}')
        return self._send(404, '{"error":"not_found"}')

def main() -> None:
    httpd = ThreadingHTTPServer((HOST, PORT), Handler)
    print(f"Server listening on http://{HOST}:{PORT}")
    httpd.serve_forever()

if __name__ == "__main__":
    main()
