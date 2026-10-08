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
import subprocess
import sys

PW = sys.argv[1]


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
    ("Habnut Lobby", "Welcome to Habnut! Sit down, say hello, make friends.", "newbie_lobby", 75, [
        # sofas on a rug by the windows
        ("plant_yukka", 5, 2, 0), ("plant_yukka", 12, 2, 0),
        ("sofa_polyfon", 7, 3, 4), ("carpet_standard", 6, 4, 0), ("table_polyfon_med", 7, 5, 0),
        ("sofachair_polyfon", 6, 5, 2), ("sofachair_polyfon", 6, 6, 2),
        ("sofachair_polyfon", 9, 5, 6), ("sofachair_polyfon", 9, 6, 6), ("sofa_polyfon", 7, 7, 0),
        # a table for six in the corner
        ("table_norja_med", 18, 2, 0), ("chair_norja", 18, 1, 4), ("chair_norja", 19, 1, 4),
        ("chair_norja", 18, 4, 0), ("chair_norja", 19, 4, 0), ("chair_norja", 17, 2, 2), ("chair_norja", 20, 3, 6),
        ("lamp_basic", 21, 0, 0), ("plant_big_cactus", 16, 0, 0), ("plant_big_cactus", 21, 8, 0),
        # a nook by the door
        ("couch_norja", 0, 5, 2), ("table_polyfon_small", 1, 5, 0), ("couch_norja", 3, 5, 6), ("lamp_basic", 0, 8, 0),
        # benches down the hall
        ("bench_armas", 8, 15, 2), ("bench_armas", 8, 17, 2), ("bench_armas", 8, 19, 2),
        ("bench_armas", 16, 15, 6), ("bench_armas", 16, 17, 6), ("bench_armas", 16, 19, 6),
        ("lamp_armas", 6, 21, 0), ("lamp_armas", 16, 21, 0),
        # and a table at the far end
        ("table_armas", 10, 23, 0), ("small_chair_armas", 10, 22, 4), ("small_chair_armas", 11, 22, 4),
        ("small_chair_armas", 9, 23, 2), ("small_chair_armas", 9, 24, 2),
        ("small_chair_armas", 12, 23, 6), ("small_chair_armas", 12, 24, 6),
        ("small_chair_armas", 10, 25, 0), ("small_chair_armas", 11, 25, 0),
        ("plant_pineapple", 5, 26, 0), ("plant_pineapple", 16, 26, 0),
    ]),
    ("The Habnut Pub", "Pull up a stool. The drinks are on the house.", "pub_a", 50, [
        # the bar
        ("bardesk_polyfon", 10, 2, 4), ("bardesk_polyfon", 12, 2, 4), ("bardesk_polyfon", 14, 2, 4),
        ("bar_chair_armas", 10, 3, 0), ("bar_chair_armas", 11, 3, 0), ("bar_chair_armas", 12, 3, 0),
        ("bar_chair_armas", 13, 3, 0), ("bar_chair_armas", 14, 3, 0), ("bar_chair_armas", 15, 3, 0),
        # tables on the floor
        ("table_armas", 11, 7, 0), ("small_chair_armas", 10, 7, 2), ("small_chair_armas", 10, 8, 2),
        ("small_chair_armas", 13, 7, 6), ("small_chair_armas", 13, 8, 6),
        ("table_armas", 15, 7, 0), ("small_chair_armas", 14, 7, 2), ("small_chair_armas", 14, 8, 2),
        ("small_chair_armas", 17, 7, 6), ("small_chair_armas", 17, 8, 6),
        ("table_armas", 13, 13, 0), ("small_chair_armas", 12, 13, 2), ("small_chair_armas", 12, 14, 2),
        ("small_chair_armas", 15, 13, 6), ("small_chair_armas", 15, 14, 6),
        # the lounge up the steps
        ("couch_norja", 2, 11, 4), ("couch_norja", 5, 11, 4),
        ("table_norja_med", 2, 12, 0), ("table_norja_med", 5, 12, 0),
        ("couch_norja", 2, 14, 0), ("couch_norja", 5, 14, 0),
        ("lamp_armas", 1, 11, 0), ("lamp_armas", 8, 11, 0), ("plant_rose", 1, 19, 0),
        # by the door
        ("plant_rose", 21, 7, 0), ("plant_rose", 21, 20, 0),
        ("bench_armas", 18, 22, 4), ("bench_armas", 20, 22, 4),
    ]),
]

# Nutropolis: name, description, model, max users, (kind, safe from fights), job or None, furniture
CITY = [
    ("Nutropolis Central", "The heart of the city. Type :rphelp to get started.", "model_n", 75, ("spawn", 1), None, [
        ("rare_fountain", 10, 10, 0), ("tree1", 8, 8, 0), ("tree2", 13, 8, 0), ("tree3", 8, 13, 0), ("tree1", 13, 13, 0),
        ("bench_armas", 3, 2, 4), ("bench_armas", 9, 2, 4), ("bench_armas", 15, 2, 4),
        ("bench_armas", 3, 19, 0), ("bench_armas", 9, 19, 0), ("bench_armas", 15, 19, 0),
        ("bench_armas", 2, 9, 2), ("bench_armas", 19, 9, 6),
        ("lamp_armas", 1, 1, 0), ("lamp_armas", 20, 1, 0), ("lamp_armas", 1, 20, 0), ("lamp_armas", 20, 20, 0),
        ("tree2", 5, 5, 0), ("tree3", 16, 5, 0), ("tree1", 5, 17, 0), ("tree2", 16, 17, 0),
    ]),
    ("Back Alley", "Nobody comes here for a good reason. Fights allowed.", "park_b", 30, ("street", 0), None, [
        ("tree1", 0, 0, 0), ("plant_big_cactus", 11, 5, 0), ("bench_armas", 8, 0, 4), ("lamp_armas", 0, 5, 0),
    ]),
    ("Nut Bank", "Deposits and withdrawals: :deposit and :withdraw. Tellers wanted.", "model_3", 40, ("bank", 1),
     ("teller", "Nut Bank", "civil", 30, 10, "Teller;Senior Teller;Bank Manager", 0), [
        ("bardesk_polyfon", 6, 4, 0), ("bardesk_polyfon", 8, 4, 0), ("bardesk_polyfon", 10, 4, 0), ("bardesk_polyfon", 12, 4, 0),
        ("bardeskcorner_polyfon", 14, 4, 0), ("exe_chair", 7, 3, 4), ("exe_chair", 11, 3, 4),
        ("safe_silo", 3, 1, 0), ("safe_silo", 4, 1, 0), ("safe_silo", 5, 1, 0),
        ("exe_rug", 7, 8, 0), ("exe_sofa", 5, 13, 0), ("plant_yukka", 15, 1, 0), ("plant_yukka", 15, 13, 0),
    ]),
    ("Nutropolis Police", "Serve and protect. Officers clock in here.", "model_4", 40, ("police", 1),
     ("police", "Nutropolis Police", "police", 40, 10, "Cadet;Officer;Sergeant;Inspector;Chief of Police", 19), [
        ("hc_exe_wrkdesk", 6, 14, 2), ("exe_chair", 7, 14, 6), ("bench_armas", 2, 17, 0), ("bench_armas", 10, 17, 0),
        ("exe_table", 3, 6, 0), ("exe_chair", 2, 6, 2), ("exe_chair", 2, 7, 2), ("exe_chair", 6, 6, 6), ("exe_chair", 6, 7, 6),
        ("exe_plant", 1, 5, 0), ("exe_plant", 8, 5, 0), ("safe_silo", 17, 1, 0), ("safe_silo", 18, 1, 0),
        ("exe_globe", 21, 1, 0), ("exe_sofa", 16, 10, 0),
    ]),
    ("Nutropolis Jail", "Do the time.", "model_h", 25, ("jail", 1), None, [
        ("bed_budget_one", 3, 10, 0), ("bed_budget_one", 10, 10, 0), ("bench_armas", 6, 12, 0),
        ("prison_tower", 10, 2, 0), ("prison_crnr", 5, 2, 0),
    ]),
    ("Nutropolis General", "Hurt? Rest here and you will mend. Medics clock in here.", "model_x", 40, ("hospital", 1),
     ("medic", "Nutropolis General", "medic", 40, 10, "Trainee;Paramedic;Nurse;Doctor;Chief of Medicine", 20), [
        ("hosptl_bed", 2, 8, 0), ("hosptl_bed", 4, 8, 0), ("hosptl_bed", 2, 14, 0), ("hosptl_bed", 4, 14, 0),
        ("hosptl_bed", 14, 8, 0), ("hosptl_bed", 16, 8, 0), ("hosptl_bed", 14, 14, 0), ("hosptl_bed", 16, 14, 0),
        ("hosptl_cab1", 6, 8, 0), ("hosptl_cab1", 13, 11, 0), ("hosptl_defibs", 18, 8, 0), ("hosptl_light", 1, 8, 0),
        ("hosptl_seat", 2, 2, 4), ("hosptl_seat", 5, 2, 4), ("exe_wrkdesk", 12, 3, 0), ("exe_chair", 12, 2, 4),
        ("exe_plant", 18, 1, 0), ("exe_plant", 1, 1, 0), ("hosptl_curtain", 2, 20, 0),
    ]),
    ("The Nut Cafe", "Coffee, gossip, and the odd brawl. Baristas wanted.", "model_3", 40, ("work", 0),
     ("barista", "The Nut Cafe", "civil", 25, 10, "Barista;Shift Lead;Cafe Manager", 0), [
        ("diner_bardesk*1", 5, 3, 0), ("diner_bardesk*1", 6, 3, 0), ("diner_bardesk*1", 7, 3, 0), ("diner_bardesk*1", 8, 3, 0),
        ("diner_bardesk*1", 9, 3, 0), ("diner_bardesk*1", 10, 3, 0), ("diner_bardesk*1", 11, 3, 0),
        ("diner_table_1*1", 4, 7, 0), ("diner_chair*1", 3, 7, 2), ("diner_chair*1", 3, 8, 2),
        ("diner_chair*1", 6, 7, 6), ("diner_chair*1", 6, 8, 6),
        ("diner_table_1*1", 10, 7, 0), ("diner_chair*1", 9, 7, 2), ("diner_chair*1", 9, 8, 2),
        ("diner_chair*1", 12, 7, 6), ("diner_chair*1", 12, 8, 6),
        ("diner_table_1*1", 10, 11, 0), ("diner_chair*1", 9, 11, 2), ("diner_chair*1", 9, 12, 2),
        ("diner_chair*1", 12, 11, 6), ("diner_chair*1", 12, 12, 6),
        ("plant_pineapple", 15, 1, 0), ("plant_pineapple", 15, 13, 0),
    ]),
    ("Town Hall", "The seat of the city council.", "model_3", 40, ("townhall", 1),
     ("clerk", "Town Hall", "gov", 35, 10, "Clerk;Councillor;Deputy Mayor;Mayor", 0), [
        ("exe_table", 7, 2, 0), ("throne", 8, 1, 4), ("exe_globe", 3, 1, 0), ("exe_plant", 15, 1, 0), ("exe_plant", 15, 13, 0),
        ("exe_rug", 7, 5, 0), ("bench_armas", 5, 8, 0), ("bench_armas", 9, 8, 0), ("bench_armas", 5, 12, 0), ("bench_armas", 9, 12, 0),
    ]),
]


def place(name, model, items, base):
    """Checks every item against the floor and each other; returns (base id, x, y, z, rot, height)."""
    grid, door = heightmap(model)
    used, placed = {}, []
    for cls, x, y, rot in items:
        bid, w, l, h = base[cls]
        if rot in (2, 6):
            w, l = l, w
        heights = set()
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
                if prev is not None and prev != "rug" and h != 0:
                    sys.exit(f"{name}: {cls} at {x},{y} overlaps {prev} at {tx},{ty}")
                used[(tx, ty)] = "rug" if h == 0 else cls
                heights.add(z)
        if len(heights) != 1:
            sys.exit(f"{name}: {cls} at {x},{y} straddles heights {sorted(heights)}")
        placed.append((bid, x, y, heights.pop(), rot, h))
    return placed


def sql_text(s):
    return "'" + s.replace("\\", "\\\\").replace("'", "''") + "'"


def room_sql(out, name, desc, model, users_max, category, placed):
    q = sql_text(name)
    out.append(f"INSERT INTO rooms (owner_id, owner_name, name, description, model, users_max, category, is_public, state, date_created)"
               f" SELECT 1, 'Habnut', {q}, {sql_text(desc)}, '{model}', {users_max}, 1, '1', 'open', UNIX_TIMESTAMP()"
               f" FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM rooms WHERE name={q} AND owner_id=1);")
    out.append(f"SET @room = (SELECT id FROM rooms WHERE name={q} AND owner_id=1 ORDER BY id LIMIT 1);")
    out.append("SET @fresh = (SELECT COUNT(*) = 0 FROM items WHERE room_id = @room);")
    # rugs first, so what stands on them is drawn on top
    for bid, x, y, z, rot, h in sorted(placed, key=lambda p: p[5] != 0):
        out.append(f"INSERT INTO items (user_id, room_id, item_id, wall_pos, x, y, z, rot, extra_data, wired_data)"
                   f" SELECT 1, @room, {bid}, '', {x}, {y}, {z}, {rot}, '0', '' FROM DUAL WHERE @fresh;")
    out.append(f"INSERT INTO navigator_publics (public_cat_id, room_id, visible)"
               f" SELECT {category}, @room, '1' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM navigator_publics WHERE room_id=@room);")


def main():
    every = [r[-1] for r in HOTEL] + [r[-1] for r in CITY]
    names = sorted({cls for items in every for cls, *_ in items})
    rows = query("SELECT item_name, id, width, length, stack_height FROM items_base WHERE item_name IN ("
                 + ",".join(f"'{n}'" for n in names) + ");")
    base = {r[0]: (int(r[1]), int(r[2]), int(r[3]), float(r[4])) for r in rows}
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
    for name, desc, model, users_max, items in HOTEL:
        room_sql(out, name, desc, model, users_max, 100, place(name, model, items, base))
    for name, desc, model, users_max, (kind, safe), job, items in CITY:
        room_sql(out, name, desc, model, users_max, 101, place(name, model, items, base))
        out.append(f"INSERT INTO habnut_rp_rooms (room_id, kind, safe) VALUES (@room, '{kind}', {safe})"
                   " ON DUPLICATE KEY UPDATE kind=VALUES(kind), safe=VALUES(safe);")
        if job:
            code, title, jkind, wage, minutes, ranks, vehicle = job
            out.append(f"INSERT INTO habnut_rp_jobs (code, name, kind, room_id, wage, shift_minutes, ranks, vehicle_effect)"
                       f" VALUES ('{code}', {sql_text(title)}, '{jkind}', @room, {wage}, {minutes}, {sql_text(ranks)}, {vehicle})"
                       " ON DUPLICATE KEY UPDATE name=VALUES(name), kind=VALUES(kind), room_id=VALUES(room_id), wage=VALUES(wage),"
                       " shift_minutes=VALUES(shift_minutes), ranks=VALUES(ranks), vehicle_effect=VALUES(vehicle_effect);")
    print("\n".join(out))
    print(f"-- {sum(len(i) for i in every)} items in {len(every)} rooms, every placement checked", file=sys.stderr)


if __name__ == "__main__":
    main()
