"""Themes for the room designer.

A group is a list of (classname, dx, dy, rotation) placed together around an
anchor, rugs first. Rotation: 0 faces up the room (north), 2 east, 4 south,
6 west, so a chair north of a table faces 4 and one south of it faces 0.
Walls: (classname, rotation by the left wall, rotation by the top wall).
"""


def table_for_four(table, chair, rug=None):
    parts = [(rug, 0, -1, 0)] if rug else []
    return parts + [(table, 0, 0, 0), (chair, 0, -1, 4), (chair, 1, -1, 4), (chair, 0, 2, 0), (chair, 1, 2, 0)]


def table_for_eight(table, chair):
    return [(table, 0, 0, 0), (chair, 0, -1, 4), (chair, 1, -1, 4), (chair, 0, 2, 0), (chair, 1, 2, 0),
            (chair, -1, 0, 2), (chair, -1, 1, 2), (chair, 2, 0, 6), (chair, 2, 1, 6)]


def sofas_facing(table, sofa, rug=None):
    """Two 2-seat sofas across a 2x2 table."""
    parts = [(rug, 0, -1, 0)] if rug else []
    return parts + [(table, 0, 0, 0), (sofa, 0, -1, 4), (sofa, 0, 2, 0)]


KITS = {
    "lounge": {
        "centrepieces": [[("bling11_statue2", 1, 1, 0), ("exe_plant", 0, 0, 0), ("exe_plant", 3, 0, 0),
                          ("exe_plant", 0, 3, 0), ("exe_plant", 3, 3, 0)]],
        "groups": [sofas_facing("glass_table", "glass_sofa", "exe_rug"),
                   table_for_four("table_norja_med", "chair_norja"),
                   sofas_facing("table_polyfon_med", "sofa_polyfon")],
        "walls": [("exe_plant", 0, 0), ("exe_artlamp", 0, 0), ("shelves_norja", 2, 4), ("bling11_plant", 0, 0),
                  ("exe_globe", 0, 0), ("lamp_basic", 0, 0)],
        "wall_step": 2, "max_groups": 16,
    },
    "pub": {
        "groups": [table_for_four("lodge_dark_diningtable", "lodge_dark_barrelstool"),
                   [("lodge_dark_diningtable", 0, 0, 0), ("lodge_dark_bench", 0, -1, 4), ("lodge_dark_bench", 0, 2, 0)]],
        "walls": [("lodge_dark_bookcase", 2, 4), ("lodge_dark_candle", 0, 0), ("lodge_dark_cornerplinth", 0, 0),
                  ("plant_bonsai", 0, 0)],
        "wall_step": 2, "max_groups": 10,
    },
    "garden": {
        "centrepieces": [[("country_well", 1, 1, 0), ("plant_sunflower", 0, 0, 0), ("plant_sunflower", 2, 0, 0),
                          ("plant_sunflower", 0, 2, 0), ("plant_sunflower", 2, 2, 0)]],
        "groups": [[("summer_c22_picnictable", 0, 0, 0), ("country_log", 0, -1, 4), ("country_log", 0, 1, 0)],
                   [("country_grass", 0, 0, 0), ("plant_rose", 0, 0, 0), ("plant_sunflower", 1, 0, 0),
                    ("garden_lupin1", 0, 1, 0), ("garden_lupin3", 1, 1, 0)],
                   [("jp_c22_water", 0, 0, 0), ("jp_c22_rock", 2, 0, 0), ("plant_bulrush", 0, 2, 0), ("plant_bulrush", 1, 2, 0)],
                   [("country_grass", 0, 0, 0), ("garden_lupin2", 0, 0, 0), ("garden_lupin4", 1, 1, 0), ("plant_fruittree", 1, 0, 0)],
                   [("summer_c22_picnictable", 0, 0, 0), ("country_log", 0, -1, 4), ("country_log", 0, 1, 0),
                    ("plant_fruittree", 2, 0, 0)],
                   [("country_soil", 0, 0, 0), ("plant_pineapple", 0, 0, 0), ("plant_pineapple", 1, 1, 0), ("country_scarecrow", 1, 0, 0)],
                   [("country_wheat", 0, 0, 0), ("country_wheat", 2, 0, 0)],
                   [("lt_c15_tree", 0, 0, 0), ("lt_c15_bush", 1, 0, 0), ("lt_c15_bush", 0, 1, 0)]],
        "walls": [("country_fnc1", 2, 0), ("plant_sunflower", 0, 0), ("country_fnc1", 2, 0), ("plant_bonsai", 0, 0)],
        "wall_step": 2, "max_groups": 70,
        "scatter": ["plant_fruittree", "garden_lupin5", "plant_rose", "lt_c15_bush", "plant_sunflower"], "max_scatter": 40,
    },
    "plaza": {
        "centrepieces": [[("rare_fountain", 1, 1, 0), ("plant_yukka", 0, 0, 0), ("plant_yukka", 2, 0, 0),
                          ("plant_yukka", 0, 2, 0), ("plant_yukka", 2, 2, 0)]],
        "groups": [[("bench_armas", 0, 0, 4), ("plant_big_cactus", 2, 0, 0)],
                   [("summer_c22_picnictable", 0, 0, 0), ("bench_puffet", 0, -1, 4), ("bench_puffet", 0, 1, 0)],
                   [("summer_c17_merchstall", 0, 0, 0), ("summer_icebox", 1, 0, 0)]],
        "walls": [("summer_c17_promenadelamp", 0, 0), ("plant_yukka", 0, 0), ("bench_armas", 2, 4)],
        "wall_step": 3, "max_groups": 18,
        "scatter": ["plant_yukka", "summer_c17_promenadelamp"], "max_scatter": 8,
    },
    "office": {
        "groups": [table_for_four("exe_s_table", "exe_chair"),
                   [("exe_rug", 0, 0, 0), ("exe_sofa", 0, 0, 4), ("exe_s_table", 0, 1, 0)]],
        "walls": [("exe_plant", 0, 0), ("exe_drinks_cabinet", 2, 4), ("exe_artlamp", 0, 0), ("exe_globe", 0, 0),
                  ("exe_copier", 2, 4)],
        "wall_step": 2, "max_groups": 10,
    },
    "cafe": {
        "groups": [[("gothiccafe_c20_rug", 0, 0, 0), ("gothiccafe_c20_coffeetable", 0, 0, 0),
                    ("gothiccafe_c20_sofa", 0, -1, 4), ("gothiccafe_c20_armchair", 0, 1, 0), ("gothiccafe_c20_armchair", 1, 1, 0)],
                   table_for_four("table_armas", "small_chair_armas")],
        "walls": [("gothiccafe_c20_bookcase", 0, 0), ("gothiccafe_c20_rosepainting", 0, 0), ("plant_rose", 0, 0),
                  ("plant_rose_black", 0, 0)],
        "wall_step": 2, "max_groups": 14,
    },
    "terrace": {
        "centrepieces": [[("mafia_c26_jacuzzi_white", 1, 1, 0), ("mafia_c26_planter_white", 0, 0, 0), ("mafia_c26_planter_white", 3, 0, 0),
                          ("mafia_c26_planter_white", 0, 3, 0), ("mafia_c26_planter_white", 3, 3, 0)]],
        "groups": [[("habbo25_c25_sunlounger1", 0, 0, 0), ("lido_parasol", 1, 0, 0), ("habbo25_c25_sunlounger1", 2, 0, 0)],
                   table_for_four("glass_table", "glass_chair"),
                   [("habbo25_c25_sunlounger3", 0, 0, 0), ("val_c21_parasol", 1, 0, 0), ("habbo25_c25_sunlounger3", 2, 0, 0)],
                   [("val15_hottub", 0, 0, 0), ("mafia_c26_planter_white", 2, 0, 0)]],
        "walls": [("mafia_c26_planter_white", 0, 0), ("purablk_c16_lamp1", 0, 0), ("room_cof15_planter", 2, 0), ("plant_yukka", 0, 0)],
        "wall_step": 2, "max_groups": 14,
    },
    "zen": {
        "centrepieces": [[("jp_c22_water", 0, 0, 0), ("jp_c22_rock", 2, 0, 0), ("jp_c22_mapleleaves", 0, 2, 0),
                          ("jp_c22_mapleleaves", 1, 2, 0)]],
        "groups": [[("jp_c22_tatami", 0, 0, 0), ("jp_c22_tatami", 1, 0, 0), ("jp_c15_teapot", 0, 2, 0),
                    ("jp_c22_seat", 0, 0, 4), ("jp_c22_seat", 1, 0, 4)],
                   [("jp_bamboo", 0, 0, 0), ("jp15_luckycat", 2, 0, 0)],
                   [("jp_c22_water", 0, 0, 0), ("jp_c22_mapleleaves", 2, 1, 0)]],
        "walls": [("jp_c22_divider", 2, 4), ("jp_c15_daruma", 0, 0), ("jp_c22_tanuki", 0, 0), ("plant_bonsai", 0, 0)],
        "wall_step": 2, "max_groups": 24,
        "scatter": ["plant_bonsai", "jp_c22_mapleleaves", "jp15_luckycat"], "max_scatter": 10,
    },
    "hospital": {
        "groups": [[("hosptl_bed", 0, 0, 0), ("hosptl_cab1", 1, 0, 0)]],
        "walls": [("hosptl_cab2", 2, 4), ("hosptl_light", 0, 0), ("hosptl_defibs", 0, 0), ("plant_yukka", 0, 0)],
        "wall_step": 2, "max_groups": 8,
    },
    "grunge": {
        "groups": [[("grunge_table", 0, 0, 0), ("grunge_chair", 0, -1, 4), ("grunge_chair", 1, 2, 0)],
                   [("grunge_mattress", 0, 0, 0)]],
        "walls": [("grunge_barrel", 0, 0), ("grunge_radiator", 2, 4), ("grunge_candle", 0, 0), ("grunge_sign", 0, 0)],
        "wall_step": 2, "max_groups": 4,
    },
}

ALL_CLASSES = sorted({c for kit in KITS.values() for key in ("centrepieces", "groups") for g in kit.get(key, []) for c, *_ in g if c}
                     | {c for kit in KITS.values() for c, *_ in kit.get("walls", [])}
                     | {c for kit in KITS.values() for c in kit.get("scatter", [])})
