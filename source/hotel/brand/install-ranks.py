#!/usr/bin/env python3
"""Installs the staff rank badges drawn by make-brand.py.

    install-ranks.py <brand out dir> <nitro root>

GIFs into c_images/album1584 (where badges live), names and descriptions into
ExternalTexts.habnut.json and the live ExternalTexts.json. Needs Pillow.
"""
import importlib.util
import json
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("badges", os.path.join(HERE, "..", "badges", "make-badges.py"))
badges = importlib.util.module_from_spec(spec)
spec.loader.exec_module(badges)

src, root = sys.argv[1:3]
RANKS = {"HNVIP": ("Habnut VIP", "A Habnut VIP."), "HNHLP": ("Habnut Helper", "Helps new Habnuts find their feet."),
         "HNSUP": ("Habnut Support", "Part of the Habnut support team."), "HNMOD": ("Habnut Moderator", "Keeps Habnut safe and fun."),
         "HNSMD": ("Habnut Senior Moderator", "Leads the Habnut moderators."), "HNADM": ("Habnut Administrator", "Runs Habnut."),
         "HNOWN": ("Habnut Owner", "Owns Habnut.")}
texts = {}
for code, (name, desc) in RANKS.items():
    badges.to_gif(Image.open(os.path.join(src, code + ".png")).convert("RGBA"), os.path.join(root, "c_images", "album1584", code + ".gif"))
    texts[f"badge_name_{code}"], texts[f"badge_desc_{code}"] = name, desc
for name in ("ExternalTexts.habnut.json", "ExternalTexts.json"):
    path = os.path.join(root, "gamedata", name)
    data = json.load(open(path)) if os.path.exists(path) else {}
    data.update(texts)
    json.dump(data, open(path + ".part", "w"), ensure_ascii=False, indent=1 if "habnut" in name else None,
              separators=None if "habnut" in name else (",", ":"))
    os.replace(path + ".part", path)
print(len(RANKS), "rank badges installed")
