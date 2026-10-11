"""
Generates the KinderJournal application icon.

Run once from the project folder; the result - assets/icon.ico - is then picked
up automatically by tools/package.ps1 for the program, the shortcut and (if a
Windows installer is ever built) the installer itself.

The design has to survive being drawn at 16 pixels in a taskbar, so it is built
from large, high-contrast shapes only: a teal notebook cover, a cream page with
ruled lines, binder rings down the spine, and a small bookmark ribbon tucked into
the top corner. The palette is the one the interface uses.

    python tools/make_icon.py
"""

from __future__ import annotations

import sys
from pathlib import Path

try:
    from PIL import Image, ImageDraw
except ImportError:  # pragma: no cover - only hit on a machine without Pillow
    sys.exit("Pillow is required to draw the icon (pip install Pillow).")

# Sizes Windows asks for: small ones for list views and the taskbar, large ones
# for a desktop shortcut and the Explorer preview pane.
SIZES = [256, 128, 64, 48, 32, 24, 16]

# Palette from the application stylesheet, so the icon matches the window.
TEAL_DARK = (12, 46, 48)
TEAL = (15, 118, 110)
RING_DARK = (10, 74, 70)
PAGE = (252, 252, 250)
RULE = (183, 196, 202)
RIBBON = (198, 40, 40)


def rounded(draw: ImageDraw.ImageDraw, box, radius: int, fill) -> None:
    """Draws a filled rounded rectangle."""
    draw.rounded_rectangle(box, radius=radius, fill=fill)


def render(size: int) -> Image.Image:
    """Draws the icon at one size, scaling every measurement from the size."""
    # Draw at 4x and downsample: smooth curves without any antialias code.
    scale = 4
    s = size * scale
    image = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)

    def u(value: float) -> float:
        """A measurement given as a fraction of the icon."""
        return value * s

    # --- cover, with a soft vertical gradient, rounded by a mask
    for y in range(s):
        blend = y / max(1, s - 1)
        colour = tuple(int(TEAL[i] + (TEAL_DARK[i] - TEAL[i]) * blend) for i in range(3))
        draw.line([(0, y), (s, y)], fill=colour + (255,))
    mask = Image.new("L", (s, s), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        [0, 0, s - 1, s - 1], radius=int(u(0.16)), fill=255
    )
    image.putalpha(mask)

    # --- the page: most of the cover, leaving a spine on the left
    page_left = u(0.26)
    page_right = u(0.86)
    page_top = u(0.12)
    page_bottom = u(0.88)
    rounded(draw, [page_left, page_top, page_right, page_bottom], int(u(0.04)), PAGE + (255,))

    # --- the bookmark ribbon, flush with the right-hand page edge. It is placed
    #     first so the ruled lines below can stop short of it.
    ribbon_w = u(0.075)
    ribbon_x = page_right - ribbon_w
    ribbon_len = u(0.175)

    # --- ruled lines across the page, the first one ending before the ribbon
    if size >= 32:
        line_left = page_left + u(0.07)
        line_right = page_right - u(0.07)
        for i in range(5):
            y = page_top + u(0.155) + i * u(0.145)
            if y + u(0.03) > page_bottom - u(0.05):
                break
            right = line_right if i > 0 else min(line_right, ribbon_x - u(0.035))
            draw.rounded_rectangle(
                [line_left, y, right, y + u(0.028)],
                radius=int(u(0.014)),
                fill=RULE + (255,),
            )

    # --- binder rings down the spine: a short bar with a ring on each end
    ring_x = u(0.13)
    ring_w = u(0.075)
    ring_h = u(0.035)
    for i in range(5):
        cy = page_top + u(0.10) + i * u(0.165)
        draw.rounded_rectangle(
            [ring_x - ring_w / 2, cy - ring_h / 2, ring_x + ring_w / 2, cy + ring_h / 2],
            radius=int(ring_h / 2),
            fill=RING_DARK + (255,),
        )
        r = u(0.045)
        draw.ellipse([ring_x - r, cy - r, ring_x + r, cy + r], fill=RING_DARK + (255,))
        # A bright centre keeps the rings visible against the dark spine at 16px.
        draw.ellipse(
            [ring_x - r * 0.5, cy - r * 0.5, ring_x + r * 0.5, cy + r * 0.5],
            fill=(226, 240, 238, 255),
        )

    # --- draw the ribbon over the page
    draw.rounded_rectangle(
        [ribbon_x, page_top, ribbon_x + ribbon_w, page_top + ribbon_len],
        radius=int(u(0.010)),
        fill=RIBBON + (255,),
    )
    draw.polygon(
        [
            (ribbon_x, page_top + ribbon_len),
            (ribbon_x + ribbon_w, page_top + ribbon_len),
            (ribbon_x + ribbon_w / 2, page_top + ribbon_len + u(0.065)),
        ],
        fill=RIBBON + (255,),
    )

    return image.resize((size, size), Image.LANCZOS)


def main() -> None:
    root = Path(__file__).resolve().parent.parent
    target = root / "assets" / "icon.ico"
    target.parent.mkdir(parents=True, exist_ok=True)

    frames = [render(size) for size in SIZES]
    frames[0].save(target, format="ICO", sizes=[(s, s) for s in SIZES])
    frames[1].save(target.with_suffix(".png"), format="PNG")

    print(f"Wrote {target} ({target.stat().st_size} bytes) with sizes {SIZES}")
    print(f"Wrote {target.with_suffix('.png')}")


if __name__ == "__main__":
    main()
