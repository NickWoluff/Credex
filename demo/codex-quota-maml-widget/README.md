# OuterView Balance MAML Widget

This is a Xiaomi Widget Studio/MTZ source package for a 4x2 desktop MAML
gadget. It reads the credential-free desktop surface:

`content://org.orynnx.codexquota/quota/desktop`

The companion app controls which services are enabled for this surface and the
display order. Build the package with:

```powershell
.\demo\codex-quota-maml-widget\build_widget.ps1
```

The build produces `OuterView-Balance-MAML-Widget.mtz`. A `.mtz` is the Xiaomi
theme/widget container; it is not something an ordinary Android app can
silently register with the launcher. Import/apply it using Xiaomi's Widget
Studio or supported theme workflow, then add the widget from the desktop
picker. If the phone does not expose third-party MAML gadgets, use the native
Android widget from OuterView Quota instead.

The source manifest intentionally uses the historical desktop gadget root
`Gadget` inside `content/manifest.xml`; the original `manifest.xml` at this
directory root remains the readable source copy with the same layout and
bindings.
