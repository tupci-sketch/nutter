-- Habnut — base content
--
-- What every hotel needs before anybody can do anything. Applied by
-- `habnutctl install` and by `habnutctl dev up`, after the schema migrations
-- and before anyone signs in.
--
-- Without this a fresh hotel has no room shapes, so no room can be created or
-- loaded; no furniture bases, so nothing can be put down; and an empty
-- catalogue, so there is nothing to buy. The install step used to call a
-- seeder that did not exist, which is why a fresh hotel came up empty.
--
-- Every statement is safe to run twice: a hotel that is already seeded is left
-- exactly as it is, including whatever its staff have changed since. That is
-- why each insert is guarded by NOT EXISTS rather than ON DUPLICATE KEY — not
-- every table has a unique key on the column that identifies the row.
--
-- No string literal in this file contains a backslash. MariaDB unescapes
-- backslash sequences in a literal and H2 does not, so an escaped newline
-- would seed one room shape into the hotel and a different one into the
-- tests. Line breaks are written as real line breaks instead.


-- ─── Room shapes ────────────────────────────────────────────────────────────
--
-- A heightmap is one character per tile: a digit is the floor height there,
-- 'x' is a hole or a wall, and a letter carries the heights past 9. The door
-- is where a player appears and is always on a walkable tile.

INSERT INTO habnut_room_models (id, heightmap, door_x, door_y, door_rotation, max_visitors)
SELECT 'model_a', 'xxxxxxxxxx
x000000000
x000000000
x000000000
0000000000
x000000000
x000000000
x000000000
x000000000
xxxxxxxxxx', 0, 4, 2, 25
WHERE NOT EXISTS (SELECT 1 FROM habnut_room_models WHERE id = 'model_a');

INSERT INTO habnut_room_models (id, heightmap, door_x, door_y, door_rotation, max_visitors)
SELECT 'model_b', 'xxxxxxxxxxxx
x00000000000
x00000000000
x00000000000
x00000000000
000000000000
x00000000000
x00000000000
x00000000000
x00000000000
x00000000000
xxxxxxxxxxxx', 0, 5, 2, 30
WHERE NOT EXISTS (SELECT 1 FROM habnut_room_models WHERE id = 'model_b');

INSERT INTO habnut_room_models (id, heightmap, door_x, door_y, door_rotation, max_visitors)
SELECT 'model_c', 'xxxxxxxxxxxxxx
x1111100000000
x1111100000000
x1111100000000
x1111100000000
00000000000000
x0000000000000
x0000000000000
x0000000000000
x0000000000000
x0000000000000
x0000000000000
xxxxxxxxxxxxxx', 0, 5, 2, 35
WHERE NOT EXISTS (SELECT 1 FROM habnut_room_models WHERE id = 'model_c');

INSERT INTO habnut_room_models (id, heightmap, door_x, door_y, door_rotation, max_visitors)
SELECT 'model_lobby', 'xxxxxxxxxxxxxxxxxxxx
x2222222222222222222
x2222222222222222222
x1111111111111111111
x1111111111111111111
00000000000000000000
x0000000000000000000
x0000000000000000000
x0000000000000000000
x0000000000000000000
x0000000000000000000
x0000000000000000000
xxxxxxxxxxxxxxxxxxxx', 0, 5, 2, 50
WHERE NOT EXISTS (SELECT 1 FROM habnut_room_models WHERE id = 'model_lobby');

INSERT INTO habnut_room_models (id, heightmap, door_x, door_y, door_rotation, max_visitors)
SELECT 'model_hall', 'xxxxxxxxxxxxxxxx
x000000000000000
x000000000000000
x000000000000000
x000000000000000
x000000000000000
0000000000000000
x000000000000000
x000000000000000
x000000000000000
x000000000000000
x000000000000000
xxxxxxxxxxxxxxxx', 0, 6, 2, 40
WHERE NOT EXISTS (SELECT 1 FROM habnut_room_models WHERE id = 'model_hall');

INSERT INTO habnut_room_models (id, heightmap, door_x, door_y, door_rotation, max_visitors)
SELECT 'model_pool', 'xxxxxxxxxxxx
x00000000000
x00000000000
x00xxxxxx000
x00xxxxxx000
000xxxxxx000
x00xxxxxx000
x00000000000
x00000000000
xxxxxxxxxxxx', 0, 5, 2, 30
WHERE NOT EXISTS (SELECT 1 FROM habnut_room_models WHERE id = 'model_pool');

INSERT INTO habnut_room_models (id, heightmap, door_x, door_y, door_rotation, max_visitors)
SELECT 'model_small', 'xxxxxxxx
x0000000
x0000000
x0000000
00000000
x0000000
x0000000
xxxxxxxx', 0, 4, 2, 15
WHERE NOT EXISTS (SELECT 1 FROM habnut_room_models WHERE id = 'model_small');

-- ─── Furniture ──────────────────────────────────────────────────────────────
--
-- A starter set covering every behaviour the room engine knows about, so each
-- can be seen working rather than only read about: something to sit on,
-- something to stand on, something that opens, something with states, and
-- something that carries you.
--
-- sprite_id is the name the asset pack uses, so installing a real pack lights
-- these up without touching the database.

INSERT INTO habnut_items_base
  (sprite_id, name, description, type, width, length, stack_height,
   can_stack, can_sit, is_walkable, is_tradeable, interaction_type, interaction_modes)
SELECT 'chair_basic', 'Wooden Chair', 'Somewhere to sit.', 'floor', 1, 1, 1.000,
       0, 1, 0, 1, 'default', 1
WHERE NOT EXISTS (SELECT 1 FROM habnut_items_base WHERE sprite_id = 'chair_basic');

INSERT INTO habnut_items_base
  (sprite_id, name, description, type, width, length, stack_height,
   can_stack, can_sit, is_walkable, is_tradeable, interaction_type, interaction_modes)
SELECT 'chair_plasto', 'Plastic Chair', 'Stacks neatly.', 'floor', 1, 1, 1.000,
       1, 1, 0, 1, 'default', 1
WHERE NOT EXISTS (SELECT 1 FROM habnut_items_base WHERE sprite_id = 'chair_plasto');

INSERT INTO habnut_items_base
  (sprite_id, name, description, type, width, length, stack_height,
   can_stack, can_sit, is_walkable, is_tradeable, interaction_type, interaction_modes)
SELECT 'sofa_red', 'Red Sofa', 'Seats two.', 'floor', 2, 1, 1.000,
       0, 1, 0, 1, 'default', 1
WHERE NOT EXISTS (SELECT 1 FROM habnut_items_base WHERE sprite_id = 'sofa_red');

INSERT INTO habnut_items_base
  (sprite_id, name, description, type, width, length, stack_height,
   can_stack, can_sit, is_walkable, is_tradeable, interaction_type, interaction_modes)
SELECT 'table_wood', 'Wooden Table', 'Put something on it.', 'floor', 2, 2, 1.000,
       1, 0, 0, 1, 'default', 1
WHERE NOT EXISTS (SELECT 1 FROM habnut_items_base WHERE sprite_id = 'table_wood');

INSERT INTO habnut_items_base
  (sprite_id, name, description, type, width, length, stack_height,
   can_stack, can_sit, is_walkable, is_tradeable, interaction_type, interaction_modes)
SELECT 'table_small', 'Side Table', 'Just big enough.', 'floor', 1, 1, 1.000,
       1, 0, 0, 1, 'default', 1
WHERE NOT EXISTS (SELECT 1 FROM habnut_items_base WHERE sprite_id = 'table_small');

INSERT INTO habnut_items_base
  (sprite_id, name, description, type, width, length, stack_height,
   can_stack, can_sit, is_walkable, is_tradeable, interaction_type, interaction_modes)
SELECT 'rug_blue', 'Blue Rug', 'Walk straight over it.', 'floor', 3, 3, 0.000,
       0, 0, 1, 1, 'default', 1
WHERE NOT EXISTS (SELECT 1 FROM habnut_items_base WHERE sprite_id = 'rug_blue');

INSERT INTO habnut_items_base
  (sprite_id, name, description, type, width, length, stack_height,
   can_stack, can_sit, is_walkable, is_tradeable, interaction_type, interaction_modes)
SELECT 'lamp_standing', 'Standing Lamp', 'Three brightnesses.', 'floor', 1, 1, 1.000,
       0, 0, 0, 1, 'default', 3
WHERE NOT EXISTS (SELECT 1 FROM habnut_items_base WHERE sprite_id = 'lamp_standing');

INSERT INTO habnut_items_base
  (sprite_id, name, description, type, width, length, stack_height,
   can_stack, can_sit, is_walkable, is_tradeable, interaction_type, interaction_modes)
SELECT 'gate_wood', 'Wooden Gate', 'Opens and closes.', 'floor', 1, 1, 1.000,
       0, 0, 0, 1, 'gate', 2
WHERE NOT EXISTS (SELECT 1 FROM habnut_items_base WHERE sprite_id = 'gate_wood');

INSERT INTO habnut_items_base
  (sprite_id, name, description, type, width, length, stack_height,
   can_stack, can_sit, is_walkable, is_tradeable, interaction_type, interaction_modes)
SELECT 'dice_wooden', 'Dice', 'Click to roll.', 'floor', 1, 1, 1.000,
       0, 0, 0, 1, 'dice', 7
WHERE NOT EXISTS (SELECT 1 FROM habnut_items_base WHERE sprite_id = 'dice_wooden');

INSERT INTO habnut_items_base
  (sprite_id, name, description, type, width, length, stack_height,
   can_stack, can_sit, is_walkable, is_tradeable, interaction_type, interaction_modes)
SELECT 'teleport_pad', 'Teleport Pad', 'Links to another pad.', 'floor', 1, 1, 1.000,
       0, 0, 1, 1, 'teleport', 2
WHERE NOT EXISTS (SELECT 1 FROM habnut_items_base WHERE sprite_id = 'teleport_pad');

INSERT INTO habnut_items_base
  (sprite_id, name, description, type, width, length, stack_height,
   can_stack, can_sit, is_walkable, is_tradeable, interaction_type, interaction_modes)
SELECT 'roller_blue', 'Roller', 'Carries whatever is on it.', 'floor', 1, 1, 0.000,
       0, 0, 1, 1, 'roller', 1
WHERE NOT EXISTS (SELECT 1 FROM habnut_items_base WHERE sprite_id = 'roller_blue');

INSERT INTO habnut_items_base
  (sprite_id, name, description, type, width, length, stack_height,
   can_stack, can_sit, is_walkable, is_tradeable, interaction_type, interaction_modes)
SELECT 'plant_fern', 'Fern', 'Needs no watering.', 'floor', 1, 1, 1.000,
       0, 0, 0, 1, 'default', 1
WHERE NOT EXISTS (SELECT 1 FROM habnut_items_base WHERE sprite_id = 'plant_fern');

INSERT INTO habnut_items_base
  (sprite_id, name, description, type, width, length, stack_height,
   can_stack, can_sit, is_walkable, is_tradeable, interaction_type, interaction_modes)
SELECT 'bed_single', 'Single Bed', 'Lie down.', 'floor', 1, 2, 1.000,
       0, 1, 0, 1, 'bed', 1
WHERE NOT EXISTS (SELECT 1 FROM habnut_items_base WHERE sprite_id = 'bed_single');

INSERT INTO habnut_items_base
  (sprite_id, name, description, type, width, length, stack_height,
   can_stack, can_sit, is_walkable, is_tradeable, interaction_type, interaction_modes)
SELECT 'fireplace', 'Fireplace', 'Lights up.', 'floor', 2, 1, 1.000,
       0, 0, 0, 1, 'default', 2
WHERE NOT EXISTS (SELECT 1 FROM habnut_items_base WHERE sprite_id = 'fireplace');

INSERT INTO habnut_items_base
  (sprite_id, name, description, type, width, length, stack_height,
   can_stack, can_sit, is_walkable, is_tradeable, interaction_type, interaction_modes)
SELECT 'stage_block', 'Stage Block', 'Stand on it.', 'floor', 1, 1, 1.000,
       1, 0, 1, 1, 'default', 1
WHERE NOT EXISTS (SELECT 1 FROM habnut_items_base WHERE sprite_id = 'stage_block');

INSERT INTO habnut_items_base
  (sprite_id, name, description, type, width, length, stack_height,
   can_stack, can_sit, is_walkable, is_tradeable, interaction_type, interaction_modes)
SELECT 'poster_hotel', 'Hotel Poster', 'For the wall.', 'wall', 1, 1, 0.000,
       0, 0, 0, 1, 'default', 1
WHERE NOT EXISTS (SELECT 1 FROM habnut_items_base WHERE sprite_id = 'poster_hotel');

INSERT INTO habnut_items_base
  (sprite_id, name, description, type, width, length, stack_height,
   can_stack, can_sit, is_walkable, is_tradeable, interaction_type, interaction_modes)
SELECT 'window_small', 'Small Window', 'Lets the light in.', 'wall', 1, 1, 0.000,
       0, 0, 0, 1, 'default', 1
WHERE NOT EXISTS (SELECT 1 FROM habnut_items_base WHERE sprite_id = 'window_small');

INSERT INTO habnut_items_base
  (sprite_id, name, description, type, width, length, stack_height,
   can_stack, can_sit, is_walkable, is_tradeable, interaction_type, interaction_modes)
SELECT 'clock_wall', 'Wall Clock', 'Tells the time, roughly.', 'wall', 1, 1, 0.000,
       0, 0, 0, 1, 'default', 1
WHERE NOT EXISTS (SELECT 1 FROM habnut_items_base WHERE sprite_id = 'clock_wall');

-- ─── Catalogue ──────────────────────────────────────────────────────────────

INSERT INTO habnut_catalogue_pages
  (id, parent_id, name, caption, visible, world_id, layout, order_index, min_rank)
SELECT 1, NULL, 'Furniture', 'Furniture', 1, 'both', 'default_3x3', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM habnut_catalogue_pages WHERE id = 1);

INSERT INTO habnut_catalogue_pages
  (id, parent_id, name, caption, visible, world_id, layout, order_index, min_rank)
SELECT 2, 1, 'Seating', 'Somewhere to sit', 1, 'both', 'default_3x3', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM habnut_catalogue_pages WHERE id = 2);

INSERT INTO habnut_catalogue_pages
  (id, parent_id, name, caption, visible, world_id, layout, order_index, min_rank)
SELECT 3, 1, 'Tables', 'Tables and surfaces', 1, 'both', 'default_3x3', 2, 1
WHERE NOT EXISTS (SELECT 1 FROM habnut_catalogue_pages WHERE id = 3);

INSERT INTO habnut_catalogue_pages
  (id, parent_id, name, caption, visible, world_id, layout, order_index, min_rank)
SELECT 4, 1, 'Decoration', 'Making it yours', 1, 'both', 'default_3x3', 3, 1
WHERE NOT EXISTS (SELECT 1 FROM habnut_catalogue_pages WHERE id = 4);

INSERT INTO habnut_catalogue_pages
  (id, parent_id, name, caption, visible, world_id, layout, order_index, min_rank)
SELECT 5, NULL, 'Wall Items', 'For the walls', 1, 'both', 'default_3x3', 2, 1
WHERE NOT EXISTS (SELECT 1 FROM habnut_catalogue_pages WHERE id = 5);

INSERT INTO habnut_catalogue_pages
  (id, parent_id, name, caption, visible, world_id, layout, order_index, min_rank)
SELECT 6, NULL, 'Gadgets', 'Things that do things', 1, 'both', 'default_3x3', 3, 1
WHERE NOT EXISTS (SELECT 1 FROM habnut_catalogue_pages WHERE id = 6);

INSERT INTO habnut_catalogue_pages
  (id, parent_id, name, caption, visible, world_id, layout, order_index, min_rank)
SELECT 7, NULL, 'Staff', 'Staff only', 1, 'both', 'default_3x3', 9, 4
WHERE NOT EXISTS (SELECT 1 FROM habnut_catalogue_pages WHERE id = 7);

INSERT INTO habnut_catalogue_offers
  (page_id, base_id, name, description, items_json,
   credits_price, diamonds_price, is_gift, is_visible, order_index)
SELECT 2, b.id, b.name, b.description,
       CONCAT('[{"baseId":', b.id, ',"count":1}]'),
       15, 0, 1, 1, 1
FROM habnut_items_base b
WHERE b.sprite_id = 'chair_basic'
  AND EXISTS (SELECT 1 FROM habnut_catalogue_pages WHERE id = 2)
  AND NOT EXISTS (SELECT 1 FROM habnut_catalogue_offers o
                  WHERE o.page_id = 2 AND o.base_id = b.id);

INSERT INTO habnut_catalogue_offers
  (page_id, base_id, name, description, items_json,
   credits_price, diamonds_price, is_gift, is_visible, order_index)
SELECT 2, b.id, b.name, b.description,
       CONCAT('[{"baseId":', b.id, ',"count":1}]'),
       12, 0, 1, 1, 2
FROM habnut_items_base b
WHERE b.sprite_id = 'chair_plasto'
  AND EXISTS (SELECT 1 FROM habnut_catalogue_pages WHERE id = 2)
  AND NOT EXISTS (SELECT 1 FROM habnut_catalogue_offers o
                  WHERE o.page_id = 2 AND o.base_id = b.id);

INSERT INTO habnut_catalogue_offers
  (page_id, base_id, name, description, items_json,
   credits_price, diamonds_price, is_gift, is_visible, order_index)
SELECT 2, b.id, b.name, b.description,
       CONCAT('[{"baseId":', b.id, ',"count":1}]'),
       45, 0, 1, 1, 3
FROM habnut_items_base b
WHERE b.sprite_id = 'sofa_red'
  AND EXISTS (SELECT 1 FROM habnut_catalogue_pages WHERE id = 2)
  AND NOT EXISTS (SELECT 1 FROM habnut_catalogue_offers o
                  WHERE o.page_id = 2 AND o.base_id = b.id);

INSERT INTO habnut_catalogue_offers
  (page_id, base_id, name, description, items_json,
   credits_price, diamonds_price, is_gift, is_visible, order_index)
SELECT 2, b.id, b.name, b.description,
       CONCAT('[{"baseId":', b.id, ',"count":1}]'),
       60, 0, 1, 1, 4
FROM habnut_items_base b
WHERE b.sprite_id = 'bed_single'
  AND EXISTS (SELECT 1 FROM habnut_catalogue_pages WHERE id = 2)
  AND NOT EXISTS (SELECT 1 FROM habnut_catalogue_offers o
                  WHERE o.page_id = 2 AND o.base_id = b.id);

INSERT INTO habnut_catalogue_offers
  (page_id, base_id, name, description, items_json,
   credits_price, diamonds_price, is_gift, is_visible, order_index)
SELECT 3, b.id, b.name, b.description,
       CONCAT('[{"baseId":', b.id, ',"count":1}]'),
       30, 0, 1, 1, 1
FROM habnut_items_base b
WHERE b.sprite_id = 'table_wood'
  AND EXISTS (SELECT 1 FROM habnut_catalogue_pages WHERE id = 3)
  AND NOT EXISTS (SELECT 1 FROM habnut_catalogue_offers o
                  WHERE o.page_id = 3 AND o.base_id = b.id);

INSERT INTO habnut_catalogue_offers
  (page_id, base_id, name, description, items_json,
   credits_price, diamonds_price, is_gift, is_visible, order_index)
SELECT 3, b.id, b.name, b.description,
       CONCAT('[{"baseId":', b.id, ',"count":1}]'),
       18, 0, 1, 1, 2
FROM habnut_items_base b
WHERE b.sprite_id = 'table_small'
  AND EXISTS (SELECT 1 FROM habnut_catalogue_pages WHERE id = 3)
  AND NOT EXISTS (SELECT 1 FROM habnut_catalogue_offers o
                  WHERE o.page_id = 3 AND o.base_id = b.id);

INSERT INTO habnut_catalogue_offers
  (page_id, base_id, name, description, items_json,
   credits_price, diamonds_price, is_gift, is_visible, order_index)
SELECT 3, b.id, b.name, b.description,
       CONCAT('[{"baseId":', b.id, ',"count":1}]'),
       25, 0, 1, 1, 3
FROM habnut_items_base b
WHERE b.sprite_id = 'stage_block'
  AND EXISTS (SELECT 1 FROM habnut_catalogue_pages WHERE id = 3)
  AND NOT EXISTS (SELECT 1 FROM habnut_catalogue_offers o
                  WHERE o.page_id = 3 AND o.base_id = b.id);

INSERT INTO habnut_catalogue_offers
  (page_id, base_id, name, description, items_json,
   credits_price, diamonds_price, is_gift, is_visible, order_index)
SELECT 4, b.id, b.name, b.description,
       CONCAT('[{"baseId":', b.id, ',"count":1}]'),
       35, 0, 1, 1, 1
FROM habnut_items_base b
WHERE b.sprite_id = 'rug_blue'
  AND EXISTS (SELECT 1 FROM habnut_catalogue_pages WHERE id = 4)
  AND NOT EXISTS (SELECT 1 FROM habnut_catalogue_offers o
                  WHERE o.page_id = 4 AND o.base_id = b.id);

INSERT INTO habnut_catalogue_offers
  (page_id, base_id, name, description, items_json,
   credits_price, diamonds_price, is_gift, is_visible, order_index)
SELECT 4, b.id, b.name, b.description,
       CONCAT('[{"baseId":', b.id, ',"count":1}]'),
       20, 0, 1, 1, 2
FROM habnut_items_base b
WHERE b.sprite_id = 'plant_fern'
  AND EXISTS (SELECT 1 FROM habnut_catalogue_pages WHERE id = 4)
  AND NOT EXISTS (SELECT 1 FROM habnut_catalogue_offers o
                  WHERE o.page_id = 4 AND o.base_id = b.id);

INSERT INTO habnut_catalogue_offers
  (page_id, base_id, name, description, items_json,
   credits_price, diamonds_price, is_gift, is_visible, order_index)
SELECT 4, b.id, b.name, b.description,
       CONCAT('[{"baseId":', b.id, ',"count":1}]'),
       40, 0, 1, 1, 3
FROM habnut_items_base b
WHERE b.sprite_id = 'lamp_standing'
  AND EXISTS (SELECT 1 FROM habnut_catalogue_pages WHERE id = 4)
  AND NOT EXISTS (SELECT 1 FROM habnut_catalogue_offers o
                  WHERE o.page_id = 4 AND o.base_id = b.id);

INSERT INTO habnut_catalogue_offers
  (page_id, base_id, name, description, items_json,
   credits_price, diamonds_price, is_gift, is_visible, order_index)
SELECT 4, b.id, b.name, b.description,
       CONCAT('[{"baseId":', b.id, ',"count":1}]'),
       120, 0, 1, 1, 4
FROM habnut_items_base b
WHERE b.sprite_id = 'fireplace'
  AND EXISTS (SELECT 1 FROM habnut_catalogue_pages WHERE id = 4)
  AND NOT EXISTS (SELECT 1 FROM habnut_catalogue_offers o
                  WHERE o.page_id = 4 AND o.base_id = b.id);

INSERT INTO habnut_catalogue_offers
  (page_id, base_id, name, description, items_json,
   credits_price, diamonds_price, is_gift, is_visible, order_index)
SELECT 5, b.id, b.name, b.description,
       CONCAT('[{"baseId":', b.id, ',"count":1}]'),
       10, 0, 1, 1, 1
FROM habnut_items_base b
WHERE b.sprite_id = 'poster_hotel'
  AND EXISTS (SELECT 1 FROM habnut_catalogue_pages WHERE id = 5)
  AND NOT EXISTS (SELECT 1 FROM habnut_catalogue_offers o
                  WHERE o.page_id = 5 AND o.base_id = b.id);

INSERT INTO habnut_catalogue_offers
  (page_id, base_id, name, description, items_json,
   credits_price, diamonds_price, is_gift, is_visible, order_index)
SELECT 5, b.id, b.name, b.description,
       CONCAT('[{"baseId":', b.id, ',"count":1}]'),
       25, 0, 1, 1, 2
FROM habnut_items_base b
WHERE b.sprite_id = 'window_small'
  AND EXISTS (SELECT 1 FROM habnut_catalogue_pages WHERE id = 5)
  AND NOT EXISTS (SELECT 1 FROM habnut_catalogue_offers o
                  WHERE o.page_id = 5 AND o.base_id = b.id);

INSERT INTO habnut_catalogue_offers
  (page_id, base_id, name, description, items_json,
   credits_price, diamonds_price, is_gift, is_visible, order_index)
SELECT 5, b.id, b.name, b.description,
       CONCAT('[{"baseId":', b.id, ',"count":1}]'),
       30, 0, 1, 1, 3
FROM habnut_items_base b
WHERE b.sprite_id = 'clock_wall'
  AND EXISTS (SELECT 1 FROM habnut_catalogue_pages WHERE id = 5)
  AND NOT EXISTS (SELECT 1 FROM habnut_catalogue_offers o
                  WHERE o.page_id = 5 AND o.base_id = b.id);

INSERT INTO habnut_catalogue_offers
  (page_id, base_id, name, description, items_json,
   credits_price, diamonds_price, is_gift, is_visible, order_index)
SELECT 6, b.id, b.name, b.description,
       CONCAT('[{"baseId":', b.id, ',"count":1}]'),
       50, 0, 1, 1, 1
FROM habnut_items_base b
WHERE b.sprite_id = 'dice_wooden'
  AND EXISTS (SELECT 1 FROM habnut_catalogue_pages WHERE id = 6)
  AND NOT EXISTS (SELECT 1 FROM habnut_catalogue_offers o
                  WHERE o.page_id = 6 AND o.base_id = b.id);

INSERT INTO habnut_catalogue_offers
  (page_id, base_id, name, description, items_json,
   credits_price, diamonds_price, is_gift, is_visible, order_index)
SELECT 6, b.id, b.name, b.description,
       CONCAT('[{"baseId":', b.id, ',"count":1}]'),
       40, 0, 1, 1, 2
FROM habnut_items_base b
WHERE b.sprite_id = 'gate_wood'
  AND EXISTS (SELECT 1 FROM habnut_catalogue_pages WHERE id = 6)
  AND NOT EXISTS (SELECT 1 FROM habnut_catalogue_offers o
                  WHERE o.page_id = 6 AND o.base_id = b.id);

INSERT INTO habnut_catalogue_offers
  (page_id, base_id, name, description, items_json,
   credits_price, diamonds_price, is_gift, is_visible, order_index)
SELECT 6, b.id, b.name, b.description,
       CONCAT('[{"baseId":', b.id, ',"count":1}]'),
       0, 5, 1, 1, 3
FROM habnut_items_base b
WHERE b.sprite_id = 'teleport_pad'
  AND EXISTS (SELECT 1 FROM habnut_catalogue_pages WHERE id = 6)
  AND NOT EXISTS (SELECT 1 FROM habnut_catalogue_offers o
                  WHERE o.page_id = 6 AND o.base_id = b.id);

INSERT INTO habnut_catalogue_offers
  (page_id, base_id, name, description, items_json,
   credits_price, diamonds_price, is_gift, is_visible, order_index)
SELECT 6, b.id, b.name, b.description,
       CONCAT('[{"baseId":', b.id, ',"count":1}]'),
       75, 0, 1, 1, 4
FROM habnut_items_base b
WHERE b.sprite_id = 'roller_blue'
  AND EXISTS (SELECT 1 FROM habnut_catalogue_pages WHERE id = 6)
  AND NOT EXISTS (SELECT 1 FROM habnut_catalogue_offers o
                  WHERE o.page_id = 6 AND o.base_id = b.id);
