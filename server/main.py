# Laptop edge server. The phone never needs it to make a decision: it only pulls fresh
# parameters from here when the laptop is reachable, so new constants never need a rebuild.
#
# Run: uv run uvicorn server.main:app --host 0.0.0.0 --port 8000

import json
import logging
from typing import Annotated, Any

from fastapi import Depends, FastAPI, HTTPException, Path

from server.config import Settings, get_settings

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
logger = logging.getLogger(__name__)

# Persona names become file names, so only simple snake_case names are accepted.
# This also rules out path tricks such as "../" before any file is touched.
PERSONA_PATTERN = r"^[a-z][a-z0-9_]{0,40}$"

app = FastAPI(title="Pratibimb edge server", version="0.1.0")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/params/{persona}")
def get_params(
    persona: Annotated[str, Path(pattern=PERSONA_PATTERN)],
    settings: Annotated[Settings, Depends(get_settings)],
) -> dict[str, Any]:
    params_file = settings.params_dir / f"twin_params_{persona}.json"
    if not params_file.is_file():
        raise HTTPException(status_code=404, detail=f"No parameters for persona '{persona}'")

    try:
        params = json.loads(params_file.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        logger.error("Invalid JSON in %s: %s", params_file, error)
        raise HTTPException(
            status_code=500, detail=f"Parameters file for '{persona}' is not valid JSON"
        ) from error

    if not isinstance(params, dict):
        logger.error("Parameters file %s does not hold a JSON object", params_file)
        raise HTTPException(
            status_code=500, detail=f"Parameters file for '{persona}' is not a JSON object"
        )

    logger.info("Served parameters for persona %s", persona)
    return params
