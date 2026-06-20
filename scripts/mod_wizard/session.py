"""
mod_wizard/session.py — Session persistence for the mod creation wizard.

Saves checkpoint state and terminal output history so that a wizard
run can be resumed after interruption.  A session is created for each
mod the user builds; the outer loop in run_ai_mode() creates a fresh
session every iteration.

Checkpoints are saved at each step transition.  On resume the saved
terminal output is replayed and execution continues from the next step.
"""

from __future__ import annotations

import builtins
import json
import sys
from datetime import datetime
from pathlib import Path
from typing import Any


SESSIONS_DIR = Path(__file__).resolve().parents[2] / "sessions"

_original_input = builtins.input

STEP_LABELS: dict[str, str] = {
    "start": "Not started",
    "step1_name": "Name entered",
    "step2_desc": "Description entered",
    "step3_sent": "AI response received",
    "step5_diagnose": "Diagnosis complete",
    "step6_compile": "Compile attempted",
    "step7_refine": "In refinement",
    "complete": "Complete",
}


class _TeeWriter:
    """Writes to both real stdout and a log file."""

    def __init__(self, real_stdout, log_file):
        self._real = real_stdout
        self._log = log_file

    def write(self, text):
        self._real.write(text)
        self._log.write(text)
        self._log.flush()

    def flush(self):
        self._real.flush()
        self._log.flush()


class Session:
    """Manages a wizard session with checkpoint save/restore and output logging."""

    _active: Session | None = None

    def __init__(self, session_id: str, data: dict[str, Any]):
        self.id = session_id
        self._data = data
        self._tee: _TeeWriter | None = None
        self._real_stdout = None

    # ── Properties ──────────────────────────────────────────────────────

    @property
    def current_step(self) -> str:
        return self._data.get("current_step", "start")

    @property
    def mod_name(self) -> str | None:
        return self._data.get("mod_name")

    @property
    def description(self) -> str | None:
        return self._data.get("description")

    @property
    def resume_url(self) -> str | None:
        return self._data.get("resume_url")

    @property
    def response(self) -> str | None:
        return self._data.get("response")

    @property
    def result(self) -> dict | None:
        return self._data.get("result")

    @property
    def should_compile(self) -> bool:
        return self._data.get("should_compile", True)

    @property
    def compiled_ok(self) -> bool:
        return self._data.get("compiled_ok", False)

    @property
    def created_at(self) -> str:
        return self._data.get("created_at", "")

    @property
    def updated_at(self) -> str:
        return self._data.get("updated_at", "")

    @property
    def step_label(self) -> str:
        return STEP_LABELS.get(self.current_step, self.current_step)

    # ── Save / Update ──────────────────────────────────────────────────

    def update(self, step: str, **kwargs: Any) -> None:
        self._data["current_step"] = step
        for k, v in kwargs.items():
            self._data[k] = v
        self._data["updated_at"] = datetime.now().isoformat()
        self._save()

    def _save(self) -> None:
        SESSIONS_DIR.mkdir(parents=True, exist_ok=True)
        path = SESSIONS_DIR / f"{self.id}.json"
        with open(path, "w", encoding="utf-8") as f:
            json.dump(self._data, f, indent=2, ensure_ascii=False)

    # ── Output capture ─────────────────────────────────────────────────

    def _log_path(self) -> Path:
        return SESSIONS_DIR / f"{self.id}_history.txt"

    def install_tee(self) -> None:
        if self._tee is not None:
            return
        self._real_stdout = sys.stdout
        log_file = open(self._log_path(), "a", encoding="utf-8")
        self._tee = _TeeWriter(self._real_stdout, log_file)
        sys.stdout = self._tee
        Session._active = self
        builtins.input = self._logged_input

    def uninstall_tee(self) -> None:
        if self._tee is not None:
            sys.stdout = self._real_stdout
            self._tee._log.close()
            self._tee = None
            self._real_stdout = None
        Session._active = None
        builtins.input = _original_input

    def _logged_input(self, prompt: str = "") -> str:
        value = _original_input(prompt)
        if self._tee:
            self._tee._log.write(value + "\n")
            self._tee._log.flush()
        return value

    # ── Replay ─────────────────────────────────────────────────────────

    def replay_history(self) -> None:
        log_path = self._log_path()
        if not log_path.exists():
            return
        text = log_path.read_text(encoding="utf-8")
        if not text.strip():
            return
        print()
        print("=" * 60)
        print("  REPLAYING SESSION HISTORY")
        print("=" * 60)
        print()
        print(text, end="")
        print()
        print("=" * 60)
        print(f"  RESUMING FROM: {self.step_label}")
        print("=" * 60)
        print()

    # ── Class methods ──────────────────────────────────────────────────

    @classmethod
    def create(cls) -> Session:
        SESSIONS_DIR.mkdir(parents=True, exist_ok=True)
        now = datetime.now()
        session_id = now.strftime("%Y%m%d_%H%M%S")
        data: dict[str, Any] = {
            "id": session_id,
            "current_step": "start",
            "mod_name": None,
            "description": None,
            "resume_url": None,
            "response": None,
            "result": None,
            "should_compile": True,
            "compiled_ok": False,
            "created_at": now.isoformat(),
            "updated_at": now.isoformat(),
        }
        session = cls(session_id, data)
        session._save()
        return session

    @classmethod
    def load(cls, session_id: str) -> Session:
        path = SESSIONS_DIR / f"{session_id}.json"
        if not path.exists():
            raise FileNotFoundError(f"Session not found: {session_id}")
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
        return cls(session_id, data)

    @classmethod
    def list_sessions(cls) -> list[dict[str, Any]]:
        SESSIONS_DIR.mkdir(parents=True, exist_ok=True)
        sessions: list[dict[str, Any]] = []
        for p in sorted(SESSIONS_DIR.glob("*.json"), reverse=True):
            try:
                with open(p, "r", encoding="utf-8") as f:
                    data = json.load(f)
                sessions.append(data)
            except (json.JSONDecodeError, OSError):
                continue
        return sessions

    @classmethod
    def delete(cls, session_id: str) -> None:
        data_path = SESSIONS_DIR / f"{session_id}.json"
        log_path = SESSIONS_DIR / f"{session_id}_history.txt"
        if data_path.exists():
            data_path.unlink()
        if log_path.exists():
            log_path.unlink()
