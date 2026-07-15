from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parent
FONT = Path("C:/Windows/Fonts/msyh.ttc")
FONT_SEMIBOLD = Path("C:/Windows/Fonts/msyhbd.ttc")
FONT_LIGHT = Path("C:/Windows/Fonts/segoeuil.ttf")
SCALE = 2


def font(size: int, weight: str = "regular") -> ImageFont.FreeTypeFont:
    path = {"semibold": FONT_SEMIBOLD, "light": FONT_LIGHT}.get(weight, FONT)
    return ImageFont.truetype(str(path), size * SCALE)


def text(draw, xy, value, size, fill, weight="regular", anchor=None):
    draw.text((xy[0] * SCALE, xy[1] * SCALE), value, font=font(size, weight), fill=fill, anchor=anchor)


def rect(draw, box, radius, fill, outline=None, width=1):
    draw.rounded_rectangle(tuple(v * SCALE for v in box), radius * SCALE, fill=fill, outline=outline, width=width * SCALE)


def mark(draw, x, y, size, ink, bg, green):
    box = tuple(v * SCALE for v in (x, y, x + size, y + size))
    draw.ellipse(box, outline=ink, width=1 * SCALE)
    inset = size * .29
    draw.ellipse(tuple(v * SCALE for v in (x + inset, y + inset, x + size - inset, y + size - inset)), outline=ink, width=1 * SCALE)
    draw.ellipse(tuple(v * SCALE for v in (x + size - 4, y + 1, x + size - 1, y + 4)), fill=green)


def progress(draw, x, y, width, value, track, ink):
    rect(draw, (x, y, x + width, y + 4), 2, track)
    rect(draw, (x, y, x + width * value / 100, y + 4), 2, ink)


def refresh_icon(draw, x, y, color):
    box = tuple(v * SCALE for v in (x - 6, y - 6, x + 6, y + 6))
    draw.arc(box, 35, 320, fill=color, width=1 * SCALE)
    draw.polygon(
        [(int((x + 6) * SCALE), int((y - 1) * SCALE)),
         (int((x + 2) * SCALE), int((y - 2) * SCALE)),
         (int((x + 5) * SCALE), int((y + 2) * SCALE))],
        fill=color,
    )


def widget(width, dark=False, dual=False, signed_out=False, cached=False):
    height = 110
    palette = {
        "bg": "#171716" if dark else "#f7f7f5",
        "ink": "#f4f4ef" if dark else "#171716",
        "muted": "#aaa9a1" if dark else "#686862",
        "surface": "#2a2a27" if dark else "#e9e9e4",
        "border": "#4d4d49" if dark else "#d9d9d3",
        "green": "#5bd3a5" if dark else "#238b68",
        "amber": "#e7ad61" if dark else "#a66613",
    }
    image = Image.new("RGB", (width * SCALE, height * SCALE), (0, 0, 0))
    draw = ImageDraw.Draw(image)
    rect(draw, (0, 0, width, height), 20, palette["bg"], palette["border"])

    if width < 220:
        text(draw, (8, 7), "Quota", 11, palette["ink"], "semibold")
        refresh_icon(draw, width - 25, 16, palette["muted"])
        if signed_out:
            text(draw, (8, 38), "CODEX", 11, palette["muted"], "semibold")
            text(draw, (width - 8, 58), "登录", 24, palette["ink"], "semibold", "rm")
            status, dot = "轻触打开 App", palette["muted"]
        else:
            text(draw, (8, 40), "WEEKLY", 11, palette["muted"], "semibold")
            text(draw, (width - 8, 58), "64%", 29, palette["ink"], "light", "rm")
            progress(draw, 8, 79, width - 16, 64, palette["surface"], palette["ink"])
            text(draw, (8, 85), "重置于 6天14小时后更新", 8, palette["muted"])
            status, dot = (("最后更新 21:57", palette["amber"]) if cached else ("最后更新 22:00", palette["green"]))
        draw.ellipse(tuple(v * SCALE for v in (8, 98, 13, 103)), fill=dot)
        text(draw, (16, 96), status, 11, palette["muted"], "semibold")
        return image

    mark(draw, 12, 11, 18, palette["ink"], palette["bg"], palette["green"])
    text(draw, (37, 10), "OuterView Quota", 11, palette["ink"], "semibold")
    refresh_icon(draw, width - 25, 16, palette["muted"])

    right_width = 84 if dual else 0
    left_end = width - 12 - right_width - (14 if dual else 0)
    text(draw, (12, 39), "WEEKLY", 11, palette["muted"], "semibold")
    text(draw, (left_end, 57), "64%", 31, palette["ink"], "light", "rm")
    progress(draw, 12, 77, left_end - 12, 64, palette["surface"], palette["ink"])
    text(draw, (12, 83), "重置于 07-21 16:44", 8, palette["muted"])

    if dual:
        rect(draw, (width - 96, 31, width - 12, 81), 12, palette["surface"])
        text(draw, (width - 84, 35), "5 HOURS", 10, palette["muted"], "semibold")
        text(draw, (width - 24, 58), "82%", 21, palette["ink"], "light", "rm")
        progress(draw, width - 84, 72, 60, 82, palette["border"], palette["ink"])
        text(draw, (width - 84, 79), "重置于 6小时后更新", 7, palette["muted"])

    dot = palette["amber"] if cached else palette["green"]
    label = "显示缓存" if cached else "最后更新"
    draw.ellipse(tuple(v * SCALE for v in (12, 98, 17, 103)), fill=dot)
    text(draw, (21, 96), label, 10, palette["muted"], "semibold")
    detail = "上次成功 21:57" if cached else "22:00"
    if width >= 220:
        detail = f"{detail} · 于6天14小时22分钟后更新"
    text(draw, (width - 12, 96), detail, 9 if width >= 220 else 10, palette["muted"], anchor="ra")
    return image


canvas = Image.new("RGB", (1000, 740), "#e7e7e2")
draw = ImageDraw.Draw(canvas)
text(draw, (40, 24), "OuterView Quota / Launcher widgets", 18, "#171716", "semibold")
text(draw, (40, 50), "Front-screen, touch-first, responsive — not a rear-card crop", 11, "#686862")

cases = [
    (40, 140, widget(120), "Compact / Weekly only"),
    (340, 140, widget(280, dual=True), "Medium / Two windows"),
    (40, 430, widget(120, dark=True, signed_out=True), "Compact dark / Sign in"),
    (340, 430, widget(280, dark=True, dual=True, cached=True), "Medium dark / Cached"),
]
for x, y, preview, label in cases:
    canvas.paste(preview, (x, y))
    draw.text((x, y + preview.height + 14), label, font=font(11, "semibold"), fill="#464641")

canvas.save(ROOT / "widget-design-preview.png")
print(ROOT / "widget-design-preview.png")
