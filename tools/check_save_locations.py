"""
Finds out which folders this computer lets KinderJournal save into, and why not.

Run this by double-clicking it, or from a terminal:

    python tools\\check_save_locations.py

It writes a few bytes into each folder in turn, reports what happened, and writes
a file called save-check.txt next to itself with the details. Nothing is left
behind: every test file is deleted again.
"""

from __future__ import annotations

import os
import sys
import tempfile
import traceback
from datetime import datetime
from pathlib import Path

HOME = Path(os.path.expanduser("~"))
APPDATA = Path(os.environ.get("APPDATA", HOME / "AppData/Roaming"))

CANDIDATES = [
    ("Documents", HOME / "Documents"),
    ("Desktop", HOME / "Desktop"),
    ("Downloads", HOME / "Downloads"),
    ("OneDrive", HOME / "OneDrive"),
    ("Pictures", HOME / "Pictures"),
    ("This is the app's own data folder", APPDATA / "KinderJournal"),
    ("The app's backups folder", APPDATA / "KinderJournal" / "backups"),
    ("The temporary folder", Path(tempfile.gettempdir())),
    ("C:\\KJ-reports (new folder at the top of C:)", Path("C:/KJ-reports")),
    ("Next to the try-out data", Path(__file__).resolve().parent.parent / "tryout-data"),
]

lines: list[str] = []


def say(text: str = "") -> None:
    print(text)
    lines.append(text)


def why(exc: BaseException) -> str:
    """The most useful part of a Windows error, including its numeric code."""
    parts = [type(exc).__name__]
    winerror = getattr(exc, "winerror", None)
    if winerror is not None:
        parts.append(f"winerror={winerror}")
    text = getattr(exc, "strerror", None) or str(exc)
    if text:
        parts.append(text.strip())
    return "  ".join(parts)


def main() -> None:
    say("KinderJournal save-location check")
    say("=" * 34)
    say(f"run at      : {datetime.now():%Y-%m-%d %H:%M:%S}")
    say(f"running as  : {os.environ.get('USERNAME', '?')}")
    say(f"python      : {sys.executable}")
    say()

    working: list[str] = []
    failing: list[tuple[str, str]] = []

    for label, folder in CANDIDATES:
        target = folder / "KinderJournal-save-check.txt"
        try:
            folder.mkdir(parents=True, exist_ok=True)
            target.write_text("test", encoding="utf-8")
            with target.open("a", encoding="utf-8") as handle:   # reopen, as a save would
                handle.write("more")
            target.unlink()
            say(f"  OK      {label}")
            say(f"          {folder}")
            working.append(label)
        except Exception as exc:                                  # noqa: BLE001
            say(f"  FAILED  {label}")
            say(f"          {folder}")
            say(f"          {why(exc)}")
            failing.append((label, why(exc)))
        say()

    say("-" * 34)
    say(f"worked : {len(working)}")
    for label in working:
        say(f"   {label}")
    say(f"failed : {len(failing)}")
    for label, reason in failing:
        say(f"   {label} - {reason}")

    report = Path(__file__).resolve().parent.parent / "save-check.txt"
    try:
        report.write_text("\n".join(lines) + "\n", encoding="utf-8")
        print()
        print(f"Details written to {report}")
    except Exception as exc:                                       # noqa: BLE001
        print(f"Could not write the report: {why(exc)}")

    # Keep the window open when double-clicked rather than run from a terminal.
    if sys.stdout.isatty() or os.environ.get("PROMPT"):
        input("\nPress Enter to close...")


if __name__ == "__main__":
    try:
        main()
    except Exception:                                              # noqa: BLE001
        traceback.print_exc()
        input("\nPress Enter to close...")
