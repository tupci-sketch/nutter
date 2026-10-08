#!/usr/bin/env python3
"""Fills the client's asset tree from the public asset archive.

    fetch-nitro-assets.py <gamedata-json-dir> <asset-root> [--only furniture,figure,icons,badges]

<gamedata-json-dir> holds FurnitureData.json and FigureMap.json from the
converter's official download. Written under <asset-root>:

    bundled/figure/<lib>.nitro          clothing, per FigureMap library
    bundled/furniture/<classname>.nitro  one per furniture artwork
    c_images/dcr/hof_furni/icons/<name>_icon.png
    c_images/album1584/<code>.gif       badges

Incremental: a file already on disk is skipped. Effects and pets come from
the converter's pre-converted zips (unpacked separately), since the archive
does not offer them converted.
"""
import concurrent.futures as cf
import json
import os
import sys
import time
import urllib.request

API = "https://www.habboassets.com/api/v2"
CDN = "https://cdn.habboassets.com"
UA = {"User-Agent": "habnut-asset-fetch", "Accept": "application/json"}


def get_json(url):
    for attempt in range(5):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
                return json.load(r)
        except Exception as e:  # rate limits and blips: back off and retry
            time.sleep(3 * (attempt + 1))
            last = e
    raise last


def fetch(url, dest):
    if os.path.exists(dest) and os.path.getsize(dest) > 0:
        return "skip"
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    for attempt in range(3):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA["User-Agent"]}), timeout=60) as r:
                data = r.read()
            tmp = dest + ".part"
            with open(tmp, "wb") as f:
                f.write(data)
            os.replace(tmp, dest)
            return "ok"
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return "404"
            time.sleep(2 * (attempt + 1))
        except Exception:
            time.sleep(2 * (attempt + 1))
    return "fail"


def run(jobs, label, workers=16):
    counts = {}
    with cf.ThreadPoolExecutor(workers) as pool:
        for i, res in enumerate(pool.map(lambda j: fetch(*j), jobs), 1):
            counts[res] = counts.get(res, 0) + 1
            if i % 1000 == 0:
                print(f"  {label}: {i}/{len(jobs)} {counts}", flush=True)
    print(f"{label}: {len(jobs)} wanted -> {counts}", flush=True)


def main():
    gd, root = sys.argv[1], sys.argv[2]
    only = set(sys.argv[sys.argv.index("--only") + 1].split(",")) if "--only" in sys.argv else {"furniture", "figure", "icons", "badges"}
    furni = json.load(open(os.path.join(gd, "FurnitureData.json")))
    types = furni["roomitemtypes"]["furnitype"] + furni["wallitemtypes"]["furnitype"]

    if "furniture" in only:
        newest = {}
        for t in types:
            base = t["classname"].split("*")[0]
            newest[base] = max(newest.get(base, 0), int(t.get("revision") or 0))
        jobs = [(f"{CDN}/derived/furni/{rev}/{name}/{name}.nitro", f"{root}/bundled/furniture/{name}.nitro")
                for name, rev in sorted(newest.items())]
        run(jobs, "furniture")

    if "icons" in only:
        jobs = []
        for t in types:
            name = t["classname"].replace("*", "_")
            jobs.append((f"{CDN}/images.habbo.com/dcr/hof_furni/{t.get('revision')}/{name}_icon.png",
                         f"{root}/c_images/dcr/hof_furni/icons/{name}_icon.png"))
        run(jobs, "icons")

    if "figure" in only:
        libs, page = {}, 1
        while True:
            d = get_json(f"{API}/swfs/libraries?kind=clothing&per_page=100&page={page}")
            for x in d["data"]:
                if x.get("nitro"):
                    libs[x["name"]] = x["nitro"]
            if page >= d["meta"]["last_page"]:
                break
            page += 1
        figuremap = json.load(open(os.path.join(gd, "FigureMap.json")))
        wanted = [lib["id"] for lib in figuremap.get("libraries", [])]
        missing = [w for w in wanted if w not in libs]
        print(f"figure: {len(wanted)} libraries in FigureMap, {len(libs)} offered, {len(missing)} not offered", flush=True)
        run([(libs[w], f"{root}/bundled/figure/{w}.nitro") for w in wanted if w in libs], "figure")
        with open(f"{root}/bundled/figure/.missing-from-archive.txt", "w") as f:
            f.write("\n".join(missing))

    if "badges" in only:
        codes, offset = [], 0
        while True:
            d = get_json(f"{API}/badges?hotel=com&limit=2000&offset={offset}")
            batch = d.get("data", [])
            codes += [b.get("code") for b in batch if b.get("code")]
            if len(batch) < 2000:
                break
            offset += 2000
        run([(f"{CDN}/images.habbo.com/c_images/album1584/{c}.gif", f"{root}/c_images/album1584/{c}.gif")
             for c in sorted(set(codes))], "badges")


if __name__ == "__main__":
    main()
