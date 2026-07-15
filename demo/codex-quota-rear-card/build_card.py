from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile

root = Path(__file__).resolve().parent
output = root / "Codex-Quota-Rear-Card.zip"
with ZipFile(output, "w", compression=ZIP_DEFLATED, compresslevel=9) as archive:
    for name in ("manifest.xml", "reareye-card.json"):
        archive.write(root / name, name)
print(output)
