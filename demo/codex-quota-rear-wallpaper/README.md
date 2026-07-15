# OuterView Codex Quota Rear Wallpaper

This is a rear-screen **Wallpaper** package for OuterView. It is not a resized
Smart Assistant card: time is the primary visual, quota is secondary ambient
information, and the full rear canvas is used without a card container.

## Build and install

```powershell
.\build_wallpaper.ps1
```

Import `OuterView-Codex-Quota-Wallpaper.mrc` from OuterView's **Wallpaper**
page, then apply it there or from Xiaomi's rear-screen wallpaper list. Do not
import it from the Assistant page.

The wallpaper reads display-only fields from
`content://org.orynnx.codexquota/quota`. The companion app must be installed
and authorized. It requests a provider refresh on init, resume, once per
minute, and when the root-level transparent touch target is tapped. The footer
shows the last update time when one exists.

## Display behavior

- Reference canvas: `480 x 304`, aspect-safe scaled and centered.
- Camera-safe rule: no meaningful content is placed left of `x = 183`.
- Gesture-safe rule: no content or hit target extends into `y >= 288`.
- Weekly and 5-hour windows render when returned by the provider.
- If only Weekly is returned, the 5-hour reading is completely absent.
- The clock and quota readings use larger type for the rear display; the date
  uses explicit English month abbreviations (for example, `JUL 14`) instead
  of following the phone locale.
- Progress tracks use a raised teal-gray contrast color, with green/amber/red
  fills for healthy, warning, and critical remaining quota.
- Disconnected and no-window states remain visually quiet but actionable.

`description.xml` and `metadata.mrm` mirror the optional metadata structure
found in a real Xiaomi/OuterView MRC. OuterView's package validator only
requires a safe top-level `manifest.xml` or `config.xml`; the manifest remains
the authoritative runtime descriptor.

OuterView and this package are independent projects and are not affiliated
with or endorsed by OpenAI.
