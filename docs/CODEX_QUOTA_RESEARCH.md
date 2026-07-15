# Codex quota research

## Findings (2026-07-14)

OpenAI's Codex app-server exposes `account/rateLimits/read`. Its documented response contains rate-limit windows with usage percentage, duration, and reset time. A response can contain one or two windows.

`primary_window` and `secondary_window` describe ordering, not duration. A weekly-only account can return its seven-day window as `primary_window`. Consumers must classify every returned window by its declared duration: `limit_window_seconds=18000` (or `window_minutes=300`) is the five-hour window, while `limit_window_seconds=604800` (or `window_minutes=10080`) is the weekly window. A lone window with no recognized duration is left unlabelled rather than fabricated as 5-hour.

There is no documented public Android SDK or stable REST API for a ChatGPT subscription's Codex quota. A platform API key has separate platform rate limits and cannot retrieve a ChatGPT plan quota.

## Third-party implementations observed

| Project | Retrieval path | Notes |
| --- | --- | --- |
| [Codex Quota Monitor (VS Code)](https://marketplace.visualstudio.com/items?itemName=Tricker.codex-quota-monitor) | Codex app-server; local session/log fallback | Reads the 5-hour and weekly windows without reading `auth.json`. |
| [CodexBar](https://github.com/steipete/CodexBar) | OAuth request to `https://chatgpt.com/backend-api/wham/usage`; Codex app-server fallback | Exposes session/weekly windows and reset countdowns through an open-source provider implementation. |
| [Codex Quota Monitor (Chrome)](https://github.com/codexquotamonitor/codex-quota-monitor) | Authenticated ChatGPT browser session | Its open source states that it requests `backend-api/wham/usage` and displays 5-hour, weekly, credits, and reset data. |

## Chosen implementation

This project deliberately avoids a computer bridge. The Android companion uses an OAuth Authorization Code + PKCE login and calls the same `wham/usage` endpoint directly. This is functional research code, not an official OpenAI mobile integration. It applies a one-minute minimum refresh interval and exposes a token-free ContentProvider to the Xiaomi rear-screen card.

## Sources

- [OpenAI Codex app-server protocol](https://github.com/openai/codex/blob/main/codex-rs/app-server/README.md): `account/rateLimits/read`, `usedPercent`, `windowDurationMins`, and `resetsAt`.
- [OpenAI Codex issue #6008](https://github.com/openai/codex/issues/6008): captured rate-limit payloads show the declared 18,000-second and 604,800-second window durations.
- [CodexBar](https://github.com/steipete/CodexBar) for the reusable OAuth/CLI provider pattern.
- [VS Code Codex Quota Monitor](https://marketplace.visualstudio.com/items?itemName=Tricker.codex-quota-monitor) for app-server and local-log fallback behavior.
- [Browser Codex Quota Monitor](https://github.com/codexquotamonitor/codex-quota-monitor) for direct authenticated `wham/usage` retrieval.
