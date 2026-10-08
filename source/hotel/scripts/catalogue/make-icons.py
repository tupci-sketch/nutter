#!/usr/bin/env python3
"""Cuts shop icons out of furniture bundles, for furniture whose icon the
archive does not have.

    make-icons.py <FurnitureData.json> <bundled/furniture> <icons dir>

Every bundle carries an <name>_icon_a sprite; colour variants (name*3) use
the base icon. Needs Pillow.
"""
import io
import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
import nitro  # noqa: E402

fd_path, bundle_dir, icon_dir = sys.argv[1:4]
fd = json.load(open(fd_path))
made, failed = 0, []
for section in ("roomitemtypes", "wallitemtypes"):
    for t in fd[section]["furnitype"]:
        c = t["classname"]
        base, _, colour = c.partition("*")
        name = f"{base}{'_' + colour if colour else ''}_icon.png"
        target = os.path.join(icon_dir, name)
        bundle = os.path.join(bundle_dir, base + ".nitro")
        if not base or os.path.exists(target) or not os.path.exists(bundle):
            continue
        try:
            files = nitro.read(bundle)
            js = json.loads(next(v for k, v in files.items() if k.endswith(".json")))
            sheet = Image.open(io.BytesIO(next(v for k, v in files.items() if k.endswith(".png")))).convert("RGBA")
            frames = js["spritesheet"]["frames"]
            key = next((k for k in frames if k.endswith("_icon_a") or k.endswith("_icon_a.png")), None)
            if key is None:
                failed.append(c)
                continue
            f = frames[key]["frame"]
            icon = sheet.crop((f["x"], f["y"], f["x"] + f["w"], f["y"] + f["h"]))
            icon.save(target)
            made += 1
        except Exception as e:  # one broken bundle must not stop the rest
            failed.append(f"{c}: {e}")
print(f"made {made} icons; no icon sprite for {len(failed)}: {failed[:10]}")
