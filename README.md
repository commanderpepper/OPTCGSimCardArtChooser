# OPTCGSim Card Art Chooser

A desktop app for Windows and macOS that lets you choose which art OPTCGSim shows for
each card: one of the card's alternate arts, or an image of your own.

- Browse every card, with filters for set, color, type, traits, alternate art and custom images.
- Pick an alternate art for a card, or add your own image (choose a file or drag it in).
- Restore the sim's original art at any time, for one card or all of them.
- After a sim update replaces the art, reapply all your choices in one click.

Card details and alternate art come from [OPTCGAPI](https://optcgapi.com).

## Install

Download the installer for your computer from the [latest release](../../releases/latest):

| Computer | File |
|---|---|
| Windows | `OPTCGSimCardArtChooser-<version>.msi` |
| Mac with Apple Silicon (M1 and later) | `OPTCGSimCardArtChooser-<version>-arm64.dmg` |
| Mac with an Intel processor | `OPTCGSimCardArtChooser-<version>-x64.dmg` |

The installers aren't code-signed, so your computer will warn you the first time:

- **Windows:** if "Windows protected your PC" appears, click **More info → Run anyway**.
- **macOS:** open the `.dmg` and drag the app to Applications. If macOS says it can't be opened, go to
  **System Settings → Privacy & Security** and click **Open Anyway** next to the app's name.

To update, install the newer version over the old one. Your choices and backups are kept.

## Using it

1. On first launch, point the app at your OPTCGSim folder. It tries to find it for you.
2. Click **Browse cards**, open a card, and choose the art you want. OPTCGSim shows it the next time it loads the
   card.
3. When an OPTCGSim update brings back the stock art, click **Reapply modified cards** on the home screen.

### Backups and restoring

Before the app changes a card for the first time, it backs up the sim's original image. Backups are never
overwritten. To put the original back, choose **Sim original** on the card's page, or use **Restore originals**
in Browse cards to restore several at once.

The app keeps its backups, downloaded art and your choices outside the sim folder, so sim updates can't touch them:

| OS | Folder |
|---|---|
| Windows | `%LOCALAPPDATA%\OPTCGSimCardArtChooser` |
| macOS | `~/Library/Application Support/OPTCGSimCardArtChooser` (downloads in `~/Library/Caches/OPTCGSimCardArtChooser`) |

### Uninstalling

Uninstalling removes the app but leaves the sim's card images as they are. To go back to the stock art, use
**Restore originals** before you uninstall. Then:

- **Windows:** Settings → Apps → Installed apps → OPTCGSimCardArtChooser → Uninstall.
- **macOS:** drag the app from Applications to the Trash.

To remove everything, also delete the app's folder listed above.

## Building from source

Requires JDK 21 (Gradle downloads it if it's missing).

| Task | Command |
|---|---|
| Run the app | `./gradlew :desktopApp:run` |
| Run with hot reload | `./gradlew :desktopApp:hotRun --auto` |
| Run all tests | `./gradlew check` |
| Build an installer for this OS | `./gradlew :desktopApp:packageDistributionForCurrentOS` |

Running from Gradle starts the debug version, titled "(Debug)". It keeps its own data folder
(`OPTCGSimCardArtChooser-Debug`) and settings, so it can't disturb an installed copy. On first launch, point it at
a copy of your OPTCGSim folder so test runs don't change your real sim.

The project has three modules: `catalog` (card data and image conversion), `shared` (the UI and app logic) and
`desktopApp` (the entry point and installer settings).

Releases are built by GitHub Actions: pushing a tag like `v1.2.3` builds all three installers and attaches them to
a draft release. See [.github/workflows/release.yml](.github/workflows/release.yml).

## License

[MIT](LICENSE)

## Disclaimer

OPTCGSim Card Art Chooser is an unofficial fan-made app and is not affiliated with, sponsored by, or endorsed by
Bandai, Eiichirō Oda, Shueisha, or Toei Animation. One Piece and all related content are © Eiichirō Oda/Shueisha,
Toei Animation.

It is also an independent project and is not affiliated with, sponsored by, or endorsed by the makers of OPTCGSim or
OPTCGAPI.

The MIT license covers this app's source code only. It grants no rights to One Piece card art or other content.
