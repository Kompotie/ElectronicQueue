from __future__ import annotations

import os
import sqlite3
import threading
from dataclasses import dataclass
from datetime import datetime


class DbError(Exception):
    pass


@dataclass(frozen=True)
class State:
    current_ticket: int
    last_ticket: int
    length: int


class QueueDb:
    """
    - WAITING: user is waiting
    - CALLED: ticket is currently served (current_ticket)
    - DONE: ticket is already served
    """
    def __init__(self, path: str):
        self.path = path
        self._lock = threading.Lock()

    def _conn(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.path, check_same_thread=False)
        conn.row_factory = sqlite3.Row
        return conn

    def init(self):
        os.makedirs(os.path.dirname(self.path), exist_ok=True)
        with self._conn() as c:
            c.execute("""
                CREATE TABLE IF NOT EXISTS queue_entries(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    ticket INTEGER UNIQUE NOT NULL,
                    name TEXT NOT NULL,
                    status TEXT NOT NULL,
                    created_at TEXT NOT NULL
                );
            """)
            c.execute("""
                CREATE TABLE IF NOT EXISTS queue_meta(
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    current_ticket INTEGER NOT NULL,
                    last_ticket INTEGER NOT NULL
                );
            """)
            row = c.execute("SELECT id FROM queue_meta WHERE id = 1").fetchone()
            if row is None:
                c.execute("INSERT INTO queue_meta(id, current_ticket, last_ticket) VALUES(1, 0, 0)")
            c.commit()

    def _get_meta(self, c: sqlite3.Connection) -> tuple[int, int]:
        row = c.execute("SELECT current_ticket, last_ticket FROM queue_meta WHERE id = 1").fetchone()
        if row is None:
            raise DbError("queue_meta is missing")
        return int(row["current_ticket"]), int(row["last_ticket"])

    def _set_meta(self, c: sqlite3.Connection, current_ticket: int, last_ticket: int):
        c.execute("UPDATE queue_meta SET current_ticket=?, last_ticket=? WHERE id=1", (current_ticket, last_ticket))

    def _now(self) -> str:
        return datetime.now().isoformat(timespec="seconds")

    def get_state(self) -> dict:
        with self._lock:
            with self._conn() as c:
                current_ticket, last_ticket = self._get_meta(c)
                length = c.execute(
                    "SELECT COUNT(*) AS n FROM queue_entries WHERE status IN ('WAITING','CALLED')"
                ).fetchone()["n"]
                return {"current_ticket": current_ticket, "last_ticket": last_ticket, "length": int(length)}

    def _position(self, c: sqlite3.Connection, ticket: int, current_ticket: int) -> int:
        """
        Position = how many active tickets are ahead of this ticket.
        Tickets ahead are: status != DONE, ticket > current_ticket, ticket < this ticket
        """
        row = c.execute(
            """
            SELECT COUNT(*) AS n
            FROM queue_entries
            WHERE status IN ('WAITING','CALLED')
              AND ticket > ?
              AND ticket < ?
            """,
            (current_ticket, ticket),
        ).fetchone()
        return int(row["n"])

    def join(self, name: str) -> tuple[int, int, int]:
        with self._lock:
            with self._conn() as c:
                current_ticket, last_ticket = self._get_meta(c)
                ticket = last_ticket + 1
                c.execute(
                    "INSERT INTO queue_entries(ticket, name, status, created_at) VALUES(?,?, 'WAITING', ?)",
                    (ticket, name, self._now()),
                )
                self._set_meta(c, current_ticket=current_ticket, last_ticket=ticket)
                # If queue was empty and no one is being served, we can call first ticket immediately
                # by advancing if current_ticket == 0 or there is no CALLED ticket.
                c.commit()
                position = self._position(c, ticket=ticket, current_ticket=current_ticket)
                return ticket, position, current_ticket

    def get_status(self, ticket: int) -> dict:
        with self._lock:
            with self._conn() as c:
                current_ticket, last_ticket = self._get_meta(c)
                row = c.execute(
                    "SELECT ticket, status FROM queue_entries WHERE ticket = ?",
                    (ticket,),
                ).fetchone()
                if row is None:
                    raise DbError("ticket not found")
                status = str(row["status"])
                position = 0
                if status in ("WAITING", "CALLED"):
                    position = self._position(c, ticket=ticket, current_ticket=current_ticket)
                return {
                    "ticket": int(ticket),
                    "status": status,
                    "position": int(position),
                    "current_ticket": int(current_ticket),
                }

    def _has_next(self, c: sqlite3.Connection, current_ticket: int) -> bool:
        row = c.execute(
            """
            SELECT 1
            FROM queue_entries
            WHERE status IN ('WAITING','CALLED') AND ticket > ?
            ORDER BY ticket ASC
            LIMIT 1
            """,
            (current_ticket,),
        ).fetchone()
        return row is not None

    def advance_if_possible(self) -> dict | None:
        with self._lock:
            with self._conn() as c:
                current_ticket, last_ticket = self._get_meta(c)
                if not self._has_next(c, current_ticket):
                    return None
                return self._advance_locked(c)

    def advance(self) -> dict:
        with self._lock:
            with self._conn() as c:
                current_ticket, _ = self._get_meta(c)
                if not self._has_next(c, current_ticket):
                    # no-op; return state
                    return self.get_state()
                return self._advance_locked(c)

    def _advance_locked(self, c: sqlite3.Connection) -> dict:
        current_ticket, last_ticket = self._get_meta(c)

        # Mark previous called as DONE (if any)
        c.execute("UPDATE queue_entries SET status='DONE' WHERE status='CALLED'")

        # Pick next active ticket > current_ticket
        row = c.execute(
            """
            SELECT ticket
            FROM queue_entries
            WHERE status='WAITING' AND ticket > ?
            ORDER BY ticket ASC
            LIMIT 1
            """,
            (current_ticket,),
        ).fetchone()
        if row is None:
            # Nothing waiting; might happen if queue only had CALLED/DONE
            self._set_meta(c, current_ticket=current_ticket, last_ticket=last_ticket)
            c.commit()
            return self.get_state()

        new_current = int(row["ticket"])
        # Mark as CALLED
        c.execute("UPDATE queue_entries SET status='CALLED' WHERE ticket=?", (new_current,))
        self._set_meta(c, current_ticket=new_current, last_ticket=last_ticket)
        c.commit()

        length = c.execute(
            "SELECT COUNT(*) AS n FROM queue_entries WHERE status IN ('WAITING','CALLED')"
        ).fetchone()["n"]
        return {"current_ticket": new_current, "last_ticket": last_ticket, "length": int(length)}

    def reset(self) -> dict:
        """Clear the queue (demo / testing helper)."""
        with self._lock:
            with self._conn() as c:
                c.execute("DELETE FROM queue_entries")
                self._set_meta(c, current_ticket=0, last_ticket=0)
                c.commit()
        return self.get_state()
