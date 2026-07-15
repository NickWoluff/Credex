# OuterView Quota Rear Card

This Smart Assistant rear-screen card reads only display fields from the OuterView companion provider:

`content://org.orynnx.codexquota/quota`

Build the import ZIP with `python -X utf8 demo/codex-quota-rear-card/build_card.py`, then import it in OuterView. Authorize the companion app before enabling the card.

The card actively requests a provider refresh on first render, every rear-screen resume, once per minute, and when the full-screen transparent touch target is tapped. The companion caps upstream network requests to one per minute. The footer always shows the last successful update time when one exists; it does not display a vague "update needed" placeholder.

The layout covers all four provider states: both windows, Weekly-only, 5-hour-only, and no window. A single available window is promoted to the large hero layout without leaving a placeholder.

The visual system reserves the physical camera area, Xiaomi's top-right time overlay, and the bottom gesture region. The development-only `design-preview.png` shows every state and is not included in the import ZIP.

It does not run Codex on Android and cannot access the OAuth token.
