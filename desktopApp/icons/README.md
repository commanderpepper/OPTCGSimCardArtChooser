# App icons

| File | Used for |
|---|---|
| `app.ico` | Windows installer, `.exe`, Start-menu and desktop shortcuts (16–256 px) |
| `app.icns` | macOS app bundle (16–1024 px) |
| `app.png` | Linux packages (1024 px) |
| `../src/main/resources/icon.png` | Window title bar and taskbar while the app runs (256 px) |

All four are generated from one picture by `make_icons.py`: it crops the picture to a square,
scales it to a 1024 px master, rounds the corners, and writes every format.

To change the icon, run from the repository root (needs Python with Pillow):

```
python desktopApp/icons/make_icons.py path/to/picture.png .
```

A square picture of at least 1024×1024 gives the sharpest results; smaller pictures are enlarged
and look soft at the biggest macOS sizes.
