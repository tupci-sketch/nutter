#!/usr/bin/env python3
"""Adds every piece of furniture the hotel has artwork for to the emulator and
the shop, and writes the SQL for it.

    import-furniture.py <db-root-password> <bundles.json> > furniture.sql

<bundles.json> comes from scan-bundles.py: each artwork bundle's logic type,
size, height and number of states, which is how an item behaves. Items the
emulator already knows are left alone. New items get a definition built from
their bundle and the furniture data, and a page in the shop chosen by their
furniture line. Left out: real-world brand tie-ins, credit-exchange furniture
(an economy hole if sold at the wrong price) and wired boxes the emulator has
no behaviour for (a wired box must never do nothing).

Safe to run again: everything is keyed by class name and page id.
"""
import collections
import json
import re
import subprocess
import sys
import zlib

PW, BUNDLES = sys.argv[1], sys.argv[2]
GAMEDATA = "/srv/habnut/nitro/gamedata/FurnitureData.json"
INTERACTIONS = __import__("os").path.join(__import__("os").path.dirname(__file__), "interactions.txt")

EXCLUDED_LINES = {"ad_sales", "ad_neopets", "sanrio", "credit_furni", "smiley"}


def query(sql):
    out = subprocess.run(["sudo", "docker", "exec", "-i", "-e", f"MYSQL_PWD={PW}", "hotel-db-1",
                          "mariadb", "-uroot", "-N", "-B", "habnut"],
                         input=sql, capture_output=True, text=True, check=True).stdout
    return [line.split("\t") for line in out.splitlines() if line]


def q(s):
    return "'" + str(s).replace("\\", "\\\\").replace("'", "''") + "'"


# ---- names --------------------------------------------------------------------

LINE_NAMES = {
    "hhistory_2024": "Hotel History", "habbo25": "Anniversary", "habbo_club_gifts": "Club Gifts",
    "nftmint": "Minted Collectibles", "nft": "Collectibles", "bonusrare": "Bonus Rares", "rare": "Classic Rares",
    "mode_gold": "Gold Mode", "diamond": "Diamond Furni", "recycler": "Recycler Prizes", "duckets": "Ducket Shop",
    "nt_newbie_room": "Starter Room", "lodge_dark": "Dark Lodge", "pj_party": "PJ Party", "cat_cafe": "Cat Cafe",
    "nyc": "New York", "skorea": "Seoul", "bubblejuice": "Bubble Juice", "misc": "Odds and Ends", "": "Odds and Ends",
    "buildersclub": "Builders Club", "wired": "More Wired", "trophies": "Trophies", "lost_tribe": "Lost Tribe",
    "vaporwave": "Vaporwave", "hygge": "Hygge", "olympus": "Olympus", "stellar": "Stellar",
}
SEASON = re.compile(r"^(easter|habboween|xmas|summer|fall|autumn|winter|spring|valentine|val|newyear|ny|halloween|lunar|cny)_?(\d{2,4})$")
SEASON_NAMES = {"habboween": "Habnutween", "halloween": "Halloween", "xmas": "Christmas", "easter": "Easter",
                "summer": "Summer", "fall": "Autumn", "autumn": "Autumn", "winter": "Winter", "spring": "Spring",
                "valentine": "Valentine's", "val": "Valentine's", "newyear": "New Year", "ny": "New Year",
                "lunar": "Lunar New Year", "cny": "Lunar New Year"}


def habnut(s):
    return re.sub(r"(?i)habb[oóòôö]", lambda m: "HABNUT" if m.group(0).isupper() else ("Habnut" if m.group(0)[0].isupper() else "habnut"), s)


def line_name(line):
    if line in LINE_NAMES:
        return LINE_NAMES[line]
    m = re.match(r"^nft(\d{4})$", line)
    if m:
        return f"Collectibles {m.group(1)}"
    m = SEASON.match(line)
    if m:
        year = m.group(2)
        year = "20" + year if len(year) == 2 else year
        return f"{SEASON_NAMES[m.group(1)]} {year}"
    if line.startswith("buildersclub_"):
        return "Builders Club " + line.split("_", 1)[1].replace("_", " ").title()
    return habnut(line.replace("_", " ").strip().title())


# ---- where things go ------------------------------------------------------------

FURNI, CLOTHING, WIRED, CLUB, LINES, BUILDERS = 2, 3, 218, 8, 209, 222
PAGES = {
    # id: (parent, caption, layout, icon, text)
    5001: (FURNI, "Collectibles", "pets3", 215, "Every collectible the hotel has ever minted, all in one place. Pick a year."),
    5002: (FURNI, "Hotel History", "default_3x3", 258, "Pieces from every era of the hotel, back on sale."),
    5003: (FURNI, "Seasons", "pets3", 195, "Easter, Habnutween, Christmas and every summer since: the seasonal ranges, all year round."),
    5004: (FURNI, "Rares", "pets3", 145, "The good stuff. Paid in diamonds, kept forever, envied by everyone."),
    5005: (FURNI, "Ducket Shop", "default_3x3", 146, "Spend your duckets here: furniture you cannot buy with credits."),
    5006: (WIRED, "More Wired", "default_3x3", 80, "Extra wired triggers, effects, conditions and selectors."),
    5007: (CLUB, "More Club Gifts", "default_3x3", 172, "Gifts from the club, for club members."),
    5008: (FURNI, "Trophies", "default_3x3", 197, "Engrave one and award it."),
    5010: (CLOTHING, "Wardrobe", "pets3", 74, "Clothing you can place, then wear: click it in your room to add it to your wardrobe."),
    5011: (5010, "Collectible Outfits", "default_3x3", 74, "Outfits from the collectible drops."),
    5012: (5010, "Seasonal Outfits", "default_3x3", 74, "Costumes and outfits from the seasons."),
    5013: (5010, "More Outfits", "default_3x3", 74, "Everything else to wear."),
}
SUBPAGE_PARENT = {"collectibles": 5001, "seasons": 5003, "rares": 5004, "lines": LINES, "builders": BUILDERS}
SINGLE_PAGE = {"history": 5002, "duckets": 5005, "wired": 5006, "club": 5007, "trophies": 5008}


def group_of(t, b):
    line = t.get("furniline") or ""
    c = t["classname"]
    if line in EXCLUDED_LINES or line.startswith("ad_") or re.search(r"(^|_|\d)test", c) or (c.startswith("clothing_") and c.endswith("test")):
        return None
    if b["logic"] == "furniture_purchasable_clothing":
        if line.startswith("nft"):
            return ("clothing", 5011)
        if SEASON.match(line):
            return ("clothing", 5012)
        return ("clothing", 5013)
    if c.startswith("wf_") or line == "wired":
        return ("wired", "wired")
    if line.startswith("nft"):
        return ("collectibles", line)
    if line == "hhistory_2024":
        return ("history", line)
    if line in ("rare", "bonusrare", "mode_gold", "diamond", "recycler"):
        return ("rares", line)
    if line == "duckets":
        return ("duckets", line)
    if line == "habbo_club_gifts":
        return ("club", line)
    if line.startswith("buildersclub"):
        return ("builders", line)
    if SEASON.match(line):
        return ("seasons", line)
    if line == "trophies":
        return ("trophies", line)
    return ("lines", line)


# ---- behaviour ----------------------------------------------------------------

LOGIC = {
    "furniture_purchasable_clothing": "clothing", "furniture_multiheight": "multiheight", "furniture_trophy": "trophy",
    "furniture_dice": "dice", "furniture_effectbox": "fx_box", "furniture_guild_customized": "guild_furni",
    "furniture_high_score": "wf_highscore", "furniture_lovelock": "love_lock", "furniture_youtube": "youtube",
    "furniture_badge_display": "badge_display", "furniture_stickie": "postit", "furniture_jukebox": "jukebox",
    "furniture_sound_machine": "trax_machine", "furniture_external_image_wallitem": "external_image",
    "furniture_counter_clock": "game_timer", "furniture_custom_stack_height": "stack_helper",
    "furniture_rentable_space": "rentable_space", "furniture_fireworks": "fireworks", "furniture_mannequin": "mannequin",
    "furniture_habbowheel": "colorwheel", "furniture_background_color": "background_toner",
    "furniture_roomdimmer": "dimmer", "furniture_one_way_door": "onewaygate",
}
CATEGORY = {"gate": "gate", "teleport": "teleport", "roller": "roller", "dimmer": "dimmer", "tent": "tent",
            "vending_machine": "vendingmachine", "bed": "bed"}


def behaviour(t, b, valid):
    c, cat, logic = t["classname"], t.get("category") or "", b["logic"]
    if c.startswith("wf_"):
        return c if c in valid else None
    it = LOGIC.get(logic)
    if it is None and logic in ("furniture_multistate", "furniture_basic"):
        it = CATEGORY.get(cat)
        if it in ("gate", "teleport", "vendingmachine") and logic != "furniture_multistate":
            it = None
        if cat == "pets":
            name = c.lower()
            it = ("pet_food" if "food" in name else "pet_drink" if ("water" in name or "drink" in name)
                  else "nest" if ("nest" in name or "bed" in name or "basket" in name) else "pet_toy" if "toy" in name else None)
    it = it or "default"
    return it if it in valid else "default"


def heights(t, b, states):
    cp = (t.get("customparams") or "").strip()
    try:
        step = float(cp)
    except ValueError:
        step = round((b.get("z") or 1) / max(states, 1), 2) or 0.5
    return ";".join(f"{step * (i + 1):.2f}".rstrip("0").rstrip(".") for i in range(max(states, 1)))


# ---- prices -------------------------------------------------------------------

BASE_PRICE = {"chair": 4, "table": 5, "bed": 6, "lighting": 5, "divider": 4, "rug": 3, "floor": 3,
              "wall_decoration": 3, "window": 4, "shelf": 5, "gate": 6, "teleport": 12, "vending_machine": 8,
              "games": 8, "music": 6, "food": 2, "pets": 6, "sound_fx": 6, "trophy": 6}
DIAMONDS = {"rare": 25, "bonusrare": 15, "mode_gold": 8, "diamond": 10, "recycler": 12}


def price(t, group):
    """(credits, points, points type)"""
    line = t.get("furniline") or ""
    size = max(1, (t.get("xdim") or 1) * (t.get("ydim") or 1))
    credits = BASE_PRICE.get(t.get("category") or "", 5) + min(size - 1, 6)
    kind = group[0]
    if kind == "rares":
        return 0, DIAMONDS[line], 5
    if kind == "collectibles":
        return 10, 3, 5
    if kind == "duckets":
        return 0, 25 * credits, 0
    if kind == "clothing":
        return (0, 3, 5) if group[1] == 5011 else (5, 0, 0)
    return credits, 0, 0


# ---- main ---------------------------------------------------------------------

def main():
    fd = json.load(open(GAMEDATA))
    bundles = json.load(open(BUNDLES))
    valid = set(open(INTERACTIONS).read().split())
    known = {r[0] for r in query("SELECT item_name FROM items_base;")}
    # For sale on some page already; anything else with artwork gets one.
    in_shop = {r[0] for r in query("SELECT DISTINCT b.item_name FROM catalog_items ci"
                                   " JOIN items_base b ON FIND_IN_SET(b.id, REPLACE(ci.item_ids, ';', ','));")}
    vend = collections.Counter(r[0] for r in query("SELECT vending_ids FROM items_base WHERE interaction_type='vendingmachine';"))
    common_vend = vend.most_common(1)[0][0] if vend else "1"

    out = ["-- Every furniture with artwork, in the emulator and the shop. Generated by import-furniture.py."]
    for pid, (parent, caption, layout, icon, text) in PAGES.items():
        out.append(
            "INSERT INTO catalog_pages (id, parent_id, caption_save, caption, page_layout, icon_color, icon_image, min_rank,"
            " order_num, visible, enabled, club_only, vip_only, page_headline, page_teaser, page_special, page_text1,"
            " page_text2, page_text_details, page_text_teaser, room_id, includes, catalog_mode)"
            f" VALUES ({pid}, {parent}, {q(re.sub('[^a-z0-9]+', '_', caption.lower()))}, {q(caption)}, '{layout}', 1, {icon}, 1,"
            f" {pid}, '1', '1', '0', '0', '', '', '', {q(text)}, '', '', '', 0, '', 'NORMAL')"
            " ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id), caption=VALUES(caption), page_layout=VALUES(page_layout),"
            " icon_image=VALUES(icon_image), page_text1=VALUES(page_text1);")

    added, skipped = collections.Counter(), collections.Counter()
    pages_for_lines = {}
    # A line keeps the page it was given before; new lines get a free id.
    existing_pages = {(int(r[1]), r[2]): int(r[0]) for r in
                      query("SELECT id, parent_id, caption_save FROM catalog_pages WHERE id BETWEEN 6000 AND 8999;")}
    taken = {int(r[0]) for r in query("SELECT id FROM catalog_pages;")}
    page_items = collections.defaultdict(list)
    for section, kind in (("roomitemtypes", "s"), ("wallitemtypes", "i")):
        for t in fd[section]["furnitype"]:
            c = t["classname"]
            b = bundles.get(c.split("*")[0])
            if not c or b is None or "error" in b:
                skipped["no artwork"] += 1
                continue
            if c in known and c in in_shop:
                continue
            group = group_of(t, b)
            if group is None:
                skipped["left out"] += 1
                continue
            it = behaviour(t, b, valid)
            if it is None:
                skipped["wired without behaviour"] += 1
                continue

            states = max(1, b.get("states") or 1)
            z = float(b.get("z") or 0)
            if kind == "i":
                w, l, h, stack = 0, 0, 1.0, 1
            else:
                w, l = max(1, t.get("xdim") or 1), max(1, t.get("ydim") or 1)
                h, stack = round(min(max(z, 0.0), 40.0), 2), 1
            sit, lay, walk = int(bool(t.get("cansiton"))), int(bool(t.get("canlayon"))), int(bool(t.get("canstandon")))
            if walk and not sit and not lay:
                h = min(h, 0.1) if z <= 0.1 else h
            modes = states if it in ("default", "gate", "teleport", "dimmer", "onewaygate", "fireworks", "tent",
                                     "dice", "colorwheel", "game_timer", "multiheight", "vendingmachine") else 1
            vending = common_vend if it == "vendingmachine" else "0"
            multi = heights(t, b, states) if it == "multiheight" else "0"
            cp = (t.get("customparams") or "").strip()
            effect = int(cp) if it == "fx_box" and cp.isdigit() else 0
            name = (t.get("name") or c)[:56]
            if c not in known:
                out.append(
                    "INSERT INTO items_base (sprite_id, public_name, item_name, type, width, length, stack_height, allow_stack,"
                    " allow_sit, allow_lay, allow_walk, allow_gift, allow_trade, allow_recycle, allow_marketplace_sell,"
                    " allow_inventory_stack, interaction_type, interaction_modes_count, vending_ids, multiheight, customparams,"
                    " effect_id_male, effect_id_female, clothing_on_walk)"
                    f" SELECT {int(t['id'])}, {q(name)}, {q(c)}, '{kind}', {w}, {l}, {h}, {stack}, {sit}, {lay}, {walk}, 1, 1, 0, 1, 1,"
                    f" '{it}', {modes}, '{vending}', '{multi}', {q(cp if it in ('water', 'effect_tile') else '')}, {effect}, {effect}, ''"
                    f" FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM items_base WHERE item_name = {q(c)});")
            if it == "clothing" and cp:
                out.append(f"INSERT INTO catalog_clothing (name, setid) SELECT {q(c)}, {q(cp.replace(' ', ''))} FROM DUAL"
                           f" WHERE NOT EXISTS (SELECT 1 FROM catalog_clothing WHERE name = {q(c)});")

            # which page
            if group[0] == "clothing":
                page = group[1]
            elif group[0] in SINGLE_PAGE:
                page = SINGLE_PAGE[group[0]]
            else:
                key = (group[0], group[1])
                if key not in pages_for_lines:
                    pid = existing_pages.get((SUBPAGE_PARENT[group[0]], group[1] or "misc"))
                    if pid is None:
                        pid = 6000 + zlib.crc32(f"{group[0]}:{group[1]}".encode()) % 3000
                        while pid in taken or pid in pages_for_lines.values():
                            pid = 6000 + (pid - 6000 + 1) % 3000
                    taken.add(pid)
                    pages_for_lines[key] = pid
                page = pages_for_lines[key]
            page_items[page].append((t, group))
            added[group[0]] += 1

    for (kind, line), pid in sorted(pages_for_lines.items(), key=lambda kv: line_name(kv[0][1])):
        parent = SUBPAGE_PARENT[kind]
        colours = any("*" in t["classname"] for t, _ in page_items[pid])
        layout = "default_3x3_color_grouping" if colours else "default_3x3"
        caption = line_name(line)
        out.append(
            "INSERT INTO catalog_pages (id, parent_id, caption_save, caption, page_layout, icon_color, icon_image, min_rank,"
            " order_num, visible, enabled, club_only, vip_only, page_headline, page_teaser, page_special, page_text1,"
            " page_text2, page_text_details, page_text_teaser, room_id, includes, catalog_mode)"
            f" VALUES ({pid}, {parent}, {q(line or 'misc')}, {q(caption)}, '{layout}', 1, {PAGES.get(parent, (0, 0, 0, 197))[3] if parent in PAGES else 197}, 1,"
            f" 500, '1', '1', '0', '0', '', '', '', {q('The ' + caption + ' range, now in Habnut.')}, '', '', '', 0, '', 'NORMAL')"
            " ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id), caption=VALUES(caption), page_layout=VALUES(page_layout);")
    # Line pages under the same parent sorted by name.
    for parent in set(SUBPAGE_PARENT.values()):
        kids = sorted((line_name(l), pid) for (k, l), pid in pages_for_lines.items() if SUBPAGE_PARENT[k] == parent)
        for n, (_, pid) in enumerate(kids):
            out.append(f"UPDATE catalog_pages SET order_num = {600 + n} WHERE id = {pid};")

    for page, items in page_items.items():
        for n, (t, group) in enumerate(sorted(items, key=lambda x: (x[0].get("name") or "", x[0]["classname"]))):
            c = t["classname"]
            credits, points, ptype = price(t, group)
            club = "1" if group[0] == "club" else "0"
            out.append(
                "INSERT INTO catalog_items (item_ids, page_id, catalog_name, cost_credits, cost_points, points_type, amount,"
                " limited_stack, limited_sells, order_number, offer_id, song_id, extradata, have_offer, club_only)"
                f" SELECT b.id, {page}, {q(c)}, {credits}, {points}, {ptype}, 1, 0, 0, {n + 1}, -1, 0, '', '1', '{club}'"
                f" FROM items_base b WHERE b.item_name = {q(c)}"
                f" AND NOT EXISTS (SELECT 1 FROM catalog_items WHERE page_id = {page} AND catalog_name = {q(c)}) LIMIT 1;")

    print("\n".join(out))
    print(f"-- added {sum(added.values())}: {dict(added)}; skipped {dict(skipped)}; "
          f"{len(pages_for_lines)} line pages", file=sys.stderr)


if __name__ == "__main__":
    main()
