#!/usr/bin/env python3
"""Writes SQL for Habnut's two worlds: the hotel's furnished public rooms
and the city of Nutropolis, with its jobs.

    gen-public-rooms.py <db-root-password> > public-rooms.sql

Reads each room shape (heightmap, door) and each item's footprint and height
from the live database, and refuses any placement that is off the floor,
across two heights, on the door, or on top of something that is not a rug.
The rooms belong to the hotel's system account, listed under "Habnut" and
"Nutropolis" at the top of the navigator. City rooms are marked for the
Nutropolis plugin (habnut_rp_rooms), whose tables must exist: start the
emulator with the plugin once first. Safe to run again: a room that already
exists keeps its furniture.
"""
import os
import subprocess
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "rooms"))
from designer import design  # noqa: E402
from kits import KITS  # noqa: E402

PW = sys.argv[1]
# --refurnish: replace the hotel's own furniture in these rooms (never anyone else's)
REFURNISH = "--refurnish" in sys.argv
# --only=Name|Other Name: refurnish just these rooms
ONLY = {n for a in sys.argv if a.startswith("--only=") for n in a[7:].split("|")}


def query(sql):
    out = subprocess.run(["sudo", "docker", "exec", "-i", "-e", f"MYSQL_PWD={PW}", "hotel-db-1",
                          "mariadb", "-uroot", "-N", "-B", "--raw", "habnut"],
                         input=sql, capture_output=True, text=True, check=True).stdout
    return [line.split("\t") for line in out.splitlines() if line]


def heightmap(model):
    door_x, door_y, door_dir = query(f"SELECT door_x, door_y, door_dir FROM room_models WHERE name='{model}';")[0]
    raw = subprocess.run(["sudo", "docker", "exec", "-i", "-e", f"MYSQL_PWD={PW}", "hotel-db-1",
                          "mariadb", "-uroot", "-N", "-B", "--raw", "habnut"],
                         input=f"SELECT heightmap FROM room_models WHERE name='{model}';",
                         capture_output=True, text=True, check=True).stdout
    rows = [r.strip() for r in raw.replace("\r", "\n").split("\n") if r.strip()]
    return rows, (int(door_x), int(door_y))


def height_of(c):
    if c.lower() == "x":
        return None
    if c.isdigit():
        return int(c)
    if "a" <= c.lower() <= "z":
        return 10 + ord(c.lower()) - ord("a")
    return None


# name, description, model, max users, [(classname, x, y, rotation)]
HOTEL = [
    ("Habnut Lobby", "Welcome to Habnut! Sit down, say hello, make friends.", "newbie_lobby", 75, {"kit": "lounge"}),
    ("The Habnut Pub", "Pull up a stool. The drinks are on the house.", "pub_a", 50, {"kit": "pub", "fixed": [
        ("lodge_dark_bardesk", 10, 2, 2), ("lodge_dark_bardesk", 13, 2, 2),
        ("lodge_dark_barrelstool", 10, 3, 0), ("lodge_dark_barrelstool", 11, 3, 0), ("lodge_dark_barrelstool", 12, 3, 0),
        ("lodge_dark_barrelstool", 13, 3, 0), ("lodge_dark_barrelstool", 14, 3, 0), ("lodge_dark_barrelstool", 15, 3, 0),
    ]}),
    ("Community Garden", "Plant a flower (:plant), water it, harvest it (:harvest). Every harvest counts towards the hotel's weekly goal.",
     "picnic", 100, {"kit": "garden", "fixed": [
        # the pond: wade in to fill your watering can
        ("jungle_c16_watertile", 20, 8, 0), ("jungle_c16_watertile", 22, 8, 0), ("jungle_c16_watertile", 24, 8, 0),
        ("watering_can", 19, 7, 0), ("watering_can", 26, 7, 0), ("gardening_box", 18, 7, 0), ("garden_c15_toolshed", 28, 6, 0),
    ]}),
    ("The Gothic Cafe", "Cake, candles and good conversation.", "tearoom", 50, {"kit": "cafe"}),
    ("Rooftop Lounge", "The best view in the hotel.", "rooftop", 50, {"kit": "terrace"}),
    ("Oriental Garden", "A quiet garden of water, stone and maple.", "orient", 50, {"kit": "zen"}),
]

# Nutropolis: name, description, model, max users, (kind, safe from fights), job or None, furniture
CITY = [
    ("Nutropolis Central", "The heart of the city. Type :rphelp to get started.", "model_n", 75, ("spawn", 1), None, {"kit": "plaza"}),
    ("Back Alley", "Nobody comes here for a good reason. Fights allowed.", "park_b", 30, ("street", 0), None, {"kit": "grunge"}),
    ("Nut Bank", "Deposits and withdrawals: :deposit and :withdraw. Tellers wanted.", "model_3", 40, ("bank", 1),
     ("teller", "Nut Bank", "civil", 30, 10, "Teller;Senior Teller;Bank Manager", 0), {"kit": "office", "fixed": [
        ("bardesk_polyfon", 6, 4, 0), ("bardesk_polyfon", 8, 4, 0), ("bardesk_polyfon", 10, 4, 0), ("bardesk_polyfon", 12, 4, 0),
        ("bardeskcorner_polyfon", 14, 4, 0), ("exe_chair", 7, 3, 4), ("exe_chair", 11, 3, 4),
        ("safe_silo", 3, 1, 0), ("safe_silo", 4, 1, 0), ("safe_silo", 5, 1, 0)]}),
    ("Nutropolis Police", "Serve and protect. Officers clock in here.", "model_4", 40, ("police", 1),
     ("police", "Nutropolis Police", "police", 40, 10, "Cadet;Officer;Sergeant;Inspector;Chief of Police", 19), {"kit": "office", "fixed": [
        ("hc_exe_wrkdesk", 6, 14, 2), ("exe_chair", 7, 14, 6), ("safe_silo", 17, 1, 0), ("safe_silo", 18, 1, 0)]}),
    ("Nutropolis Jail", "Do the time.", "model_h", 25, ("jail", 1), None, {"kit": "grunge", "fixed": [
        ("bed_budget_one", 3, 10, 0), ("bed_budget_one", 10, 10, 0), ("prison_tower", 10, 2, 0)]}),
    ("Nutropolis General", "Hurt? Rest here and you will mend. Medics clock in here.", "model_x", 40, ("hospital", 1),
     ("medic", "Nutropolis General", "medic", 40, 10, "Trainee;Paramedic;Nurse;Doctor;Chief of Medicine", 20), {"kit": "hospital", "fixed": [
        ("exe_wrkdesk", 12, 3, 0), ("exe_chair", 12, 2, 4), ("hosptl_seat", 2, 2, 4), ("hosptl_seat", 5, 2, 4)]}),
    ("The Nut Cafe", "Coffee, gossip, and the odd brawl. Baristas wanted.", "model_3", 40, ("work", 0),
     ("barista", "The Nut Cafe", "civil", 25, 10, "Barista;Shift Lead;Cafe Manager", 0), {"kit": "cafe", "fixed": [
        ("diner_bardesk*1", 5, 3, 0), ("diner_bardesk*1", 6, 3, 0), ("diner_bardesk*1", 7, 3, 0), ("diner_bardesk*1", 8, 3, 0),
        ("diner_bardesk*1", 9, 3, 0), ("diner_bardesk*1", 10, 3, 0), ("diner_bardesk*1", 11, 3, 0)]}),
    ("Town Hall", "The seat of the city council.", "model_3", 40, ("townhall", 1),
     [("clerk", "Town Hall", "gov", 35, 10, "Clerk;Councillor;Deputy Mayor;Mayor", 0),
      ("judge", "Nutropolis Court", "gov", 45, 10, "Clerk of Court;Judge;Chief Justice", 0)], {"kit": "office", "fixed": [
        ("exe_table", 7, 2, 0), ("throne", 8, 1, 4),
        ("bench_armas", 5, 8, 0), ("bench_armas", 9, 8, 0), ("bench_armas", 5, 12, 0), ("bench_armas", 9, 12, 0)]}),
]


# City property for sale (paid from a citizen's Nutropolis bank): name, description, model, price.
# Empty: the buyer furnishes it. Shopfronts suit a business.
PROPERTIES = [
    ("Nutropolis Flat 1", "A cosy flat. For sale: type :buyproperty here.", "model_a", 600),
    ("Nutropolis Flat 2", "A bright flat. For sale: type :buyproperty here.", "model_b", 650),
    ("Nutropolis Flat 3", "A corner flat. For sale: type :buyproperty here.", "model_d", 650),
    ("Shopfront on Main Street", "Room for a business. For sale: type :buyproperty here.", "model_e", 1200),
    ("Shopfront on Market Row", "Room for a business. For sale: type :buyproperty here.", "model_f", 1200),
    ("The Nutropolis Penthouse", "The best address in the city. For sale: type :buyproperty here.", "model_k", 5000),
]


def furnish(name, model, spec, base_full):
    """A themed spec (kit + fixed pieces) designed into concrete furniture; a plain list as it is."""
    if isinstance(spec, list):
        return spec
    grid, door = heightmap(model)
    kit_base = {c: (w, l, h, sit, walk) for c, (bid, w, l, h, sit, walk) in base_full.items()}
    return design(grid, door, KITS[spec["kit"]], kit_base, spec.get("fixed", []))


def place(name, model, items, base):
    """Checks every item against the floor and each other; returns (base id, x, y, z, rot, height)."""
    grid, door = heightmap(model)
    used, placed = {}, []
    for cls, x, y, rot in items:
        bid, w, l, h = base[cls][:4]
        rug = base[cls][5] and h <= 0.1  # walkable and flat: things may stand on it
        if rot in (2, 6):
            w, l = l, w
        heights, lift = set(), 0.0
        for dx in range(w):
            for dy in range(l):
                tx, ty = x + dx, y + dy
                c = grid[ty][tx] if 0 <= ty < len(grid) and 0 <= tx < len(grid[ty]) else "x"
                z = height_of(c)
                if z is None:
                    sys.exit(f"{name}: {cls} at {x},{y} is off the floor at {tx},{ty}")
                if (tx, ty) == door:
                    sys.exit(f"{name}: {cls} at {x},{y} blocks the door")
                prev = used.get((tx, ty))
                if prev is not None and (not isinstance(prev, float) or rug):
                    sys.exit(f"{name}: {cls} at {x},{y} overlaps {prev} at {tx},{ty}")
                if isinstance(prev, float):
                    lift = max(lift, prev)  # stands on a rug
                used[(tx, ty)] = h if rug else cls
                heights.add(z)
        if len(heights) != 1:
            sys.exit(f"{name}: {cls} at {x},{y} straddles heights {sorted(heights)}")
        placed.append((bid, x, y, heights.pop() + lift, rot, h, rug))
    return placed


def sql_text(s):
    return "'" + s.replace("\\", "\\\\").replace("'", "''") + "'"


def room_sql(out, name, desc, model, users_max, category, placed):
    q = sql_text(name)
    out.append(f"INSERT INTO rooms (owner_id, owner_name, name, description, model, users_max, category, is_public, state, date_created)"
               f" SELECT 1, 'Habnut', {q}, {sql_text(desc)}, '{model}', {users_max}, 1, '1', 'open', UNIX_TIMESTAMP()"
               f" FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM rooms WHERE name={q} AND owner_id=1);")
    out.append(f"SET @room = (SELECT id FROM rooms WHERE name={q} AND owner_id=1 ORDER BY id LIMIT 1);")
    if REFURNISH and (not ONLY or name in ONLY):
        out.append("DELETE FROM items WHERE room_id = @room AND user_id = 1;")
    out.append("SET @fresh = (SELECT COUNT(*) = 0 FROM items WHERE room_id = @room AND user_id = 1);")
    # rugs first, so what stands on them is drawn on top
    for bid, x, y, z, rot, h, rug in sorted(placed, key=lambda p: not p[6]):
        out.append(f"INSERT INTO items (user_id, room_id, item_id, wall_pos, x, y, z, rot, extra_data, wired_data)"
                   f" SELECT 1, @room, {bid}, '', {x}, {y}, {z}, {rot}, '0', '' FROM DUAL WHERE @fresh;")
    out.append(f"INSERT INTO navigator_publics (public_cat_id, room_id, visible)"
               f" SELECT {category}, @room, '1' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM navigator_publics WHERE room_id=@room);")


def main():
    specs = [r[-1] for r in HOTEL] + [r[-1] for r in CITY]
    names = set()
    for spec in specs:
        if isinstance(spec, list):
            names |= {cls for cls, *_ in spec}
        else:
            names |= {cls for cls, *_ in spec.get("fixed", [])}
    from kits import ALL_CLASSES
    names = sorted(names | set(ALL_CLASSES))
    rows = query("SELECT item_name, id, width, length, stack_height, allow_sit, allow_walk FROM items_base WHERE item_name IN ("
                 + ",".join(f"'{n}'" for n in names) + ");")
    base = {r[0]: (int(r[1]), int(r[2]), int(r[3]), float(r[4]), r[5] == "1", r[6] == "1") for r in rows}
    missing = [n for n in names if n not in base]
    if missing:
        sys.exit(f"not in items_base: {missing}")

    out = ["-- Habnut's two worlds. Generated by gen-public-rooms.py; safe to run again.",
           "UPDATE users SET username='Habnut', motto='Welcome to Habnut!' WHERE id=1;",
           "UPDATE rooms SET owner_name='Habnut' WHERE owner_id=1;",
           "INSERT INTO navigator_publiccats (id, name, image, visible, order_num) VALUES (100, 'Habnut', '0', '1', -2)"
           " ON DUPLICATE KEY UPDATE name=VALUES(name), visible='1', order_num=-2;",
           "INSERT INTO navigator_publiccats (id, name, image, visible, order_num) VALUES (101, 'Nutropolis', '0', '1', -1)"
           " ON DUPLICATE KEY UPDATE name=VALUES(name), visible='1', order_num=-1;"]
    every = []
    for name, desc, model, users_max, spec in HOTEL:
        items = furnish(name, model, spec, base)
        every.append(items)
        room_sql(out, name, desc, model, users_max, 100, place(name, model, items, base))
    for name, desc, model, users_max, (kind, safe), job, spec in CITY:
        items = furnish(name, model, spec, base)
        every.append(items)
        room_sql(out, name, desc, model, users_max, 101, place(name, model, items, base))
        out.append(f"INSERT INTO habnut_rp_rooms (room_id, kind, safe) VALUES (@room, '{kind}', {safe})"
                   " ON DUPLICATE KEY UPDATE kind=VALUES(kind), safe=VALUES(safe);")
        for job in ([job] if isinstance(job, tuple) else (job or [])):
            code, title, jkind, wage, minutes, ranks, vehicle = job
            out.append(f"INSERT INTO habnut_rp_jobs (code, name, kind, room_id, wage, shift_minutes, ranks, vehicle_effect)"
                       f" VALUES ('{code}', {sql_text(title)}, '{jkind}', @room, {wage}, {minutes}, {sql_text(ranks)}, {vehicle})"
                       " ON DUPLICATE KEY UPDATE name=VALUES(name), kind=VALUES(kind), room_id=VALUES(room_id), wage=VALUES(wage),"
                       " shift_minutes=VALUES(shift_minutes), ranks=VALUES(ranks), vehicle_effect=VALUES(vehicle_effect);")
    for name, desc, model, price in PROPERTIES:
        q = sql_text(name)
        # A sold property belongs to its buyer: find it by name whoever owns it.
        out.append(f"INSERT INTO rooms (owner_id, owner_name, name, description, model, users_max, category, is_public, state, date_created)"
                   f" SELECT 1, 'Habnut', {q}, {sql_text(desc)}, '{model}', 25, 1, '1', 'open', UNIX_TIMESTAMP()"
                   f" FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM rooms WHERE name={q});")
        out.append(f"SET @room = (SELECT id FROM rooms WHERE name={q} ORDER BY id LIMIT 1);")
        out.append("INSERT INTO navigator_publics (public_cat_id, room_id, visible)"
                   " SELECT 101, @room, '1' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM navigator_publics WHERE room_id=@room);")
        out.append("INSERT INTO habnut_rp_rooms (room_id, kind, safe) VALUES (@room, 'home', 1) ON DUPLICATE KEY UPDATE kind='home';")
        out.append(f"INSERT INTO habnut_rp_properties (room_id, price) VALUES (@room, {price}) ON DUPLICATE KEY UPDATE price=VALUES(price);")
    print("\n".join(out))
    print(f"-- {sum(len(i) for i in every)} items in {len(every)} rooms, every placement checked", file=sys.stderr)


if __name__ == "__main__":
    main()
