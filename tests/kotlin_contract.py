# Reads names straight out of TwinEngine.kt, so Python-side tests can check field names and enum
# values against the Kotlin contract without a Kotlin compiler.

import re
from pathlib import Path

KOTLIN_CONTRACT = (
    Path(__file__).resolve().parent.parent
    / "contracts"
    / "kotlin"
    / "com"
    / "pratibimb"
    / "twin"
    / "TwinEngine.kt"
)

# Comments are stripped first: they contain parentheses that would confuse the regexes below.
KOTLIN_SOURCE = re.sub(r"//[^\n]*", "", KOTLIN_CONTRACT.read_text(encoding="utf-8"))


def kotlin_fields(class_name: str) -> list[str]:
    match = re.search(rf"data class {class_name}\((.*?)\)", KOTLIN_SOURCE, re.DOTALL)
    assert match, f"data class {class_name} not found in TwinEngine.kt"
    return re.findall(r"val (\w+):", match.group(1))


def kotlin_enum(enum_name: str) -> list[str]:
    match = re.search(
        rf"enum class {enum_name}(?:\([^)]*\))?\s*\{{(.*?)\}}", KOTLIN_SOURCE, re.DOTALL
    )
    assert match, f"enum class {enum_name} not found in TwinEngine.kt"
    # A constant starts the block or follows a comma, on one line or many.
    return re.findall(r"(?:^|,)\s*([A-Z][A-Z0-9_]*)\b", match.group(1).strip())


def kotlin_category_keys() -> list[str]:
    return re.findall(r'[A-Z_]+\("([a-z_]+)"\)', KOTLIN_SOURCE)


def json_key(kotlin_name: str) -> str:
    return re.sub(r"([A-Z])", r"_\1", kotlin_name).lower()
