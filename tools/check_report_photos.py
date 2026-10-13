"""Checks that the photos in a report reach the Word and Excel files.

The HTML a report is rendered to carries photos as data URIs. Word and Excel cannot
hold those, so the writers embed the pictures as real image parts inside their
packages. This opens the written files with independent libraries and looks for the
pictures, which is the only check that proves they are really in there.

    python tools\\check_report_photos.py <folder>
"""

from __future__ import annotations

import os
import re
import sys
import zipfile
from pathlib import Path

import docx
import openpyxl

failures: list[str] = []


def fail(message: str) -> None:
    failures.append(message)
    print(f"  FAIL  {message}")


def ok(message: str) -> None:
    print(f"  ok    {message}")


def html_image_count(folder: Path, stem: str) -> int:
    """How many photos the report held, counted from its own HTML."""
    html = folder / f"{stem}.html"
    if not html.is_file():
        return -1
    return len(re.findall(r"data:image/", html.read_text(encoding="utf-8", errors="replace")))


def check_docx(path: Path, expected: int) -> None:
    if not path.is_file():
        fail(f"{path.name}: not written")
        return
    with zipfile.ZipFile(path) as archive:
        media = [n for n in archive.namelist() if n.startswith("word/media/")]
        sizes = [archive.getinfo(n).file_size for n in media]
        document = archive.read("word/document.xml").decode("utf-8", errors="replace")
        types = archive.read("[Content_Types].xml").decode("utf-8", errors="replace")
    if expected == 0:
        ok(f"{path.name}: no photos in this report, so none expected")
        return
    if len(media) != expected:
        fail(f"{path.name}: the report has {expected} photo(s) but the file carries "
             f"{len(media)}")
        return
    if any(size == 0 for size in sizes):
        fail(f"{path.name}: an embedded photo is empty")
        return
    # A picture in Word is a drawing that points at an image relationship.
    drawings = document.count("<w:drawing>")
    embeds = len(re.findall(r'<a:blip r:embed="', document))
    if drawings < expected:
        fail(f"{path.name}: {len(media)} image part(s) but only {drawings} drawing(s)")
        return
    if embeds < expected:
        fail(f"{path.name}: {len(media)} image part(s) but only {embeds} reference(s)")
        return
    if "image/png" not in types and "image/jpeg" not in types:
        fail(f"{path.name}: the package does not declare any image type")
        return
    # Finally, a real library has to accept the file.
    try:
        reopened = docx.Document(str(path))
    except Exception as exc:                       # noqa: BLE001
        fail(f"{path.name}: python-docx could not open it ({exc})")
        return
    paragraphs = len([p for p in reopened.paragraphs if p.text.strip()])
    ok(f"{path.name}: {len(media)} photo(s) embedded, {drawings} drawing(s), "
       f"{embeds} reference(s), {os.path.getsize(path):,} bytes, {paragraphs} paragraphs")


def check_xlsx(path: Path, expected: int) -> None:
    if not path.is_file():
        fail(f"{path.name}: not written")
        return
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        media = [n for n in names if n.startswith("xl/media/")]
        drawings = [n for n in names if "drawings/drawing" in n]
        sheets = sorted(n for n in names if n.startswith("xl/worksheets/sheet"))
        # The photos get a sheet of their own, so every sheet is checked for the
        # reference rather than only the first.
        references = [
            "<drawing " in archive.read(n).decode("utf-8", errors="replace")
            or "<drawing/>" in archive.read(n).decode("utf-8", errors="replace")
            for n in sheets
        ]
    if expected == 0:
        ok(f"{path.name}: no photos in this report, so none expected")
        return
    if len(media) != expected:
        fail(f"{path.name}: the report has {expected} photo(s) but the file carries "
             f"{len(media)}")
        return
    if not drawings:
        fail(f"{path.name}: {len(media)} image part(s) but no drawing to place them")
        return
    if not any(references):
        fail(f"{path.name}: no sheet references the drawing, so the photos would not show")
        return
    try:
        book = openpyxl.load_workbook(str(path))
    except Exception as exc:                       # noqa: BLE001
        fail(f"{path.name}: openpyxl could not open it ({exc})")
        return
    ok(f"{path.name}: {len(media)} photo(s), {len(drawings)} drawing part(s), "
       f"{book.sheetnames}, {os.path.getsize(path):,} bytes")


def main() -> None:
    folder = Path(sys.argv[1] if len(sys.argv) > 1 else "build/photo-report")
    if not folder.is_dir():
        raise SystemExit(f"No such folder: {folder}")

    print(f"Checking the reports in {folder}")
    print()
    print("What each report held")
    for html in sorted(folder.glob("*.html")):
        count = html_image_count(folder, html.stem)
        print(f"  {html.stem:22} {count} photo(s) in the HTML")

    print()
    print("What reached Word and Excel")
    for stem in sorted({p.stem for p in folder.glob("*.html")}):
        expected = html_image_count(folder, stem)
        docx_path = folder / f"{stem}.docx"
        xlsx_path = folder / f"{stem}.xlsx"
        if docx_path.is_file():
            check_docx(docx_path, expected)
        if xlsx_path.is_file():
            check_xlsx(xlsx_path, expected)

    print()
    if failures:
        print(f"{len(failures)} problem(s):")
        for message in failures:
            print(f"  - {message}")
        raise SystemExit(1)
    print("Photos are present in every Word and Excel file that should have them.")


if __name__ == "__main__":
    main()
