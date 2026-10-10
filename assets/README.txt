Place the application's icons in this folder
============================================

`tools\package.ps1` looks for `assets\icon.ico` and, when it finds it, uses that
picture for the packaged program and for the installer. Without it, jpackage
supplies its own default icon and everything still works.

To add your own icon
--------------------

1. Draw or export the picture as PNG, at least 256 x 256 pixels.
2. Convert it to a Windows `.ico` file containing at least these sizes:
   16, 32, 48, 64, 128 and 256 pixels. Any icon editor can do this; on Windows,
   a quick way is to use an online converter or ImageMagick:

       magick icon.png -define icon:auto-resize=256,128,64,48,32,16 icon.ico

3. Save it here as:

       assets\icon.ico

4. Run `package.bat` again. The packaging step prints

       icon: ...\assets\icon.ico

   when it picks the file up.

Notes
-----

- Only `.ico` is used, because the target is a Windows application. If the project
  is ever packaged for another system, a PNG of at least 512 x 512 would be added
  here as well.
- The icon is optional. Leaving this folder empty is perfectly fine.
