"""Writes seed/sql/public.sql: furnished public rooms for a new hotel.

  python3 scripts/gen-public-rooms.py scripts/public-rooms-dims.tsv apps/launcher/internal/seed/sql/public.sql

The dims file is each item's footprint (sprite_id, width, length, height,
can_sit) as habnut_items_base holds it after a furniture sync. Every placement
is checked against the room's heightmap and footprints, so nothing floats,
sinks, sits on a wall, blocks the door or overlaps.
"""
import sys

dims = {}
for line in open(sys.argv[1]):
    s, w, l, h, sit = line.split('\t')
    dims[s] = (int(w), int(l), float(h))

maps = {
 'model_lobby': ("xxxxxxxxxxxxxxxxxxxx|x2222222222222222222|x2222222222222222222|x1111111111111111111|"
                 "x1111111111111111111|00000000000000000000|x0000000000000000000|x0000000000000000000|"
                 "x0000000000000000000|x0000000000000000000|x0000000000000000000|x0000000000000000000|"
                 "xxxxxxxxxxxxxxxxxxxx", (0, 5)),
 'model_hall': ("xxxxxxxxxxxxxxxx|x000000000000000|x000000000000000|x000000000000000|x000000000000000|"
                "x000000000000000|0000000000000000|x000000000000000|x000000000000000|x000000000000000|"
                "x000000000000000|x000000000000000|xxxxxxxxxxxxxxxx", (0, 6)),
 'model_pool': ("xxxxxxxxxxxx|x00000000000|x00000000000|x00xxxxxx000|x00xxxxxx000|000xxxxxx000|"
                "x00xxxxxx000|x00000000000|x00000000000|xxxxxxxxxxxx", (0, 5)),
 'model_b': ("xxxxxxxxxxxx|x00000000000|x00000000000|x00000000000|x00000000000|000000000000|"
             "x00000000000|x00000000000|x00000000000|x00000000000|x00000000000|xxxxxxxxxxxx", (0, 5)),
}

# (name, description, model, wallpaper, floor, landscape, max, items)
# items: (classname, x, y, rotation) — rotation 0/4 face the room's two
# depths, 2/6 its two sides.
rooms = [
 ("Habnut Lobby", "Where everybody arrives. Grab a seat.", 'model_lobby', '201', '301', '1.1', 50, [
    ('sofa_polyfon', 3, 1, 4), ('sofa_polyfon', 7, 1, 4), ('sofa_polyfon', 12, 1, 4), ('sofa_polyfon', 16, 1, 4),
    ('plant_yukka', 1, 1, 0), ('plant_yukka', 19, 1, 0), ('lamp_basic', 10, 1, 0),
    ('chair_norja', 3, 3, 4), ('chair_norja', 5, 3, 4), ('chair_norja', 14, 3, 4), ('chair_norja', 16, 3, 4),
    ('plant_pineapple', 9, 3, 0), ('plant_pineapple', 11, 3, 0),
    ('carpet_standard', 8, 6, 0),
    ('couch_norja', 3, 7, 4), ('table_norja_med', 3, 8, 0), ('couch_norja', 3, 10, 0),
    ('couch_norja', 14, 7, 4), ('table_norja_med', 14, 8, 0), ('couch_norja', 14, 10, 0),
    ('lamp_armas', 1, 11, 0), ('lamp_armas', 19, 11, 0), ('plant_big_cactus', 19, 6, 0),
    ('doormat_plain', 1, 5, 0),
 ]),
 ("The Café", "Tables by the window, coffee on the counter.", 'model_hall', '110', '205', '1.3', 40, [
    ('bardesk_polyfon', 2, 1, 4), ('bardesk_polyfon', 4, 1, 4), ('bar_polyfon', 6, 1, 4),
    ('bar_chair_armas', 2, 3, 0), ('bar_chair_armas', 3, 3, 0), ('bar_chair_armas', 4, 3, 0), ('bar_chair_armas', 5, 3, 0),
    ('table_armas', 9, 2, 0), ('small_chair_armas', 8, 2, 2), ('small_chair_armas', 8, 3, 2),
    ('small_chair_armas', 11, 2, 6), ('small_chair_armas', 11, 3, 6),
    ('table_armas', 9, 8, 0), ('small_chair_armas', 8, 8, 2), ('small_chair_armas', 8, 9, 2),
    ('small_chair_armas', 11, 8, 6), ('small_chair_armas', 11, 9, 6),
    ('table_armas', 3, 8, 0), ('bench_armas', 3, 7, 4), ('bench_armas', 3, 10, 0),
    ('plant_rose', 15, 1, 0), ('plant_rose', 15, 11, 0), ('lamp_armas', 13, 1, 0), ('lamp_armas', 13, 11, 0),
    ('shelves_norja', 1, 1, 2),
 ]),
 ("The Pool", "Deep end on the left. No running.", 'model_pool', '401', '402', '1.4', 30, [
    ('summer_chair*1', 1, 1, 4), ('summer_chair*1', 2, 1, 4), ('summer_chair*1', 9, 1, 4), ('summer_chair*1', 10, 1, 4),
    ('summer_chair*1', 1, 8, 0), ('summer_chair*1', 2, 8, 0), ('summer_chair*1', 9, 8, 0), ('summer_chair*1', 10, 8, 0),
    ('plant_big_cactus', 11, 1, 0), ('plant_big_cactus', 11, 8, 0),
    ('plant_pineapple', 10, 4, 0), ('plant_pineapple', 11, 5, 0),
 ]),
 ("The Lounge", "Somewhere quiet to sit and talk.", 'model_b', '305', '108', '1.1', 25, [
    ('carpet_polar', 5, 4, 0),
    ('sofa_polyfon', 5, 2, 4), ('sofachair_polyfon', 4, 5, 2), ('sofachair_polyfon', 7, 5, 6),
    ('table_polyfon_med', 5, 5, 0), ('sofa_polyfon', 5, 7, 0),
    ('tv_luxus', 11, 4, 0), ('lamp_basic', 1, 1, 0), ('lamp_basic', 11, 1, 0),
    ('plant_yukka', 1, 10, 0), ('plant_yukka', 11, 10, 0), ('shelves_norja', 9, 1, 4), ('shelves_norja', 10, 1, 4),
 ]),
]

def tiles(rows):
    return [list(r) for r in rows.split('|')]

out = ["-- Public rooms for a new hotel: furnished, owned by the first administrator.",
       "-- Generated from the room shapes and each item's real footprint; every",
       "-- placement is on floor, at the floor's height, with nothing overlapping.",
       "-- Safe to run again: a room or item already there is left alone.", ""]
for name, desc, model, wall, floor, land, maxv, items in rooms:
    grid = tiles(maps[model][0])
    door = maps[model][1]
    used = {}
    rows = []
    for cls, x, y, rot in items:
        w, l, h = dims[cls]
        if rot in (2, 6):
            w, l = l, w
        heights = set()
        for dx in range(w):
            for dy in range(l):
                tx, ty = x + dx, y + dy
                c = grid[ty][tx] if 0 <= ty < len(grid) and 0 <= tx < len(grid[ty]) else 'x'
                assert c != 'x', f"{name}: {cls} at {x},{y} is off the floor at {tx},{ty}"
                assert (tx, ty) != door, f"{name}: {cls} blocks the door"
                heights.add(int(c))
                stackable = h == 0.0  # rugs: things stand on them
                prev = used.get((tx, ty))
                assert prev is None or prev == 'rug' or stackable, f"{name}: {cls} overlaps at {tx},{ty}"
                used[(tx, ty)] = 'rug' if stackable else cls
        assert len(heights) == 1, f"{name}: {cls} straddles two heights"
        rows.append((cls, x, y, heights.pop(), rot))
    q = name.replace("'", "''")
    out.append(f"INSERT INTO habnut_rooms (name, description, owner_id, model_id, category_id, world_id, access_type,\n"
               f"  max_visitors, is_promoted, wallpaper, floor_pattern, landscape_colour)\n"
               f"SELECT '{q}', '{desc.replace(chr(39), chr(39)*2)}', u.id, '{model}', 1, 'classic', 0, {maxv}, 1, '{wall}', '{floor}', '{land}'\n"
               f"FROM habnut_users u WHERE u.`rank` >= 7\n"
               f"  AND NOT EXISTS (SELECT 1 FROM habnut_rooms WHERE name = '{q}' AND world_id = 'classic')\n"
               f"ORDER BY u.id LIMIT 1;\n")
    # rugs first, so the furniture placed on them stands on them
    rows.sort(key=lambda r: dims[r[0]][2] != 0.0)
    for cls, x, y, z, rot in rows:
        out.append(f"INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)\n"
                   f"SELECT b.id, r.id, r.owner_id, {x}, {y}, {z}, {rot}, 0 FROM habnut_rooms r\n"
                   f"JOIN habnut_items_base b ON b.sprite_id = '{cls}'\n"
                   f"WHERE r.name = '{q}' AND r.world_id = 'classic'\n"
                   f"  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = {x} AND e.y = {y});")
    out.append("")
open(sys.argv[2], 'w').write("\n".join(out))
print("ok:", sum(len(r[7]) for r in rooms), "items in", len(rooms), "rooms")
