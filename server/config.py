# Edge-server settings, read from the environment once at startup.

import os
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_PARAMS_DIR = REPO_ROOT / "contracts" / "params"


@dataclass(frozen=True)
class Settings:
    params_dir: Path


@lru_cache
def get_settings() -> Settings:
    raw_dir = os.environ.get("PRATIBIMB_PARAMS_DIR")
    params_dir = Path(raw_dir) if raw_dir else DEFAULT_PARAMS_DIR
    # Relative paths in .env are relative to the repo, not to wherever uvicorn was started.
    if not params_dir.is_absolute():
        params_dir = REPO_ROOT / params_dir
    return Settings(params_dir=params_dir)
