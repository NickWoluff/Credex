from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
FONT = Path("C:/Windows/Fonts/msyh.ttc")
FONT_BOLD = Path("C:/Windows/Fonts/msyhbd.ttc")
BG, SURFACE, MUTED = "#0f0f0e", "#181817", "#20201f"
INK, SECONDARY, LINE = "#f4f4f0", "#a6a69f", "#30302d"
GREEN, AMBER = "#10a37f", "#c77a10"


def f(size, bold=False):
    return ImageFont.truetype(str(FONT_BOLD if bold else FONT), size)


def t(d, xy, value, size, color=INK, bold=False, anchor=None):
    d.text(xy, value, font=f(size, bold), fill=color, anchor=anchor)


def mark(d, x, y, size):
    d.ellipse((x, y, x + size, y + size), outline=INK, width=max(1, size // 18))
    inset = int(size * .29)
    d.ellipse((x + inset, y + inset, x + size - inset, y + size - inset), outline=INK, width=max(1, size // 22))
    dot = max(4, size // 7)
    d.ellipse((x + size - dot * 1.2, y + dot * .25, x + size - dot * .2, y + dot * 1.25), fill=GREEN)


def header(d, settings=False):
    mark(d, 22, 38, 28)
    t(d, (60, 39), "OuterView", 16, bold=True)
    t(d, (60, 60), "SETTINGS" if settings else "CODEX USAGE", 9, SECONDARY)
    if settings:
        t(d, (22, 58), "‹", 28, SECONDARY, anchor="mm")
    else:
        t(d, (330, 51), "↻", 22, SECONDARY, anchor="mm")
        t(d, (364, 51), "•••", 15, SECONDARY, anchor="mm")


def progress(d, value, box, color=INK):
    d.rounded_rectangle(box, 4, fill=MUTED)
    x1, y1, x2, y2 = box
    d.rounded_rectangle((x1, y1, x1 + (x2 - x1) * value / 100, y2), 4, fill=color)


def login():
    im = Image.new("RGB", (390, 844), BG); d = ImageDraw.Draw(im)
    mark(d, 22, 40, 30); t(d, (62, 45), "OuterView", 16, bold=True)
    mark(d, 24, 154, 76)
    t(d, (24, 262), "把 Codex 用量", 32, bold=True)
    t(d, (24, 309), "带到背屏", 32, bold=True)
    t(d, (24, 374), "直接连接你的 OpenAI 账户。无需电脑桥接，", 15, SECONDARY)
    t(d, (24, 402), "也不需要在 Android 上运行 Codex。", 15, SECONDARY)
    d.rounded_rectangle((24, 468, 366, 524), 18, fill=INK)
    t(d, (195, 496), "使用 OpenAI 账户继续", 15, BG, True, "mm")
    t(d, (195, 549), "将在系统浏览器中安全打开授权页面", 12, SECONDARY, anchor="mm")
    t(d, (195, 603), "授权遇到问题？", 13, SECONDARY, anchor="mm")
    t(d, (24, 791), "独立 Companion，由 OuterView 提供，与 OpenAI 无隶属关系。", 10, SECONDARY)
    return im


def dashboard():
    im = Image.new("RGB", (390, 844), BG); d = ImageDraw.Draw(im); header(d)
    t(d, (20, 108), "用量", 28, bold=True); t(d, (20, 145), "Plus plan", 12, SECONDARY)
    d.rounded_rectangle((294, 110, 370, 143), 17, fill=MUTED)
    d.ellipse((306, 122, 314, 130), fill=GREEN); t(d, (323, 126), "已连接", 11, anchor="lm")
    d.rounded_rectangle((20, 174, 370, 448), 28, fill=SURFACE, outline=LINE, width=1)
    t(d, (42, 202), "本周剩余", 15, SECONDARY)
    t(d, (42, 247), "64%", 68, bold=True)
    progress(d, 64, (42, 351, 348, 359))
    t(d, (42, 382), "重置于 07-20 09:00 · 于5天8小时20分钟后更新", 12, SECONDARY)
    d.rounded_rectangle((20, 464, 370, 603), 22, fill=SURFACE, outline=LINE, width=1)
    t(d, (40, 490), "5 小时剩余", 16, bold=True)
    t(d, (350, 490), "72%", 27, bold=True, anchor="ra")
    t(d, (40, 520), "重置于 07-14 02:00 · 于2小时18分钟后更新", 11, SECONDARY)
    progress(d, 72, (40, 564, 350, 570))
    d.rounded_rectangle((20, 619, 370, 687), 18, fill=MUTED)
    d.ellipse((39, 648, 47, 656), fill=GREEN)
    t(d, (59, 639), "最后更新", 13, bold=True)
    t(d, (59, 661), "21:57", 11, SECONDARY)
    return im


def row(d, y, title, subtitle, toggle=None):
    d.ellipse((38, y + 15, 72, y + 49), fill=MUTED)
    t(d, (55, y + 32), "·", 22, SECONDARY, anchor="mm")
    t(d, (86, y + 11), title, 14, bold=True)
    t(d, (86, y + 35), subtitle, 10, SECONDARY)
    if toggle is None:
        t(d, (348, y + 31), "›", 24, SECONDARY, anchor="mm")
    else:
        d.rounded_rectangle((324, y + 18, 362, y + 42), 12, fill=GREEN if toggle else LINE)
        cx = 350 if toggle else 336
        d.ellipse((cx - 9, y + 21, cx + 9, y + 39), fill=INK)


def settings():
    im = Image.new("RGB", (390, 844), BG); d = ImageDraw.Draw(im); header(d, True)
    t(d, (20, 108), "设置", 28, bold=True)
    t(d, (24, 158), "同步", 11, SECONDARY)
    d.rounded_rectangle((20, 180, 370, 394), 22, fill=SURFACE, outline=LINE, width=1)
    row(d, 187, "持续同步", "前台服务正在运行", True)
    d.line((82, 250, 356, 250), fill=LINE)
    row(d, 257, "通知", "已允许")
    d.line((82, 320, 356, 320), fill=LINE)
    row(d, 327, "后台与电池", "不受电池优化限制")
    t(d, (24, 416), "主屏幕", 11, SECONDARY)
    d.rounded_rectangle((20, 438, 370, 510), 22, fill=SURFACE, outline=LINE, width=1)
    row(d, 441, "添加配额小组件", "小尺寸主窗口 · 横向双窗口")
    t(d, (24, 536), "账户", 11, SECONDARY)
    d.rounded_rectangle((20, 558, 370, 704), 22, fill=SURFACE, outline=LINE, width=1)
    row(d, 561, "Plus", "OpenAI 账户已授权")
    d.line((82, 630, 356, 630), fill=LINE)
    row(d, 635, "隐私与凭证", "Android Keystore 加密")
    t(d, (24, 728), "关于", 11, SECONDARY)
    d.rounded_rectangle((20, 750, 370, 824), 22, fill=SURFACE, outline=LINE, width=1)
    row(d, 753, "OuterView Quota", "版本 0.4.0 · 独立 Companion")
    return im


canvas = Image.new("RGB", (1190, 884), "#e8e8e3")
for i, (screen, label) in enumerate(((login(), "Sign in"), (dashboard(), "Dashboard"), (settings(), "Settings"))):
    x = 5 + i * 395
    canvas.paste(screen, (x, 5))
    t(ImageDraw.Draw(canvas), (x + 10, 858), label, 12, "#363633", True)
canvas.save(ROOT / "app-design-preview.png")
print(ROOT / "app-design-preview.png")
