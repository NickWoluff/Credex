# OuterView Balance / Widget Studio source

This folder is the source layout for a Xiaomi desktop MAML gadget. The
runtime data comes from the companion app's desktop surface:

`content://org.orynnx.codexquota/quota/desktop`

The source is kept as a hand-written `content/manifest.xml` so it can be
opened and previewed in Xiaomi Widget Studio. The checked-in `.mtz` is a
theme/widget package, not an Android AppWidget registration. On a supported
MIUI/HyperOS build, import it into Widget Studio or apply it through the
system theme/widget workflow, then add the 4x2 card from the desktop widget
picker.

The native Android AppWidget remains the reliable app-based desktop path.
