#!/usr/bin/env python3
"""从一张参考照片生成淑婷试衣间启动图标。

用法：python3 tools/make_icon.py <参考图.jpg>
会在 app/src/main/res/mipmap-*/ 下生成 ic_launcher.png / ic_launcher_round.png，
并在 mipmap-xxxhdpi 下生成自适应图标前景 ic_launcher_foreground.png（432x432）。
参考图本身不要提交进仓库（个人照片）。
裁剪：以照片中人脸为中心取正方形（默认中心点 0.5w, 0.35h，可改 CX/CY）。
"""
import os
import sys
from PIL import Image, ImageDraw

CX, CY = 0.50, 0.35  # 脸部中心在图中的相对位置
SIDE = 900           # 裁剪边长（像素）
SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def main() -> None:
    if len(sys.argv) < 2:
        print("用法：python3 tools/make_icon.py <参考图.jpg>")
        sys.exit(1)
    src = Image.open(sys.argv[1]).convert("RGB")
    w, h = src.size
    cx, cy = int(w * CX), int(h * CY)
    side = min(SIDE, w, h)
    x0 = max(0, min(w - side, cx - side // 2))
    y0 = max(0, min(h - side, cy - side // 2))
    crop = src.crop((x0, y0, x0 + side, y0 + side))
    base = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")
    for d, s in SIZES.items():
        os.makedirs(os.path.join(base, f"mipmap-{d}"), exist_ok=True)
        icon = crop.resize((s, s), Image.LANCZOS)
        icon.save(os.path.join(base, f"mipmap-{d}", "ic_launcher.png"))
        mask = Image.new("L", (s, s), 0)
        ImageDraw.Draw(mask).ellipse((0, 0, s, s), fill=255)
        round_icon = Image.new("RGBA", (s, s), (0, 0, 0, 0))
        round_icon.paste(icon.convert("RGBA"), (0, 0), mask)
        round_icon.save(os.path.join(base, f"mipmap-{d}", "ic_launcher_round.png"))
    crop.resize((432, 432), Image.LANCZOS).save(
        os.path.join(base, "mipmap-xxxhdpi", "ic_launcher_foreground.png")
    )
    print(f"图标已生成（裁剪框 x={x0} y={y0} 边长={side}）")


if __name__ == "__main__":
    main()
