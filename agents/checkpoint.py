# Progress files for long agent runs. Each finished step is saved as soon as it passes
# validation, so a run that stops halfway (a used-up quota, a dropped connection) resumes where
# it left off instead of paying for the same work again.

import json
import logging
from dataclasses import dataclass
from pathlib import Path
from typing import Any

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class SavedStep:
    model: str
    data: Any


class Checkpoint:
    def __init__(self, path: Path) -> None:
        self.path = path

    def load(self) -> dict[str, SavedStep]:
        steps = self._read()
        if steps:
            logger.info("Resuming from %s: %d step(s) already done", self.path.name, len(steps))
        return {name: SavedStep(step["model"], step["data"]) for name, step in steps.items()}

    def save(self, name: str, model: str, data: Any) -> None:
        steps = self._read()
        steps[name] = {"model": model, "data": data}
        self.path.parent.mkdir(parents=True, exist_ok=True)
        # Written to a side file first, so a crash mid-write never leaves a half-written file.
        temporary = self.path.with_name(self.path.name + ".tmp")
        temporary.write_text(json.dumps({"steps": steps}, ensure_ascii=False), encoding="utf-8")
        temporary.replace(self.path)

    def _read(self) -> dict[str, Any]:
        if not self.path.exists():
            return {}
        steps: dict[str, Any] = json.loads(self.path.read_text(encoding="utf-8"))["steps"]
        return steps

    def clear(self) -> None:
        self.path.unlink(missing_ok=True)


def progress_path(out: Path) -> Path:
    return out.with_name(out.stem + ".progress.json")
