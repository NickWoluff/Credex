from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
FONT = Path("C:/Windows/Fonts/segoeui.ttf")
FONT_SEMI = Path("C:/Windows/Fonts/seguisb.ttf")


def font(size: int, semibold: bool = False):
    return ImageFont.truetype(str(FONT_SEMI if semibold else FONT), size)


def text(draw, xy, value, size, fill, semibold=False, anchor=None):
    draw.text(xy, value, font=font(size, semibold), fill=fill, anchor=anchor)


def progress(draw, value, y, color, height=6):
    draw.rounded_rectangle((204, y, 444, y + height), height / 2, fill="#30302d")
    width = 240 * max(0, min(100, value)) / 100
    if width:
        draw.rounded_rectangle((204, y, 204 + width, y + height), height / 2, fill=color)


def render(state: str):
    image = Image.new("RGB", (480, 304), "#0d0d0c")
    d = ImageDraw.Draw(image)
    # Physical camera occlusion guide only; not part of the MAML artwork.
    d.ellipse((27, 31, 145, 149), fill="#070707", outline="#242424", width=2)
    d.ellipse((38, 168, 134, 264), fill="#070707", outline="#242424", width=2)

    d.rounded_rectangle((183, 15, 465, 289), 29, fill="#292926")
    d.rounded_rectangle((184, 16, 464, 288), 28, fill="#171715")
    d.ellipse((196, 33, 212, 49), fill="#f4f4ef")
    d.ellipse((200, 37, 208, 45), fill="#171715")
    d.ellipse((207, 34, 211, 38), fill="#10a37f")
    text(d, (218, 29), "OuterView", 15, "#f4f4ef", True)
    text(d, (218, 48), "CODEX USAGE", 9, "#7c7c76")

    # Host-owned time overlay guide.
    d.rounded_rectangle((386, 22, 455, 51), 15, fill="#30302d")
    text(d, (420, 36), "22:00", 13, "#b0b0a8", semibold=True, anchor="mm")

    if state == "both":
        text(d, (204, 73), "5-hour", 12, "#b0b0a8")
        text(d, (444, 62), "72%", 32, "#f4f4ef", True, "ra")
        progress(d, 72, 105, "#f4f4ef")
        text(d, (204, 119), "Resets 07-14 02:00", 10, "#7c7c76")
        d.rectangle((204, 144, 444, 145), fill="#292926")
        text(d, (204, 164), "Weekly", 12, "#b0b0a8")
        text(d, (444, 153), "41%", 32, "#f4f4ef", True, "ra")
        progress(d, 41, 196, "#f4f4ef")
        text(d, (204, 210), "Resets 07-20 09:00", 10, "#7c7c76")
    elif state in ("weekly", "five"):
        label = "Weekly remaining" if state == "weekly" else "5-hour remaining"
        value = 64 if state == "weekly" else 28
        color = "#f4f4ef" if value >= 35 else "#c77a10"
        text(d, (204, 80), label, 12, "#b0b0a8")
        text(d, (204, 100), f"{value}%", 60, "#f4f4ef", True)
        progress(d, value, 176, color, 8)
        text(d, (204, 195), "Resets 07-20 09:00", 11, "#7c7c76")
    else:
        d.ellipse((198, 91, 234, 127), fill="#30302d")
        d.ellipse((205, 98, 227, 120), fill="#171715")
        d.ellipse((224, 95, 230, 101), fill="#b0b0a8")
        text(d, (204, 143), "Connect companion", 21, "#f4f4ef", True)
        text(d, (204, 174), "Open OuterView Quota to continue", 11, "#7c7c76")

    d.ellipse((204, 259, 210, 265), fill="#10a37f" if state != "empty" else "#7c7c76")
    text(d, (216, 254), "Last update 21:57" if state != "empty" else "Not connected", 10, "#7c7c76")
    text(d, (444, 254), "TAP TO REFRESH", 9, "#7c7c76", anchor="ra")
    return image


canvas = Image.new("RGB", (984, 660), "#e8e8e3")
draw = ImageDraw.Draw(canvas)
for index, (name, label) in enumerate((("both", "Two windows"), ("weekly", "Weekly only"), ("five", "5-hour only"), ("empty", "Disconnected"))):
    x = 8 + (index % 2) * 492
    y = 8 + (index // 2) * 326
    canvas.paste(render(name), (x, y))
    text(draw, (x + 8, y + 308), label, 11, "#363633", True)
canvas.save(ROOT / "design-preview.png")
print(ROOT / "design-preview.png")
