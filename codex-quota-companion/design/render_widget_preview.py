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


def mark(draw, x, y, size, ink, green):
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


def widget(width, dark=False, dual=False, signed_out=False, cached=False, balance=False):
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
    image = Image.new("RGB", (width * SCALE, height * SCALE), palette["bg"])
    draw = ImageDraw.Draw(image)
    rect(draw, (0, 0, width, height), 18, palette["bg"], palette["border"])

    if width < 220:
        mark(draw, 10, 10, 14, palette["ink"], palette["green"])
        text(draw, (30, 10), "OUTERVIEW", 9, palette["ink"], "semibold")
        refresh_icon(draw, width - 19, 17, palette["muted"])
        if signed_out:
            text(draw, (10, 39), "CODEX", 10, palette["muted"], "semibold")
            text(draw, (10, 56), "登录以查看配额", 13, palette["ink"], "semibold")
            status, dot = "轻触打开 App", palette["muted"]
        elif balance:
            text(draw, (10, 39), "SILICONFLOW", 9, palette["muted"], "semibold")
            text(draw, (width - 10, 57), "¥3.40", 23, palette["ink"], "light", "rm")
            progress(draw, 10, 72, width - 20, 72, palette["surface"], palette["ink"])
            text(draw, (10, 80), "已连接 · 22:00", 8, palette["muted"])
            status, dot = "余额已同步", palette["green"]
        else:
            text(draw, (10, 39), "WEEKLY", 10, palette["muted"], "semibold")
            text(draw, (width - 10, 58), "64%", 25, palette["ink"], "light", "rm")
            progress(draw, 10, 72, width - 20, 64, palette["surface"], palette["ink"])
            text(draw, (10, 80), "重置于 6天14小时后", 8, palette["muted"])
            status, dot = (("显示缓存", palette["amber"]) if cached else ("最后更新 22:00", palette["green"]))
        draw.ellipse(tuple(v * SCALE for v in (10, 98, 15, 103)), fill=dot)
        text(draw, (19, 95), status, 9, palette["muted"], "semibold")
        return image

    mark(draw, 12, 11, 16, palette["ink"], palette["green"])
    text(draw, (35, 10), "OUTERVIEW QUOTA", 10, palette["ink"], "semibold")
    refresh_icon(draw, width - 19, 17, palette["muted"])

    right_width = 88 if dual else 0
    left_end = width - 12 - right_width - (10 if dual else 0)
    if balance:
        text(draw, (12, 39), "SILICONFLOW", 9, palette["muted"], "semibold")
        text(draw, (left_end, 57), "¥3.40", 25, palette["ink"], "light", "rm")
        progress(draw, 12, 72, left_end - 12, 72, palette["surface"], palette["ink"])
        text(draw, (12, 80), "已连接 · 22:00", 8, palette["muted"])
    else:
        text(draw, (12, 39), "WEEKLY", 10, palette["muted"], "semibold")
        text(draw, (left_end, 57), "64%", 27, palette["ink"], "light", "rm")
        progress(draw, 12, 72, left_end - 12, 64, palette["surface"], palette["ink"])
        text(draw, (12, 80), "重置于 07-21 16:44", 8, palette["muted"])

    if dual:
        rect(draw, (width - 100, 31, width - 12, 82), 12, palette["surface"])
        text(draw, (width - 88, 35), "5 HOURS", 9, palette["muted"], "semibold")
        text(draw, (width - 22, 58), "82%", 20, palette["ink"], "light", "rm")
        progress(draw, width - 88, 72, 64, 82, palette["border"], palette["ink"])
        text(draw, (width - 88, 79), "重置于 6小时后", 7, palette["muted"])

    dot = palette["amber"] if cached else palette["green"]
    label = "显示缓存" if cached else ("余额" if balance else "最后更新")
    detail = "SILICONFLOW ¥3.40" if balance else ("上次成功 21:57" if cached else "22:00 · 6天14小时后")
    draw.ellipse(tuple(v * SCALE for v in (12, 98, 17, 103)), fill=dot)
    text(draw, (21, 95), label, 9, palette["muted"], "semibold")
    text(draw, (width - 12, 95), detail, 8, palette["muted"], anchor="ra")
    return image


canvas = Image.new("RGB", (1000, 740), "#e7e7e2")
draw = ImageDraw.Draw(canvas)
text(draw, (40, 24), "OuterView Quota / Launcher widgets", 18, "#171716", "semibold")
text(draw, (40, 50), "Fixed grid · single-line labels · no accidental wrapping", 11, "#686862")

cases = [
    (40, 140, widget(120), "Compact / Weekly"),
    (340, 140, widget(280, dual=True), "Medium / Two windows"),
    (40, 430, widget(120, dark=True, balance=True), "Compact dark / Balance"),
    (340, 430, widget(280, dark=True, balance=True), "Medium dark / Balance + quota"),
]
for x, y, preview, label in cases:
    canvas.paste(preview, (x, y))
    draw.text((x, y + preview.height + 14), label, font=font(11, "semibold"), fill="#464641")

canvas.save(ROOT / "widget-design-preview.png")
widget(280, dual=True).save(ROOT / "widget-preview-4x2.png")
widget(120).save(ROOT / "widget-preview-2x2.png")
print(ROOT / "widget-design-preview.png")
