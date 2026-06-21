"""
根据与 ic_launcher_foreground / ic_launcher_background 一致的视觉设计，
为旧版 Android (pre-O) 生成 ic_launcher / ic_launcher_round 多分辨率 PNG。
设计要点：
- 108×108 viewport，72×72 safe zone (内 18dp 边距)
- 径向渐变背景（中心 #3F4D7A -> 边缘 #0F1530）
- 外圈细环 + 未闭合三角盾 + 内嵌白色闪电
"""
from PIL import Image, ImageDraw
import math
import os

OUT_BASE = r"D:/document/Projects/df-item-crafting-monitoring/app/src/main/res"

# 五档 dpi：mdpi 48, hdpi 72, xhdpi 96, xxhdpi 144, xxxhdpi 192
DPI_SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

# 颜色（与 vector 一致）
BG_INNER = (0x3F, 0x4D, 0x7A)
BG_OUTER = (0x0F, 0x15, 0x30)
TEAL = (0x5E, 0xEA, 0xD4)
WHITE = (0xFF, 0xFF, 0xFF)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def make_icon(size: int, round_mask: bool):
    """size = 输出像素边长，round_mask = True 时画圆形 mask"""
    # 在 4x 超采样下绘制保证质量，再下采样
    s = size * 4
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    cx, cy = s / 2, s * 0.4  # 中心点略偏上
    R = s * 0.72

    # ---- 背景：径向渐变 ----
    # PIL 的 R 通道最远，用嵌套圆近似径向渐变
    steps = 80
    for i in range(steps, 0, -1):
        t = (steps - i) / steps
        col = lerp(BG_INNER, BG_OUTER, t)
        r = R * i / steps
        draw.ellipse(
            (cx - r, cy - r, cx + r, cy + r),
            fill=col + (255,),
        )
    # 补齐超出椭圆外的方形区域为外圈色
    draw.rectangle((0, 0, s, s), fill=BG_OUTER + (255,))

    # ---- 圆形 mask (圆图标) ----
    if round_mask:
        mask = Image.new("L", (s, s), 0)
        ImageDraw.Draw(mask).ellipse((0, 0, s, s), fill=255)
        # 应用圆形 mask
        rounded = Image.new("RGBA", (s, s), (0, 0, 0, 0))
        rounded.paste(img, (0, 0), mask)
        img = rounded
        draw = ImageDraw.Draw(img)

    # ---- 外圈细环 ----
    ring_r = s * 0.30
    ring_w = max(2, int(s * 0.016))
    draw.ellipse(
        (cx - ring_r, cy - ring_r, cx + ring_r, cy + ring_r),
        outline=TEAL + (255,),
        width=ring_w,
    )

    # ---- 未闭合三角盾（顶角在上，底边两侧到中心 V 形开口）----
    # 与 vector 一致：M54,30 L80,76 L54,68 L28,76 Z
    # 在 s 尺寸下坐标按比例缩放：54/108=0.5, 30/108=0.278, 80/108=0.741, 76/108=0.704, 68/108=0.630
    pts = [
        (cx, cy - s * 0.205),          # 顶角（54,30 在 108 中）
        (cx + s * 0.241, cy + s * 0.241),  # 右下 (80,76)
        (cx, cy + s * 0.130),          # 内凹 (54,68)
        (cx - s * 0.241, cy + s * 0.241),  # 左下 (28,76)
    ]
    draw.polygon(pts, fill=TEAL + (255,))

    # ---- 内嵌闪电 ----
    # 简化 6 点闪电，居中略偏下
    bolt = [
        (cx + s * 0.028, cy - s * 0.139),  # 57,38
        (cx - s * 0.056, cy + s * 0.046),  # 48,56
        (cx, cy + s * 0.046),              # 54,56
        (cx - s * 0.028, cy + s * 0.222),  # 51,70
        (cx + s * 0.074, cy + s * 0.037),  # 62,52
        (cx + s * 0.019, cy + s * 0.037),  # 56,52
        (cx + s * 0.046, cy - s * 0.139),  # 59,38
    ]
    draw.polygon(bolt, fill=WHITE + (255,))

    # 下采样到目标尺寸
    return img.resize((size, size), Image.LANCZOS)


def main():
    for folder, size in DPI_SIZES.items():
        out_dir = os.path.join(OUT_BASE, folder)
        os.makedirs(out_dir, exist_ok=True)
        sq = make_icon(size, round_mask=False)
        rd = make_icon(size, round_mask=True)
        sq.save(os.path.join(out_dir, "ic_launcher.png"))
        rd.save(os.path.join(out_dir, "ic_launcher_round.png"))
        print(f"  {folder}: {size}x{size} -> ic_launcher.png + ic_launcher_round.png")


if __name__ == "__main__":
    main()
    print("done")
