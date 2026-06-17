#!/usr/bin/env python3
"""Generate Google Play Store listing assets for Donezy.

Outputs (next to this script):
  - app_icon_512.png       512×512, 32-bit PNG with alpha   (Play "App icon")
  - feature_graphic_1024x500.png  1024×500, no alpha        (Play "Feature graphic")

The artwork matches the Android adaptive launcher icon: a green→teal→blue diagonal
gradient, a white circular badge with a green checkmark and a 12-ray celebratory
burst — the same composition used for the iOS icon.

Run: python3 gen_playstore_assets.py
"""
import math
import os
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.abspath(__file__))

# Donezy palette (from ic_launcher_background.xml / foreground)
START = (0x1A, 0x6B, 0x48)   # green
CENTER = (0x2E, 0x8B, 0x6B)  # teal
END = (0x1B, 0x61, 0xA4)     # blue
GREEN = (0x1A, 0x6B, 0x48)
WHITE = (0xFF, 0xFF, 0xFF)


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def gradient_color(t):
    """Three-stop diagonal gradient: start → center → end."""
    if t < 0.5:
        return lerp(START, CENTER, t / 0.5)
    return lerp(CENTER, END, (t - 0.5) / 0.5)


def paint_gradient(img):
    """Fill an RGB image with the diagonal top-left → bottom-right gradient."""
    w, h = img.size
    px = img.load()
    maxd = (w - 1) + (h - 1)
    for y in range(h):
        for x in range(w):
            px[x, y] = gradient_color((x + y) / maxd)


def draw_badge(draw, cx, cy, scale, with_rays=True):
    """Draw the white badge + burst + green check centred at (cx, cy).

    `scale` is the badge radius reference in pixels; all parts are derived from it.
    """
    S = scale  # treat as the "viewport" half-size analogue

    # ── Celebratory burst: 12 tapered rays radiating from the badge ──────────
    if with_rays:
        ray_inner = S * 1.13
        ray_outer = S * 1.53
        for i in range(12):
            ang = math.radians(i * 30)
            x1 = cx + ray_inner * math.cos(ang)
            y1 = cy + ray_inner * math.sin(ang)
            x2 = cx + ray_outer * math.cos(ang)
            y2 = cy + ray_outer * math.sin(ang)
            draw.line([(x1, y1), (x2, y2)], fill=WHITE, width=int(S * 0.04))

    # ── White circular badge ─────────────────────────────────────────────────
    r = S
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=WHITE)

    # Subtle inner ring
    ir = S * 0.85
    draw.ellipse([cx - ir, cy - ir, cx + ir, cy + ir], outline=GREEN, width=max(1, int(S * 0.013)))

    # ── Bold green checkmark, slightly tilted, grounded bottom-right ─────────
    lw = int(S * 0.166)
    p1 = (cx - S * 0.35, cy + S * 0.03)
    p2 = (cx - S * 0.07, cy + S * 0.33)
    p3 = (cx + S * 0.43, cy - S * 0.28)
    draw.line([p1, p2], fill=GREEN, width=lw, joint="curve")
    draw.line([p2, p3], fill=GREEN, width=lw, joint="curve")
    for (px_, py_) in (p1, p2, p3):
        rr = lw / 2
        draw.ellipse([px_ - rr, py_ - rr, px_ + rr, py_ + rr], fill=GREEN)

    # Kinetic trailing dots off the tick tip.
    for (dx, dy, dr) in [(0.52, -0.37, 0.06), (0.62, -0.45, 0.043)]:
        ddx, ddy, ddr = cx + S * dx, cy + S * dy, S * dr
        draw.ellipse([ddx - ddr, ddy - ddr, ddx + ddr, ddy + ddr], fill=GREEN)


def make_app_icon(size=512):
    """512×512 launcher-style icon WITH alpha (Play requires a 32-bit PNG)."""
    ss = 4
    S = size * ss
    base = Image.new("RGBA", (S, S), (0, 0, 0, 0))

    # Rounded-square gradient tile (Play displays icons with rounded masking, but the
    # uploaded asset itself is a full square; we keep a slight rounding for safety).
    grad = Image.new("RGB", (S, S))
    paint_gradient(grad)
    grad = grad.convert("RGBA")

    mask = Image.new("L", (S, S), 0)
    md = ImageDraw.Draw(mask)
    radius = int(S * 0.20)
    md.rounded_rectangle([0, 0, S - 1, S - 1], radius=radius, fill=255)
    base.paste(grad, (0, 0), mask)

    draw = ImageDraw.Draw(base)
    draw_badge(draw, S / 2, S / 2, S * 0.30, with_rays=True)

    return base.resize((size, size), Image.LANCZOS)


def load_font(size):
    """Best-effort system font for the feature-graphic wordmark."""
    candidates = [
        "/System/Library/Fonts/SFNSRounded.ttf",
        "/System/Library/Fonts/SFNS.ttf",
        "/System/Library/Fonts/HelveticaNeue.ttc",
        "/Library/Fonts/Arial Bold.ttf",
        "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
    ]
    for path in candidates:
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except Exception:
                continue
    return ImageFont.load_default()


def make_feature_graphic(width=1024, height=500):
    """1024×500 feature graphic, NO alpha (Play disallows transparency here)."""
    ss = 2
    W, H = width * ss, height * ss
    img = Image.new("RGB", (W, H))
    paint_gradient(img)
    draw = ImageDraw.Draw(img)

    # Badge on the left third, vertically centred.
    badge_cx = W * 0.22
    badge_cy = H * 0.5
    draw_badge(draw, badge_cx, badge_cy, H * 0.30, with_rays=True)

    # Wordmark + tagline on the right.
    title_font = load_font(int(H * 0.20))
    tag_font = load_font(int(H * 0.082))
    text_x = W * 0.44

    title = "Donezy"
    tagline = "Plan it · Do it · Done it."

    # Vertically centre the two lines as a block.
    t_bbox = draw.textbbox((0, 0), title, font=title_font)
    g_bbox = draw.textbbox((0, 0), tagline, font=tag_font)
    t_h = t_bbox[3] - t_bbox[1]
    g_h = g_bbox[3] - g_bbox[1]
    gap = int(H * 0.04)
    block_h = t_h + gap + g_h
    top = badge_cy - block_h / 2

    draw.text((text_x, top - t_bbox[1]), title, font=title_font, fill=WHITE)
    draw.text((text_x, top + t_h + gap - g_bbox[1]), tagline, font=tag_font,
              fill=(255, 255, 255, 230))

    return img.resize((width, height), Image.LANCZOS)


def main():
    icon = make_app_icon(512)
    icon_path = os.path.join(ROOT, "app_icon_512.png")
    icon.save(icon_path)
    print("Wrote", icon_path, icon.size, "RGBA")

    fg = make_feature_graphic(1024, 500)
    fg_path = os.path.join(ROOT, "feature_graphic_1024x500.png")
    fg.save(fg_path)
    print("Wrote", fg_path, fg.size, "RGB (no alpha)")


if __name__ == "__main__":
    main()
