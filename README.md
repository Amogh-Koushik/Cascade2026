# Pratibimb

A personal attention twin for Android. During a focus session it predicts what each incoming
notification will cost your focus, silently holds the ones that pull hard but matter little,
and releases them at your next break. Every live decision is made on the phone.

CASCADE '26 · Theme 3: Attention Economy · PS 3.3: The Attention Digital Twin

## Repository

| Folder | Contents | Owner |
|---|---|---|
| `contracts/` | Shared interfaces and data formats: engine interface, params schema, draw bank | Amogh, Safal |
| `agents/` | LLM agents (persona, coach, red-team), laptop only | Amogh |
| `policy/` | Tuned policy constants | Amogh |
| `engine/` | `engine.py` reference engine and `TwinEngine.kt` port | Amogh, Safal |
| `data/`, `sim/`, `fit/`, `eval/`, `dashboard/` | Datasets, simulator, fitting, evaluation, proof dashboard | Safal |
| `server/` | FastAPI edge server on the laptop | Satvik |
| `app/` | Android app (Kotlin, Jetpack Compose) | Satvik, CSS |
| `pitch/` | Slides and demo material | CSS |

## Laptop setup

Requires [uv](https://docs.astral.sh/uv/).

```bash
uv sync
cp .env.example .env
uv run --env-file .env python -m agents.check_llm
uv run uvicorn server.main:app --host 0.0.0.0 --port 8000
```

Generated data (run once; the outputs are committed):

```bash
uv run --env-file .env python -m agents.persona_agent
uv run --env-file .env python -m agents.text_bank_agent
uv run --env-file .env python -m agents.redteam_agent
uv run python -m agents.redteam_runner --out eval/redteam_report.md
```

Checks:

```bash
uv run ruff check .
uv run ruff format --check .
uv run pytest
```

## Android app

Open `app/` in Android Studio, let Gradle sync, and run on a phone with Android 13 or newer.
On a sideloaded install, allow restricted settings (App info → ⋮ → Allow restricted settings)
before switching on notification access.

## Credits

Built with the help of AI coding assistants (Claude). Laptop-side agents use a local model
through Ollama or Google Gemini. Public datasets and libraries are credited here as they are
added.
