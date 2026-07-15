# CodeX Rate on Rear Screen

Standalone Android companion and rear-display surfaces for viewing ChatGPT
Codex quota on an OuterView-compatible device.

This repository is intentionally separate from the main OuterView app. It
contains:

- `codex-quota-companion/` - OAuth companion app, Launcher widgets, settings,
  background refresh, and display-only content provider.
- `demo/codex-quota-rear-card/` - Smart Assistant rear card package.
- `demo/codex-quota-rear-wallpaper/` - rear-screen Wallpaper package.
- `docs/` - quota research and surface integration notes.

## Build

```powershell
.\gradlew.bat :codex-quota-companion:testDebugUnitTest
.\gradlew.bat :codex-quota-companion:assembleDebug
```

Build the rear-display packages with:

```powershell
python demo/codex-quota-rear-card/build_card.py
.\demo\codex-quota-rear-wallpaper\build_wallpaper.ps1
```

The Android app uses ChatGPT OAuth Authorization Code + PKCE. Tokens remain in
the companion app and are never exposed through the rear-display provider.
OpenAI can change or revoke the undocumented usage endpoint at any time.

## Release artifacts

The GitHub Release includes the debug APK, Smart Assistant card ZIP, and rear
Wallpaper MRC package. Install the companion app first, authorize it, then
import the desired rear-display surface.
