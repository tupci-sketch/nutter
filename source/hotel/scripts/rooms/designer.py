"""Fills a room with furniture by theme, the way a person would: groups of
seats around tables on rugs, things along the walls, a centrepiece in the
middle, and walkways left clear.

Every placement is checked against the floor plan (on the floor, one
height, not on the door) and against reachability: the door must still reach
every free tile and every seat, so nobody is ever walled in.

    design(grid, door, kit, base) -> [(classname, x, y, rotation)]

grid: the room's heightmap rows; door: (x, y); kit: a theme (see kits.py);
base: classname -> (width, length, height, can_sit, can_walk).
"""
from collections import deque


def height_of(c):
    if c.lower() == "x":
        return None
    if c.isdigit():
        return int(c)
    return 10 + ord(c.lower()) - ord("a")


class Room:
    def __init__(self, grid, door, base):
        self.base = base
        self.floor = {}
        for y, row in enumerate(grid):
            for x, c in enumerate(row):
                h = height_of(c)
                if h is not None:
                    self.floor[(x, y)] = h
        self.door = door
        self.blocked = set()      # tiles a person cannot walk through
        self.taken = set()        # tiles with a standing object (rugs aside)
        self.rugs = set()         # tiles covered by a rug
        self.seats = set()
        self.reserved = set()     # walkways and the door's surroundings
        self.items = []
        # only what the door can reach in the empty room has to stay reachable
        self.open_reach = self.reach(set())
        dx, dy = door
        for x in range(dx - 2, dx + 3):
            for y in range(dy - 2, dy + 3):
                self.reserved.add((x, y))

    # -- geometry ---------------------------------------------------------

    def footprint(self, cls, x, y, rot):
        w, l = self.base[cls][0], self.base[cls][1]
        if rot in (2, 6):
            w, l = l, w
        return [(x + i, y + j) for i in range(w) for j in range(l)]

    def wall_side(self, x, y):
        """'left' if the tile backs onto the left wall, 'top' for the top wall.

        Walls stand only on the room's outer edge (nothing further out in that
        row or column), not at steps or holes inside it."""
        if (x - 1, y) not in self.floor and not any((i, y) in self.floor for i in range(x - 1)):
            return "left"
        if (x, y - 1) not in self.floor and not any((x, j) in self.floor for j in range(y - 1)):
            return "top"
        return None

    def centre(self):
        xs = [p[0] for p in self.floor]
        ys = [p[1] for p in self.floor]
        return (sum(xs) / len(xs), sum(ys) / len(ys))

    def is_rug(self, cls):
        return self.base[cls][4] and self.base[cls][2] <= 0.1

    # -- placing ------------------------------------------------------------

    def fits(self, cls, x, y, rot, allow_reserved=False, on_rug=True):
        tiles = self.footprint(cls, x, y, rot)
        heights = {self.floor.get(t) for t in tiles}
        if None in heights or len(heights) != 1:
            return False
        rug = self.is_rug(cls)
        for t in tiles:
            if t == self.door or t in self.taken:
                return False
            if t in self.rugs and (rug or not on_rug):
                return False
            if not allow_reserved and t in self.reserved:
                return False
        return True

    def reachable_ok(self, extra_blocked):
        """True if, with extra_blocked added, the door still reaches every free tile and seat it could reach."""
        blocked = self.blocked | extra_blocked
        seen = self.reach(blocked)
        return all(t in seen or t in blocked for t in self.open_reach)

    def reach(self, blocked):
        start = self.door
        seen = {start}
        queue = deque([start])
        while queue:
            x, y = queue.popleft()
            for n in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1), (x + 1, y + 1), (x - 1, y - 1), (x + 1, y - 1), (x - 1, y + 1)):
                if n in seen or n not in self.floor or n in blocked:
                    continue
                # diagonal moves need one of the two sides open
                if n[0] != x and n[1] != y and ((n[0], y) in blocked and (x, n[1]) in blocked):
                    continue
                if abs(self.floor[n] - self.floor[(x, y)]) > 1:
                    continue
                seen.add(n)
                queue.append(n)
        return seen

    def place(self, cls, x, y, rot, allow_reserved=False, check_reach=True):
        if cls not in self.base or not self.fits(cls, x, y, rot, allow_reserved):
            return False
        tiles = set(self.footprint(cls, x, y, rot))
        w, l, h, sit, walk = self.base[cls]
        blocking = set() if (walk or sit) else tiles
        if check_reach and blocking and not self.reachable_ok(blocking):
            return False
        if self.is_rug(cls):
            self.rugs |= tiles
        else:
            self.taken |= tiles
        self.blocked |= blocking
        if sit:
            self.seats |= tiles
        self.items.append((cls, x, y, rot))
        return True

    def group(self, parts, x, y):
        """Places a group of (cls, dx, dy, rot) all or nothing, with a free ring around it."""
        planned = []
        tiles = set()
        for cls, dx, dy, rot in parts:
            if cls not in self.base or not self.fits(cls, x + dx, y + dy, rot):
                return False
            fp = set(self.footprint(cls, x + dx, y + dy, rot))
            if fp & tiles:
                # only rugs may share tiles, and only with what stands on them
                if self.is_rug(cls):
                    return False
                standing = {t for (c, px, py, r) in planned if not self.is_rug(c) for t in self.footprint(c, px, py, r)}
                if fp & standing:
                    return False
            tiles |= fp
            planned.append((cls, x + dx, y + dy, rot))
        blocking = {t for (c, px, py, r) in planned if not (self.base[c][3] or self.base[c][4]) for t in self.footprint(c, px, py, r)}
        if not self.reachable_ok(blocking):
            return False
        # rugs first so things stand on them
        for cls, px, py, rot in sorted(planned, key=lambda p: not self.is_rug(p[0])):
            fp = set(self.footprint(cls, px, py, rot))
            w, l, h, sit, walk = self.base[cls]
            if self.is_rug(cls):
                self.rugs |= fp
            else:
                self.taken |= fp
            if not (sit or walk):
                self.blocked |= fp
            if sit:
                self.seats |= fp
            self.items.append((cls, px, py, rot))
        # keep a walkway around the group
        xs = [t[0] for t in tiles]
        ys = [t[1] for t in tiles]
        for gx in range(min(xs) - 1, max(xs) + 2):
            for gy in range(min(ys) - 1, max(ys) + 2):
                if (gx, gy) not in tiles:
                    self.reserved.add((gx, gy))
        return True


def design(grid, door, kit, base, fixed=()):
    room = Room(grid, door, base)
    # 1. what the designer was told to put exactly here
    for cls, x, y, rot in fixed:
        room.place(cls, x, y, rot, allow_reserved=True)

    cx, cy = room.centre()
    by_centre = sorted(room.floor, key=lambda t: (t[0] - cx) ** 2 + (t[1] - cy) ** 2)

    # 2. a centrepiece in the middle of the room, with space around it
    for parts in kit.get("centrepieces", []):
        for (x, y) in by_centre[:40]:
            if room.group(parts, x, y):
                break

    # 3. groups of seats, from the middle outwards, as many as fit
    groups = kit.get("groups", [])
    if groups:
        n = 0
        for (x, y) in by_centre:
            if (x, y) in room.reserved or (x, y) in room.taken:
                continue
            if room.group(groups[n % len(groups)], x, y):
                n += 1
                if n >= kit.get("max_groups", 40):
                    break

    # 4. along the walls: a rhythm of decorations, facing into the room
    along = kit.get("walls", [])
    if along:
        edge = sorted((t for t in room.floor if room.wall_side(*t)), key=lambda t: (room.wall_side(*t), t[0] + t[1]))
        step = kit.get("wall_step", 2)
        n = 0
        last = None
        for (x, y) in edge:
            side = room.wall_side(x, y)
            if last and side == last[2] and abs(x - last[0]) + abs(y - last[1]) < step:
                continue
            cls, rot_left, rot_top = along[n % len(along)]
            if room.place(cls, x, y, rot_left if side == "left" else rot_top):
                n += 1
                last = (x, y, side)

    # 5. scattered extras in what open floor is left, sparingly
    extras = kit.get("scatter", [])
    if extras:
        n = 0
        for (x, y) in by_centre[::7]:
            if (x, y) in room.reserved:
                continue
            if room.place(extras[n % len(extras)], x, y, 0):
                n += 1
                if n >= kit.get("max_scatter", 12):
                    break
    return room.items
