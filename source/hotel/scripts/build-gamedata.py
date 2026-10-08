#!/usr/bin/env python3
"""Builds the client's gamedata directory for Habnut.

    build-gamedata.py <converter-json-dir> <productdata.xml> <octane-config-dir> <out-dir> <english-texts.json> <furnidata.xml>

The furnidata and productdata XML are habbo.com's (gamedata/furnidata_xml/1,
gamedata/productdata/1): the converter's copies come from whichever hotel it
reached, and names are taken from the English ones by classname.

Takes the official files the converter downloaded (FurnitureData, FigureData,
FigureMap, EffectMap, HabboAvatarActions, ExternalTexts), adds ProductData
built from the official productdata XML, and the client's English UI texts.
Player-visible text is rebranded to Habnut; identifiers are left alone.
"""
import json
import os
import re
import shutil
import sys
import xml.etree.ElementTree as ET

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from habnut_brand import rebrand  # noqa: E402  the one rule for visible text


def main():
    src, productdata, octane_cfg, out, english, furnixml = sys.argv[1:7]
    names = {t.get("classname"): (t.findtext("name") or "", t.findtext("description") or "")
             for t in ET.parse(furnixml).getroot().iter("furnitype")}
    os.makedirs(out, exist_ok=True)

    for name in ("FigureData.json", "FigureMap.json", "EffectMap.json", "HabboAvatarActions.json"):
        shutil.copyfile(os.path.join(src, name), os.path.join(out, name))

    furni = json.load(open(os.path.join(src, "FurnitureData.json")))
    for section in ("roomitemtypes", "wallitemtypes"):
        for t in furni[section]["furnitype"]:
            name, desc = names.get(t["classname"], (t.get("name"), t.get("description")))
            t["name"] = rebrand(name)
            t["description"] = rebrand(desc)
    # Habnut's own furniture (exclusives/make-exclusives.py) lives in an overlay.
    overlay = os.path.join(out, "FurnitureData.habnut.json")
    if os.path.exists(overlay):
        extra = json.load(open(overlay))
        for section in ("roomitemtypes", "wallitemtypes"):
            ours = {t["classname"] for t in extra.get(section, [])}
            furni[section]["furnitype"] = [t for t in furni[section]["furnitype"] if t["classname"] not in ours] + extra.get(section, [])
    json.dump(furni, open(os.path.join(out, "FurnitureData.json"), "w"), ensure_ascii=False, separators=(",", ":"))

    # The converter's texts come from whichever hotel it reached; the English
    # set comes from fetch-english-texts.py.
    texts = json.load(open(english))
    texts = {k: rebrand(v) for k, v in texts.items()}
    # Habnut's own texts (badges/make-badges.py and friends) live in an overlay.
    text_overlay = os.path.join(out, "ExternalTexts.habnut.json")
    if os.path.exists(text_overlay):
        texts.update(json.load(open(text_overlay)))
    json.dump(texts, open(os.path.join(out, "ExternalTexts.json"), "w"), ensure_ascii=False, separators=(",", ":"))

    root = ET.parse(productdata).getroot()
    products = [{"code": p.get("code"), "name": rebrand(p.findtext("name") or ""),
                 "description": rebrand(p.findtext("description") or "")} for p in root.iter("product")]
    json.dump({"productdata": {"product": products}}, open(os.path.join(out, "ProductData.json"), "w"),
              ensure_ascii=False, separators=(",", ":"))

    ui = open(os.path.join(octane_cfg, "UITexts_en.jsonc.example"), encoding="utf8").read()
    open(os.path.join(out, "UITexts.jsonc"), "w", encoding="utf8").write(rebrand(ui))

    # The soundboard's pad list; empty until the hotel has sounds of its own.
    sound = os.path.join(out, "SoundData.json")
    if not os.path.exists(sound):
        json.dump({"categories": [], "sounds": []}, open(sound, "w"))

    print(f"gamedata: {sum(len(furni[s]['furnitype']) for s in ('roomitemtypes', 'wallitemtypes'))} furniture, "
          f"{len(texts)} texts, {len(products)} products -> {out}")


if __name__ == "__main__":
    main()
