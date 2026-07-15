# OuterView Quota for Codex

The app can use a user-controlled Android foreground service with a persistent low-priority notification to refresh quota data every 15 minutes. In **Settings > Sync**, **Continuous sync** and **Persistent notification** are separate controls: turning off the latter stops the foreground service and keeps the quieter persisted JobScheduler refresh path, without a notification prompt. Android or OEM battery policy may still defer that quiet work, so the settings page includes a shortcut to the app's system background settings.

Version 0.9 adds the optional quiet-sync control to the standard Android Launcher widget, app, and rear-display surfaces. The compact two-cell form keeps one quota window legible; widening it reveals the 5-hour window only when OpenAI actually returns one. It uses an Android adaptive icon with a GPT Image 2 color layer plus an Android 13 monochrome layer, and uses standard Material refresh artwork. It supports day/night resources, one-tap refresh, cached and authorization states, and direct entry into the app. JobScheduler refreshes continue to push new state to every placed widget even when the foreground notification is disabled, while the widget's system update interval remains disabled rather than implying a platform-unsupported 15-minute cadence.

When a reset timestamp is available, the app and medium widget show both the absolute time and a relative countdown. The compact widget keeps only the one-line relative countdown when space permits, while preserving the last-successful-update status.

After sign-in, add it from **Settings > Home screen > Add quota widget**. If the Launcher does not support in-app pinning, long-press the front home screen and select **OuterView Quota** from the widget picker. The development preview is `design/widget-design-preview.png`.

The visual system follows OpenAI's published principles of geometric precision, rounded warmth, and generous spacing. OuterView remains the primary brand; the app does not reproduce the OpenAI logo or imply affiliation.

If OpenAI returns only a weekly window, the app, rear card, and Launcher widget hide the unavailable 5-hour quota instead of displaying a placeholder.

Standalone Android helper for Codex quota surfaces. It does not run Codex on Android.

Transient refresh failures preserve the last known-good quota values. The UI labels that snapshot as cached instead of replacing it with an empty state.

## Data flow

1. The user completes a ChatGPT OAuth Authorization Code + PKCE flow in the system browser.
2. The app encrypts the resulting access token with an Android Keystore AES key.
3. It requests `GET https://chatgpt.com/backend-api/wham/usage` at most once per minute.
4. The rear-screen MAML runtime reads only display fields through `content://org.orynnx.codexquota/quota`.
5. Repository refreshes publish the same display-only state to placed Launcher widgets via standard `RemoteViews`.

The provider never returns the OAuth token, refresh token, account identifier, or raw API response.

## Build

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :codex-quota-companion:assembleDebug
```

Debug APK: `codex-quota-companion/build/outputs/apk/debug/codex-quota-companion-debug.apk`.

## Compatibility warning

The `wham/usage` endpoint and the Codex OAuth client are not a stable mobile-app API. OpenAI can alter or revoke them, and reauthorization may then be required. This helper has no API-key mode because OpenAI Platform API keys do not represent a ChatGPT plan's Codex quota.
