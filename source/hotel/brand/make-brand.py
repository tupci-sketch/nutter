#!/usr/bin/env python3
"""Draws Habnut's own brand pictures, in Habnut's own type.

    make-brand.py <fonts dir> <out dir>

Writes: the wordmark at several sizes, the app icon, toolbar icons, the
hotel-view sky and sun, and staff rank badges. Type is Lilita One (logo and
headings) and Nunito (text), not the hotel-of-origin's fonts. Needs Pillow.
"""
import os
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

FONTS, OUT = sys.argv[1:3]
os.makedirs(OUT, exist_ok=True)

GOLD = [(255, 236, 128), (255, 196, 40), (240, 140, 0)]
OUTLINE = (58, 30, 0)
SHADOW = (30, 14, 0)


def font(name, size, variation=None):
    f = ImageFont.truetype(os.path.join(FONTS, name), size)
    if variation:
        try:
            f.set_variation_by_name(variation)
        except Exception:
            pass
    return f


def gradient(size, stops, vertical=True):
    w, h = size
    img = Image.new("RGBA", size)
    px = img.load()
    span = h if vertical else w
    for i in range(span):
        t = i / max(1, span - 1)
        seg = min(int(t * (len(stops) - 1)), len(stops) - 2)
        lt = t * (len(stops) - 1) - seg
        c = tuple(int(a + (b - a) * lt) for a, b in zip(stops[seg], stops[seg + 1]))
        for j in range(w if vertical else h):
            px[(j, i) if vertical else (i, j)] = c + (255,)
    return img


def outlined_text(text, fnt, fill_stops, outline=OUTLINE, stroke=None, shadow=3, pad=None):
    """Text filled with a gradient, a thick outline and a drop shadow, on transparency."""
    stroke = stroke if stroke is not None else max(2, fnt.size // 12)
    pad = pad if pad is not None else stroke + shadow + 2
    box = ImageDraw.Draw(Image.new("L", (1, 1))).textbbox((0, 0), text, font=fnt, stroke_width=stroke)
    w, h = box[2] - box[0] + 2 * pad, box[3] - box[1] + 2 * pad
    origin = (pad - box[0], pad - box[1])
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).text(origin, text, font=fnt, fill=255)
    outer = Image.new("L", (w, h), 0)
    ImageDraw.Draw(outer).text(origin, text, font=fnt, fill=255, stroke_width=stroke, stroke_fill=255)
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    if shadow:
        sh = Image.new("RGBA", (w, h), SHADOW + (200,))
        out.paste(sh, (shadow, shadow), ImageChops.offset(outer, 0, 0))
    out.paste(Image.new("RGBA", (w, h), outline + (255,)), (0, 0), outer)
    fill = gradient((w, h), fill_stops)
    out.paste(fill, (0, 0), mask)
    # a soft highlight across the top half of the letters
    hl = Image.new("L", (w, h), 0)
    ImageDraw.Draw(hl).rectangle((0, 0, w, int(origin[1] + (box[3] - box[1]) * 0.42)), fill=70)
    hl = ImageChops.multiply(hl, mask)
    out.paste(Image.new("RGBA", (w, h), (255, 255, 255, 255)), (0, 0), hl)
    return out


def wordmark(height):
    fnt = font("LilitaOne-Regular.ttf", int(height * 0.78))
    return outlined_text("Habnut", fnt, GOLD, stroke=max(2, height // 14), shadow=max(2, height // 20))


def icon(size):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    r = size // 5
    bg = gradient((size, size), [(255, 170, 30), (226, 100, 0)])
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size - 1, size - 1), radius=r, fill=255)
    img.paste(bg, (0, 0), mask)
    ImageDraw.Draw(img).rounded_rectangle((0, 0, size - 1, size - 1), radius=r, outline=OUTLINE + (255,), width=max(1, size // 24))
    letter = outlined_text("H", font("LilitaOne-Regular.ttf", int(size * 0.72)), [(255, 255, 255), (255, 236, 170)],
                           stroke=max(1, size // 22), shadow=max(1, size // 30))
    img.alpha_composite(letter, ((size - letter.width) // 2, (size - letter.height) // 2 + size // 40))
    return img


def sky(size):
    return gradient(size, [(118, 196, 240), (170, 222, 248), (255, 220, 170)])


def sun(size):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    glow = Image.new("L", (size, size), 0)
    ImageDraw.Draw(glow).ellipse((size * 0.1, size * 0.1, size * 0.9, size * 0.9), fill=120)
    glow = glow.filter(ImageFilter.GaussianBlur(size / 10))
    layer = Image.new("RGBA", (size, size), (255, 236, 160, 0))
    layer.putalpha(glow)
    img.alpha_composite(layer)
    d = ImageDraw.Draw(img)
    c = size // 2
    rr = size * 0.24
    d.ellipse((c - rr, c - rr, c + rr, c + rr), fill=(255, 214, 80, 255), outline=(240, 160, 30, 255), width=max(1, size // 60))
    return img


RANKS = [
    # code, label, colours (top, bottom)
    ("HNVIP", "VIP", [(255, 210, 90), (226, 140, 0)]),
    ("HNHLP", "HELP", [(120, 210, 120), (40, 140, 70)]),
    ("HNSUP", "SUP", [(110, 190, 240), (30, 110, 190)]),
    ("HNMOD", "MOD", [(190, 140, 240), (110, 60, 190)]),
    ("HNSMD", "SMOD", [(240, 120, 160), (180, 40, 90)]),
    ("HNADM", "ADM", [(255, 120, 80), (190, 40, 20)]),
    ("HNOWN", "OWN", [(255, 236, 128), (240, 140, 0)]),
]


def rank_badge(label, colours):
    """A 40x40 shield with the rank's letters, in Habnut's pixel face."""
    size = 40
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    shield = [(4, 3), (35, 3), (35, 20), (20, 37), (4, 20)]
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).polygon(shield, fill=255)
    img.paste(gradient((size, size), colours), (0, 0), mask)
    d = ImageDraw.Draw(img)
    d.line(shield + [shield[0]], fill=(30, 14, 0, 255), width=2)
    d.line([(7, 6), (32, 6)], fill=(255, 255, 255, 140), width=1)
    star = font("LilitaOne-Regular.ttf", 9)
    d.text((20, 8), "★" if False else "H", font=star, fill=(255, 255, 255, 230), anchor="mt")
    fnt = font("PixelifySans.ttf", 11 if len(label) <= 3 else 9, "Bold")
    d.text((20, 23), label, font=fnt, fill=(255, 255, 255, 255), anchor="mm", stroke_width=1, stroke_fill=(30, 14, 0, 255))
    return img


NUTTY = "hd-185-1.hr-3369-61.ch-3109-1296.lg-3078-90.sh-305-90.ha-3254-1301"
IMAGER = "https://habnut.co.uk/imager/avatarimage"


def nutty(**params):
    """Nutty, Habnut's concierge, drawn by the hotel's own avatar imager."""
    import io
    import urllib.parse
    import urllib.request
    query = urllib.parse.urlencode(dict({"figure": NUTTY, "direction": 3, "head_direction": 3}, **params))
    request = urllib.request.Request(f"{IMAGER}?{query}", headers={"User-Agent": "Habnut brand kit"})
    data = urllib.request.urlopen(request, timeout=30).read()
    img = Image.open(io.BytesIO(data)).convert("RGBA")
    return img.crop(img.getbbox())


def fit(img, size):
    """img scaled down (never up) and centred at the bottom of a canvas of size."""
    img = img.copy()
    img.thumbnail(size, Image.NEAREST)
    out = Image.new("RGBA", size, (0, 0, 0, 0))
    out.alpha_composite(img, ((size[0] - img.width) // 2, size[1] - img.height))
    return out


def stop_sign():
    img = Image.new("RGBA", (34, 60), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle((15, 26, 18, 59), fill=(120, 120, 120, 255), outline=(40, 40, 40, 255))
    c, r = (17, 15), 14
    import math
    pts = [(c[0] + r * math.cos(math.radians(22.5 + 45 * i)), c[1] + r * math.sin(math.radians(22.5 + 45 * i))) for i in range(8)]
    d.polygon(pts, fill=(210, 30, 30, 255), outline=(60, 0, 0, 255))
    d.text(c, "STOP", font=font("PixelifySans.ttf", 8, "Bold"), fill=(255, 255, 255, 255), anchor="mm")
    return img


def mascots():
    standing = nutty(size="s")
    fit(standing, (32, 87)).save(os.path.join(OUT, "hint-nutty.png"))
    stop = Image.new("RGBA", (74, 96), (0, 0, 0, 0))
    body = nutty(size="n", direction=2, head_direction=2)
    body.thumbnail((50, 96), Image.NEAREST)
    stop.alpha_composite(body, (0, 96 - body.height))
    stop.alpha_composite(stop_sign(), (40, 30))
    stop.save(os.path.join(OUT, "stop-nutty.png"))
    trophy = Image.new("RGBA", (65, 82), (0, 0, 0, 0))
    d = ImageDraw.Draw(trophy)
    d.polygon([(14, 58), (51, 58), (56, 80), (9, 80)], fill=(150, 100, 20, 255), outline=(60, 34, 0, 255))
    d.rectangle((18, 50, 47, 58), fill=(226, 160, 30, 255), outline=(60, 34, 0, 255))
    head = nutty(size="n", headonly=1)
    head.thumbnail((44, 50), Image.NEAREST)
    trophy.alpha_composite(head, ((65 - head.width) // 2, 50 - head.height + 4))
    d.text((32, 70), "?", font=font("LilitaOne-Regular.ttf", 12), fill=(255, 236, 128, 255), anchor="mm")
    trophy.save(os.path.join(OUT, "trophy-nutty.png"))


def hotel_view(size=(3000, 1185)):
    """The client's hotel view: Habnut sky, sun and the wordmark."""
    view = sky(size)
    view.alpha_composite(sun(420), (size[0] - 760, 120))
    mark = wordmark(260)
    view.alpha_composite(mark, ((size[0] - mark.width) // 2, size[1] // 2 - mark.height))
    return view


def main():
    mascots()
    hotel_view().convert("RGB").save(os.path.join(OUT, "hotelview.png"), optimize=True)
    for h, name in ((48, "logo.png"), (120, "logo-large.png"), (28, "logo-small.png")):
        wordmark(h).save(os.path.join(OUT, name))
    for s in (192, 512, 32, 16, 28):
        icon(s).save(os.path.join(OUT, f"icon-{s}.png"))
    sky((64, 1185)).save(os.path.join(OUT, "sky.png"))
    sun(280).save(os.path.join(OUT, "sun.png"))
    for code, label, colours in RANKS:
        rank_badge(label, colours).save(os.path.join(OUT, f"{code}.png"))
    print("brand kit drawn into", OUT)


if __name__ == "__main__":
    main()
