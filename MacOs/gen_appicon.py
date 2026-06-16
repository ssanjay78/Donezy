#!/usr/bin/env python3
"""Generate the Donezy iOS app icon to match the Android adaptive icon.

Android icon = green→teal→blue diagonal gradient, white circular badge with a
green checkmark and a 12-ray celebratory burst. iOS icons are full-bleed (no
transparency; the system rounds the corners), so we paint the gradient edge to
edge and center the badge — the same composition the Android launcher shows.

Outputs a single 1024×1024 PNG into the app's AppIcon asset set, plus a 180×180
PNG used as the notification large-image attachment so shade notifications carry
the same artwork.

Run: python3 gen_appicon.py
"""
import math
import os
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.abspath(__file__))
SIZE = 1024

# Android palette (from ic_launcher_background.xml / foreground)
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


def make_icon(size):
    # Supersample for crisp anti-aliased curves, then downscale.
    ss = 4
    S = size * ss
    img = Image.new("RGB", (S, S), START)
    px = img.load()

    # Diagonal gradient along the top-left → bottom-right axis (matches Android).
    maxd = (S - 1) + (S - 1)
    for y in range(S):
        for x in range(S):
            px[x, y] = gradient_color((x + y) / maxd)

    draw = ImageDraw.Draw(img)
    cx = cy = S / 2

    # ── Celebratory burst: 12 tapered rays radiating from the badge ──────────
    ray_inner = S * 0.34
    ray_outer = S * 0.46
    for i in range(12):
        ang = math.radians(i * 30)
        x1 = cx + ray_inner * math.cos(ang)
        y1 = cy + ray_inner * math.sin(ang)
        x2 = cx + ray_outer * math.cos(ang)
        y2 = cy + ray_outer * math.sin(ang)
        draw.line([(x1, y1), (x2, y2)], fill=WHITE, width=int(S * 0.012))

    # ── White circular badge ─────────────────────────────────────────────────
    r = S * 0.30
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=WHITE)

    # Subtle inner ring
    ir = S * 0.255
    draw.ellipse([cx - ir, cy - ir, cx + ir, cy + ir],
                 outline=(GREEN[0], GREEN[1], GREEN[2]), width=max(1, int(S * 0.004)))

    # ── Bold green checkmark, slightly tilted, grounded bottom-right ─────────
    lw = int(S * 0.05)
    p1 = (cx - S * 0.105, cy + S * 0.01)
    p2 = (cx - S * 0.02, cy + S * 0.10)
    p3 = (cx + S * 0.13, cy - S * 0.085)
    draw.line([p1, p2], fill=GREEN, width=lw, joint="curve")
    draw.line([p2, p3], fill=GREEN, width=lw, joint="curve")
    # Round the stroke ends/elbow with filled circles (PIL lines aren't capped).
    for (px_, py_) in (p1, p2, p3):
        rr = lw / 2
        draw.ellipse([px_ - rr, py_ - rr, px_ + rr, py_ + rr], fill=GREEN)

    # Kinetic trailing dots off the tick tip.
    for (dx, dy, dr, a) in [(0.155, -0.11, 0.018, 0.5), (0.185, -0.135, 0.013, 0.35)]:
        ddx, ddy, ddr = cx + S * dx, cy + S * dy, S * dr
        blended = lerp(WHITE, GREEN, 1.0)  # solid green dots, faint via size
        draw.ellipse([ddx - ddr, ddy - ddr, ddx + ddr, ddy + ddr], fill=GREEN)

    return img.resize((size, size), Image.LANCZOS)


def main():
    appicon_dir = os.path.join(ROOT, "Donezy", "Assets.xcassets", "AppIcon.appiconset")
    os.makedirs(appicon_dir, exist_ok=True)

    icon = make_icon(SIZE)
    icon.save(os.path.join(appicon_dir, "AppIcon-1024.png"))
    print("Wrote AppIcon-1024.png")

    # Notification attachment artwork (shown as the big image in the shade).
    notif_dir = os.path.join(ROOT, "Donezy", "Assets.xcassets", "NotificationIcon.imageset")
    os.makedirs(notif_dir, exist_ok=True)
    for scale, name in [(1, "notif-180.png")]:
        make_icon(180).save(os.path.join(notif_dir, name))
    print("Wrote notif-180.png")


if __name__ == "__main__":
    main()
