# ВОЛНА TV Installer

Portable Windows x64 app for installing the IPTV Samsung TV package (`.wgt`) on a Samsung Smart TV in Developer Mode.

## User flow

1. Enter the TV IP and SDB port (`26101` by default).
2. The app downloads Node.js LTS and Samsung's official `tizen-agent-skills` CLI, then prepares the command-line Tizen SDK and Samsung TV Extension. It does not install or launch the graphical Tizen Studio IDE.
3. Connect to the TV through Samsung's remote-device/SDB flow and read its DUID.
4. Create a Samsung Author Certificate and a Distributor Certificate bound to that DUID. Samsung Account authentication opens in the system browser.
5. Download `iptv-samsung.wgt` from the latest `volnalabs/IPTV` GitHub Release.
6. Re-sign the WGT with the user's Samsung certificate profile, verify both Samsung signature files, then install it with Tizen Core CLI.

## Build

Requires Windows and .NET 10 SDK:

```powershell
dotnet publish tools/TVInstaller/TVInstaller.csproj `
  --configuration Release `
  --runtime win-x64 `
  --self-contained true `
  -p:PublishSingleFile=true `
  -p:IncludeNativeLibrariesForSelfExtract=true `
  --output artifacts/TVInstaller
```

The CI workflow publishes `Volna-TV-Installer.exe`.

## Runtime downloads

The app downloads these components on first use:

- Current Node.js LTS Windows x64 ZIP from `nodejs.org`, verifying the published SHA-256 checksum.
- The newest `tizen-sdk-skills-vX.Y.Z` tag from the official `Samsung/tizen-agent-skills` GitHub repository. The app installs npm dependencies and builds the official `tizen-sdk` CLI locally.
- The official Tizen SDK baseline and Samsung TV Extension through the Samsung CLI installer.

The SDK install can require approximately 15 GB of free disk space and may take a while. No Tizen Studio GUI is installed. The Samsung CLI and SDK are stored under the current Windows user's Local AppData and home directory.

## Security and privacy

- Samsung Account authentication is handled by Samsung's official certificate tooling in the system browser. The app does not ask for, store, or send the Samsung Account password.
- The certificate password is written to a temporary local file only for the duration of the certificate command and is deleted afterwards. It is not written to the app log or a settings file.
- Node.js downloads are checked against the SHA-256 checksum published by nodejs.org.
- Samsung certificate files remain on the user's computer. A Distributor Certificate is bound to the connected TV's DUID and is not portable to other TVs.
- The downloaded unsigned WGT remains untouched. The signed WGT is saved separately under `%LOCALAPPDATA%\Volna\TVInstaller\downloads`.
- The app downloads only `iptv-samsung.wgt` from the latest public IPTV release.

## Requirements

- Windows 10/11 x64.
- Internet access for official tool downloads and Samsung Account sign-in.
- Samsung TV with Developer Mode enabled, configured with the host PC IP, and restarted after enabling Developer Mode.
- TV and PC on the same local network, with SDB port `26101` reachable.
- Approximately 15 GB free disk space for a first-time SDK setup.

## Current limitations

- First release targets Windows x64 only.
- Samsung's certificate flow requires a real Samsung Account login in the browser.
- The app has not yet been validated against every Tizen/TV model generation. Test on a supported TV before considering the installer production-ready.
- Samsung's Developer Mode and network restrictions still apply; the app cannot bypass them.
