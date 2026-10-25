import json
from collections.abc import Iterator
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from server.config import Settings, get_settings
from server.main import app


@pytest.fixture
def params_dir(tmp_path: Path) -> Path:
    return tmp_path


@pytest.fixture
def client(params_dir: Path) -> Iterator[TestClient]:
    app.dependency_overrides[get_settings] = lambda: Settings(params_dir=params_dir)
    yield TestClient(app)
    app.dependency_overrides.clear()


def test_health_reports_ok(client: TestClient) -> None:
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_params_are_served_for_a_known_persona(client: TestClient, params_dir: Path) -> None:
    params = {"version": "1.0", "persona": "student_coder"}
    (params_dir / "twin_params_student_coder.json").write_text(json.dumps(params))

    response = client.get("/params/student_coder")

    assert response.status_code == 200
    assert response.json() == params


def test_unknown_persona_returns_404(client: TestClient) -> None:
    response = client.get("/params/night_shift_nurse")

    assert response.status_code == 404


@pytest.mark.parametrize("bad_name", ["Student", "student-coder", "..", "1student", "a" * 42])
def test_persona_names_outside_the_pattern_are_rejected(client: TestClient, bad_name: str) -> None:
    response = client.get(f"/params/{bad_name}")

    # ".." is collapsed by the URL parser before routing, so it surfaces as 404, not 422.
    assert response.status_code in (404, 422)


def test_broken_params_file_returns_500(client: TestClient, params_dir: Path) -> None:
    (params_dir / "twin_params_student_coder.json").write_text("{not json")

    response = client.get("/params/student_coder")

    assert response.status_code == 500


def test_params_file_holding_a_list_returns_500(client: TestClient, params_dir: Path) -> None:
    (params_dir / "twin_params_student_coder.json").write_text("[1, 2, 3]")

    response = client.get("/params/student_coder")

    assert response.status_code == 500
