#!/usr/bin/env python3
"""Applies Habnut's text rule (habnut_brand.py) to the live gamedata.

    rebrand-gamedata.py <gamedata dir>

Only what players read: text values, furniture and product names and
descriptions. Keys, class names and furniture lines stay as they are.
"""
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from habnut_brand import rebrand  # noqa: E402

out = sys.argv[1]
changed = 0


def save(name, data):
    path = os.path.join(out, name)
    json.dump(data, open(path + ".part", "w"), ensure_ascii=False, separators=(",", ":"))
    os.replace(path + ".part", path)


for name in ("ExternalTexts.json", "ExternalTexts.habnut.json"):
    path = os.path.join(out, name)
    if not os.path.exists(path):
        continue
    texts = json.load(open(path))
    new = {k: rebrand(v) for k, v in texts.items()}
    changed += sum(1 for k in texts if texts[k] != new[k])
    save(name, new)

for name in ("FurnitureData.json", "FurnitureData.habnut.json"):
    path = os.path.join(out, name)
    if not os.path.exists(path):
        continue
    fd = json.load(open(path))
    for section in ("roomitemtypes", "wallitemtypes"):
        types = fd[section]["furnitype"] if "furnitype" in fd.get(section, {}) else fd.get(section, [])
        for t in types:
            for field in ("name", "description"):
                if t.get(field) and rebrand(t[field]) != t[field]:
                    t[field] = rebrand(t[field])
                    changed += 1
    save(name, fd)

pd_path = os.path.join(out, "ProductData.json")
pd = json.load(open(pd_path))
for p in pd["productdata"]["product"]:
    for field in ("name", "description"):
        if p.get(field) and rebrand(p[field]) != p[field]:
            p[field] = rebrand(p[field])
            changed += 1
save("ProductData.json", pd)

ui_path = os.path.join(out, "UITexts.jsonc")
ui = open(ui_path, encoding="utf8").read()
ui_new = re.sub(r'(":\s*")((?:\\.|[^"\\])*)(")', lambda m: m.group(1) + rebrand(m.group(2)) + m.group(3), ui)
changed += ui != ui_new
open(ui_path, "w", encoding="utf8").write(ui_new)

print(f"rebranded {changed} texts in {out}")
