#!/usr/bin/env python3
"""Copy the local Apple team setting into an ignored Xcode configuration file."""

from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]
PROPERTIES = ROOT / "local.properties"
OUTPUT = ROOT / "iosApp" / "LocalSigning.xcconfig"


def read_property(name: str) -> str:
    if not PROPERTIES.exists():
        return ""
    for line in PROPERTIES.read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.lstrip().startswith(("#", "!")):
            continue
        key, separator, value = line.partition("=")
        if separator and key.strip() == name:
            return value.strip()
    return ""


team_id = read_property("apple.team.id")
if team_id and not re.fullmatch(r"[A-Z0-9]{10}", team_id):
    raise SystemExit("apple.team.id must be a 10-character Apple Developer Team ID")

OUTPUT.write_text(f"PROMPTER_DEVELOPMENT_TEAM = {team_id}\n", encoding="utf-8")
print(f"Updated {OUTPUT.relative_to(ROOT)}")
