from __future__ import annotations

import sqlite3
from dataclasses import dataclass
from datetime import datetime

class DbError(RuntimeError):
    pass

@dataclass
class QueueState:
    current_ticket: int
    last_ticket: int
    length: int

class QueueDb:
    def __init__(self, db_path: str = "queue.db"):
        self.db_path = db_path
        self._init()

    def _conn(self):
        return sqlite3.connect(self.db_path)

    def _init(self):
        with self._conn() as c:
            c.execute("""
                CREATE TABLE IF NOT EXISTS queue_entries(
                    ticket INTEGER PRIMARY KEY,
                    name TEXT NOT NULL,
                    status TEXT NOT NULL,
                    created_at TEXT NOT NULL
                )
            """)
            c.execute("""
                CREATE TABLE IF NOT EXISTS queue_meta(
                    id INTEGER PRIMARY KEY CHECK(id=1),
                    current_ticket INTEGER NOT NULL,
                    last_ticket INTEGER NOT NULL
                )
            """)
            cur = c.execute("SELECT COUNT(*) FROM queue_meta WHERE id=1")
            if cur.fetchone()[0] == 0:
                c.execute("INSERT INTO queue_meta(id,current_ticket,last_ticket) VALUES (1,0,0)")

    def join(self, name: str) -> tuple[int,int,int]:
        with self._conn() as c:
            last = c.execute("SELECT last_ticket FROM queue_meta WHERE id=1").fetchone()[0]
            current = c.execute("SELECT current_ticket FROM queue_meta WHERE id=1").fetchone()[0]
            ticket = last + 1
            c.execute("UPDATE queue_meta SET last_ticket=? WHERE id=1", (ticket,))
            c.execute("INSERT INTO queue_entries(ticket,name,status,created_at) VALUES (?,?,?,?)",
                      (ticket, name, "WAITING", datetime.utcnow().isoformat()))
            if current == 0:
                # call first
                c.execute("UPDATE queue_meta SET current_ticket=? WHERE id=1", (ticket,))
                c.execute("UPDATE queue_entries SET status='CALLED' WHERE ticket=?", (ticket,))
                current = ticket
            pos = c.execute("SELECT COUNT(*) FROM queue_entries WHERE status='WAITING' AND ticket<?", (ticket,)).fetchone()[0]
            return ticket, pos, current

    def state(self) -> QueueState:
        with self._conn() as c:
            current, last = c.execute("SELECT current_ticket,last_ticket FROM queue_meta WHERE id=1").fetchone()
            length = c.execute("SELECT COUNT(*) FROM queue_entries WHERE status IN ('WAITING','CALLED')").fetchone()[0]
            return QueueState(current_ticket=current, last_ticket=last, length=length)

    def status(self, ticket: int) -> tuple[str,int,int]:
        with self._conn() as c:
            row = c.execute("SELECT status FROM queue_entries WHERE ticket=?", (ticket,)).fetchone()
            st = row[0] if row else "UNKNOWN"
            current = c.execute("SELECT current_ticket FROM queue_meta WHERE id=1").fetchone()[0]
            pos = 0
            if st == "WAITING":
                pos = c.execute("SELECT COUNT(*) FROM queue_entries WHERE status='WAITING' AND ticket<?", (ticket,)).fetchone()[0]
            return st, pos, current

    def next(self) -> QueueState:
        with self._conn() as c:
            current, last = c.execute("SELECT current_ticket,last_ticket FROM queue_meta WHERE id=1").fetchone()
            if current != 0:
                c.execute("UPDATE queue_entries SET status='DONE' WHERE ticket=?", (current,))
            row = c.execute("SELECT ticket FROM queue_entries WHERE status='WAITING' ORDER BY ticket LIMIT 1").fetchone()
            if row:
                current = row[0]
                c.execute("UPDATE queue_entries SET status='CALLED' WHERE ticket=?", (current,))
            else:
                current = 0
            c.execute("UPDATE queue_meta SET current_ticket=? WHERE id=1", (current,))
            length = c.execute("SELECT COUNT(*) FROM queue_entries WHERE status IN ('WAITING','CALLED')").fetchone()[0]
            return QueueState(current_ticket=current, last_ticket=last, length=length)

    def reset(self) -> None:
        with self._conn() as c:
            c.execute("DELETE FROM queue_entries")
            c.execute("UPDATE queue_meta SET current_ticket=0,last_ticket=0 WHERE id=1")
