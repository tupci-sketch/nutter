#!/usr/bin/env python3
"""Draws Habnut's own badges and gives them names.

    make-badges.py <badges.json> <nitro root>

Each badge is a 40x40 GIF in c_images/album1584 (where the client looks):
a coloured, bevelled disc with a furniture icon or a short label on it.
Names and descriptions go into gamedata/ExternalTexts.habnut.json, which
build-gamedata.py merges into ExternalTexts.json, and straight into the live
ExternalTexts.json too. Needs Pillow. Safe to run again.
"""
import json
import os
import sys

from PIL import Image, ImageDraw, ImageFont

SIZE = 40


def shade(rgb, k):
    return tuple(max(0, min(255, int(c * k))) for c in rgb)


def badge(colour, icon=None, label=None, ring=None):
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    base = tuple(colour)
    ring = tuple(ring) if ring else shade(base, 0.55)
    d.ellipse((1, 1, SIZE - 2, SIZE - 2), fill=(0, 0, 0, 255))           # outline
    d.ellipse((2, 2, SIZE - 3, SIZE - 3), fill=ring + (255,))            # rim
    d.ellipse((5, 5, SIZE - 6, SIZE - 6), fill=base + (255,))            # face
    d.arc((5, 5, SIZE - 6, SIZE - 6), 200, 330, fill=shade(base, 1.35) + (255,), width=2)  # light
    d.arc((5, 5, SIZE - 6, SIZE - 6), 20, 150, fill=shade(base, 0.75) + (255,), width=2)   # shadow
    if icon:
        pic = Image.open(icon).convert("RGBA")
        pic.thumbnail((24, 24), Image.NEAREST)
        img.alpha_composite(pic, ((SIZE - pic.width) // 2, (SIZE - pic.height) // 2 + (2 if label else 0) - (4 if label else 0)))
    if label:
        font = ImageFont.load_default()
        box = d.textbbox((0, 0), label, font=font)
        w, h = box[2] - box[0], box[3] - box[1]
        x, y = (SIZE - w) // 2, (SIZE - h) // 2 + (9 if icon else 0)
        for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            d.text((x + dx - box[0], y + dy - box[1]), label, font=font, fill=(0, 0, 0, 255))
        d.text((x - box[0], y - box[1]), label, font=font, fill=(255, 255, 255, 255))
    return img


def to_gif(img, path):
    # GIF has one transparent colour: flatten alpha to on/off first.
    rgba = img.copy()
    alpha = rgba.getchannel("A").point(lambda a: 255 if a > 96 else 0)
    rgb = Image.new("RGB", rgba.size, (255, 0, 255))
    rgb.paste(rgba.convert("RGB"), mask=alpha)
    pal = rgb.quantize(colors=255, method=Image.Quantize.MEDIANCUT)
    key = pal.getpixel((0, 0))
    pal.save(path, transparency=key)


def main():
    spec_path, root = sys.argv[1:3]
    spec = json.load(open(spec_path))
    album = os.path.join(root, "c_images", "album1584")
    icons = os.path.join(root, "c_images", "dcr", "hof_furni", "icons")
    texts_path = os.path.join(root, "gamedata", "ExternalTexts.json")
    overlay_path = os.path.join(root, "gamedata", "ExternalTexts.habnut.json")
    texts = {}
    for b in spec["badges"]:
        icon = os.path.join(icons, b["icon"] + "_icon.png") if b.get("icon") else None
        if icon and not os.path.exists(icon):
            icon = None
        img = badge(b["colour"], icon, b.get("label"), b.get("ring"))
        to_gif(img, os.path.join(album, b["code"] + ".gif"))
        texts[f"badge_name_{b['code']}"] = b["name"]
        texts[f"badge_desc_{b['code']}"] = b["description"]
        print(f"  {b['code']}: {b['name']}")
    overlay = json.load(open(overlay_path)) if os.path.exists(overlay_path) else {}
    overlay.update(texts)
    json.dump(overlay, open(overlay_path, "w"), ensure_ascii=False, indent=1)
    live = json.load(open(texts_path))
    live.update(overlay)
    json.dump(live, open(texts_path + ".part", "w"), ensure_ascii=False, separators=(",", ":"))
    os.replace(texts_path + ".part", texts_path)
    print(f"{len(spec['badges'])} badges drawn and named")


if __name__ == "__main__":
    main()
