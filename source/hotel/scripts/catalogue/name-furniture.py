#!/usr/bin/env python3
"""Gives readable names to furniture whose name is a placeholder.

    name-furniture.py <FurnitureData.json> <pages.tsv> <out.json>

Some furniture comes with no real name: "pcnc_bbq name", "lidowall1",
"minirare_fan_8 name" or nothing at all. A name is made from the classname
instead: known abbreviations spelled out, run-together words split
(wordninja), and colour variants (*7) named after their colour. pages.tsv
(classname, type, catalogue page) gives placeholder descriptions a line
about where the item comes from.

The result, {classname: {"name", "description"}}, is applied by
build-gamedata.py as FurnitureNames.habnut.json. Needs: pip install wordninja.
"""
import json
import re
import sys

import wordninja

# Abbreviations and odd spellings in classnames, as players should read them.
WORDS = {
    "pcnc": "Picnic", "dvdr": "Divider", "dvdrtile": "Divider Tile", "bbq": "BBQ", "sf": "Sci-Fi", "lon": "London",
    "bg": "Background", "mpu": "Billboard", "minirare": "Mini", "noob": "Starter", "hween": "Halloween",
    "xmas": "Christmas", "val": "Valentine", "jp": "Japanese", "exe": "Executive", "hc": "Club", "tv": "TV",
    "dj": "DJ", "lt": "Lost Tribe", "habbo": "Habnut", "habnut": "Habnut", "hoh": "Hall of Fame", "win": "Winner",
    "info": "Info", "mutearea": "Mute Area", "prizetrophy": "Prize Trophy", "pura": "Pura", "uk": "UK", "us": "US",
    "usa": "USA", "diy": "DIY", "ufo": "UFO", "cd": "CD", "dvd": "DVD", "pc": "PC", "led": "LED", "wc": "WC",
    "nye": "New Year", "eleg": "Elegant", "elegrass": "Elegant Grass", "lido": "Lido", "trax": "Trax",
    "scifi": "Sci-Fi", "scifiport": "Sci-Fi Port", "scifirocket": "Sci-Fi Rocket", "bw": "Black and White",
    "mrare": "Mini Rare", "shishi": "Shishi", "anc": "Ancient", "cny": "Lunar New Year", "fx": "Effect",
    "cnstr": "Construction", "kiddy": "Kiddie", "gld": "Gold", "slv": "Silver", "brnz": "Bronze", "rbw": "Rainbow",
    "nt": "Neon", "lm": "Limited", "md": "Medium", "sm": "Small", "lg": "Large", "s": "", "tut": "Welcome",
    "colourable": "Colourable", "shishilamp": "Shishi Lamp", "eleblock": "Elevation Block", "chocapic": "Chocapic",
    "partingscreen": "Parting Screen", "bardeskcorner": "Bar Desk Corner", "mobi": "Mobile", "vip": "VIP", "rp": "Roleplay", "hd": "HD", "hq": "HQ", "npc": "NPC", "wf": "Wired",
}
DROP = re.compile(r"^(?:c|r|h|ltd|b|cy|ny|v|nft|w)\d+$|^(?:ads|nft|room|name|desc|tradeable|cloth|clothing|furni)$")
# A colour per variant: named after the variant's main part colour.
PALETTE = {
    "Red": (200, 40, 40), "Dark Red": (120, 20, 20), "Pink": (240, 130, 180), "Orange": (240, 140, 30),
    "Yellow": (240, 220, 50), "Gold": (210, 170, 60), "Lime": (160, 220, 60), "Green": (60, 160, 60),
    "Dark Green": (30, 90, 40), "Teal": (40, 160, 160), "Turquoise": (60, 210, 210), "Light Blue": (130, 190, 240),
    "Blue": (50, 100, 210), "Navy": (30, 40, 110), "Purple": (130, 60, 170), "Lilac": (190, 150, 220),
    "Brown": (120, 80, 40), "Beige": (220, 200, 160), "Black": (25, 25, 25), "Grey": (130, 130, 130),
    "Silver": (190, 190, 195), "White": (245, 245, 245),
}


def colour_name(hexes):
    for h in hexes:
        h = h.lstrip("#")
        if len(h) != 6 or h.lower() in ("000000", "ffffff"):
            continue
        rgb = tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))
        return min(PALETTE, key=lambda n: sum((a - b) ** 2 for a, b in zip(PALETTE[n], rgb)))
    return None


def word(token):
    t = token.lower()
    if t in WORDS:
        return WORDS[t]
    m = re.fullmatch(r"([a-z]+)(\d+)", t)
    if m and m.group(1) in WORDS:
        return f"{WORDS[m.group(1)]} {int(m.group(2))}"
    if m:
        return f"{word(m.group(1))} {int(m.group(2))}"
    if t.isdigit():
        return str(int(t))
    if len(t) <= 3:
        return t.upper() if not re.search(r"[aeiouy]", t) else t.capitalize()
    # split only long run-together words, and never into single letters ("sofa" is not "S Of A")
    parts = wordninja.split(t)
    if len(t) < 7 or len(parts) < 2 or any(len(w) == 1 for w in parts) or sum(len(w) <= 2 for w in parts) > 1:
        return t.capitalize()
    return " ".join(w if w.isdigit() else (WORDS.get(w) or w.capitalize()) for w in parts)


def humanise(classname):
    base = classname.split("*")[0]
    tokens = [t for t in re.split(r"[_\s]+", base) if t and not DROP.match(t.lower())]
    # a trailing number names one of a series: "#14"
    number = None
    if len(tokens) > 1 and tokens[-1].isdigit():
        number = str(int(tokens.pop()))
    words = " ".join(w for w in (word(t) for t in tokens) if w)
    words = re.sub(r"\b(\w+)( \1\b)+", r"\1", words)  # "Habnut Habnut" -> "Habnut"
    return (words + (f" #{number}" if number else "")).strip()


def placeholder(text, classname):
    t = (text or "").strip()
    return (not t or "_" in t or t.endswith((" name", " desc", " text")) or t.lower() == classname.lower()
            or t.lower() == classname.split("*")[0].lower() or bool(re.fullmatch(r"[a-z0-9]+", t)))


def main():
    furni_path, pages_path, out_path = sys.argv[1:4]
    furni = json.load(open(furni_path))
    page_of = {}
    for line in open(pages_path):
        parts = line.rstrip("\n").split("\t")
        if len(parts) == 3:
            page_of.setdefault(parts[0], parts[2])
    out = {}
    for section in ("roomitemtypes", "wallitemtypes"):
        for t in furni[section]["furnitype"]:
            cls = t["classname"]
            if cls in ("floor", "wallpaper", "landscape"):
                continue
            name, desc = t.get("name") or "", t.get("description") or ""
            new_name = new_desc = None
            if re.fullmatch(r"[a-z0-9]+(?: [a-z0-9]+)+", name.strip()) and not name.strip().endswith((" name", " desc", " text")):
                new_name = name.strip().title()  # "starter lamp"
            elif placeholder(name, cls):
                new_name = humanise(cls)
                if "*" in cls:
                    colour = colour_name(t.get("partcolors", {}).get("color", []))
                    new_name += f" ({colour})" if colour else f" #{cls.split('*')[1]}"
                if not new_name:
                    new_name = None
            if placeholder(desc, cls) or (new_name and desc.strip() == name.strip()):
                page = page_of.get(cls)
                if cls.startswith("clothing_") or "_cloth_" in cls:
                    new_desc = "Open it from your inventory to add it to your wardrobe."
                elif page:
                    new_desc = f"From the {page} collection."
                else:
                    new_desc = "A piece of Habnut furniture."
            if new_name or new_desc:
                out[cls] = {"name": new_name or name, "description": new_desc or desc}
    json.dump(dict(sorted(out.items())), open(out_path, "w"), ensure_ascii=False, indent=1)
    print(f"{len(out)} names and descriptions -> {out_path}")


if __name__ == "__main__":
    main()
