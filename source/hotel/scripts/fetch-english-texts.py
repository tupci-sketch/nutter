#!/usr/bin/env python3
"""Fetches the English (habbo.com) external texts from the asset archive into
a flat JSON map, the shape the client's ExternalTexts.json takes.

    fetch-english-texts.py <out.json>
"""
import json, sys, time, urllib.request

URL = "https://www.habboassets.com/api/v2/gamedata?type=texts&sort=key&per_page=500&page={}"
H = {"Accept": "application/json", "X-Hotel-ID": "1", "User-Agent": "habnut-texts"}

texts, page = {}, 1
while True:
    for attempt in range(5):
        try:
            with urllib.request.urlopen(urllib.request.Request(URL.format(page), headers=H), timeout=60) as r:
                d = json.load(r)
            break
        except Exception:
            time.sleep(5 * (attempt + 1))
    rows = [row for block in d.get("data", []) for row in block.get("rows", [])]
    for row in rows:
        texts[row["key"]] = row["val"]
    meta = d.get("meta", {})
    if not rows or page >= meta.get("last_page", page):
        break
    page += 1
json.dump(texts, open(sys.argv[1], "w"), ensure_ascii=False)
print(f"{len(texts)} English texts in {page} pages")
