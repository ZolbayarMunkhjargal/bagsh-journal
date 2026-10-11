The application's icon
======================

`icon.ico` and `icon.png` in this folder are **generated** - do not edit them by
hand. They are drawn by:

    python tools\make_icon.py

The design is a teal notebook cover with a cream page, ruled lines, binder rings
down the spine and a small red bookmark ribbon, using the same palette as the
application window. Every measurement is a fraction of the icon size, so the same
code draws all seven sizes Windows asks for - 16, 24, 32, 48, 64, 128 and 256 -
and the small ones stay legible rather than turning to mush.

`tools\package.ps1` picks `icon.ico` up automatically and uses it for the
program, the shortcut and (if an installer is built) the installer. It prints

    icon: ...\assets\icon.ico

when it finds it. Without the file, jpackage supplies a default icon and
everything still works.

Changing the design
-------------------

Edit `tools\make_icon.py` and run it again:

    python tools\make_icon.py

Two things are worth keeping in mind:

- **Draw at large sizes, then look at 16 and 32 pixels.** Details that read
  nicely at 256 disappear or smear at 16. That is why the ruled lines are only
  drawn at 32 pixels and above.
- **Keep large, high-contrast shapes.** The ribbon started out much wider and
  ran down into the text, which made it look like a red smear at small sizes; it
  now sits flush with the page edge and stops above the first ruled line.

Using a different icon
----------------------

Replace `assets\icon.ico` with any `.ico` containing at least 16, 32, 48, 64, 128
and 256 pixel images, then run `package.bat`. ImageMagick can convert a PNG:

    magick icon.png -define icon:auto-resize=256,128,64,48,32,16 icon.ico

Only `.ico` is used, because the target is a Windows application. `icon.png` is
written alongside it for the README and for anyone packaging on another system.
