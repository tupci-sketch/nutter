-- Public rooms for a new hotel: furnished, owned by the first administrator.
-- Generated from the room shapes and each item's real footprint; every
-- placement is on floor, at the floor's height, with nothing overlapping.
-- Safe to run again: a room or item already there is left alone.

INSERT INTO habnut_rooms (name, description, owner_id, model_id, category_id, world_id, access_type,
  max_visitors, is_promoted, wallpaper, floor_pattern, landscape_colour)
SELECT 'Habnut Lobby', 'Where everybody arrives. Grab a seat.', u.id, 'model_lobby', 1, 'classic', 0, 50, 1, '201', '301', '1.1'
FROM habnut_users u WHERE u.`rank` >= 7
  AND NOT EXISTS (SELECT 1 FROM habnut_rooms WHERE name = 'Habnut Lobby' AND world_id = 'classic')
ORDER BY u.id LIMIT 1;

INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 8, 6, 0, 0, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'carpet_standard'
WHERE r.name = 'Habnut Lobby' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 8 AND e.y = 6);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 1, 5, 0, 0, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'doormat_plain'
WHERE r.name = 'Habnut Lobby' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 1 AND e.y = 5);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 3, 1, 2, 4, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'sofa_polyfon'
WHERE r.name = 'Habnut Lobby' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 3 AND e.y = 1);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 7, 1, 2, 4, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'sofa_polyfon'
WHERE r.name = 'Habnut Lobby' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 7 AND e.y = 1);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 12, 1, 2, 4, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'sofa_polyfon'
WHERE r.name = 'Habnut Lobby' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 12 AND e.y = 1);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 16, 1, 2, 4, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'sofa_polyfon'
WHERE r.name = 'Habnut Lobby' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 16 AND e.y = 1);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 1, 1, 2, 0, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'plant_yukka'
WHERE r.name = 'Habnut Lobby' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 1 AND e.y = 1);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 19, 1, 2, 0, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'plant_yukka'
WHERE r.name = 'Habnut Lobby' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 19 AND e.y = 1);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 10, 1, 2, 0, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'lamp_basic'
WHERE r.name = 'Habnut Lobby' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 10 AND e.y = 1);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 3, 3, 1, 4, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'chair_norja'
WHERE r.name = 'Habnut Lobby' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 3 AND e.y = 3);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 5, 3, 1, 4, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'chair_norja'
WHERE r.name = 'Habnut Lobby' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 5 AND e.y = 3);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 14, 3, 1, 4, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'chair_norja'
WHERE r.name = 'Habnut Lobby' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 14 AND e.y = 3);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 16, 3, 1, 4, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'chair_norja'
WHERE r.name = 'Habnut Lobby' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 16 AND e.y = 3);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 9, 3, 1, 0, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'plant_pineapple'
WHERE r.name = 'Habnut Lobby' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 9 AND e.y = 3);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 11, 3, 1, 0, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'plant_pineapple'
WHERE r.name = 'Habnut Lobby' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 11 AND e.y = 3);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 3, 7, 0, 4, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'couch_norja'
WHERE r.name = 'Habnut Lobby' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 3 AND e.y = 7);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 3, 8, 0, 0, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'table_norja_med'
WHERE r.name = 'Habnut Lobby' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 3 AND e.y = 8);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 3, 10, 0, 0, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'couch_norja'
WHERE r.name = 'Habnut Lobby' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 3 AND e.y = 10);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 14, 7, 0, 4, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'couch_norja'
WHERE r.name = 'Habnut Lobby' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 14 AND e.y = 7);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 14, 8, 0, 0, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'table_norja_med'
WHERE r.name = 'Habnut Lobby' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 14 AND e.y = 8);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 14, 10, 0, 0, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'couch_norja'
WHERE r.name = 'Habnut Lobby' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 14 AND e.y = 10);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 1, 11, 0, 0, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'lamp_armas'
WHERE r.name = 'Habnut Lobby' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 1 AND e.y = 11);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 19, 11, 0, 0, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'lamp_armas'
WHERE r.name = 'Habnut Lobby' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 19 AND e.y = 11);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 19, 6, 0, 0, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'plant_big_cactus'
WHERE r.name = 'Habnut Lobby' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 19 AND e.y = 6);

INSERT INTO habnut_rooms (name, description, owner_id, model_id, category_id, world_id, access_type,
  max_visitors, is_promoted, wallpaper, floor_pattern, landscape_colour)
SELECT 'The Café', 'Tables by the window, coffee on the counter.', u.id, 'model_hall', 1, 'classic', 0, 40, 1, '110', '205', '1.3'
FROM habnut_users u WHERE u.`rank` >= 7
  AND NOT EXISTS (SELECT 1 FROM habnut_rooms WHERE name = 'The Café' AND world_id = 'classic')
ORDER BY u.id LIMIT 1;

INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 2, 1, 0, 4, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'bardesk_polyfon'
WHERE r.name = 'The Café' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 2 AND e.y = 1);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 4, 1, 0, 4, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'bardesk_polyfon'
WHERE r.name = 'The Café' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 4 AND e.y = 1);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 6, 1, 0, 4, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'bar_polyfon'
WHERE r.name = 'The Café' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 6 AND e.y = 1);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 2, 3, 0, 0, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'bar_chair_armas'
WHERE r.name = 'The Café' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 2 AND e.y = 3);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 3, 3, 0, 0, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'bar_chair_armas'
WHERE r.name = 'The Café' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 3 AND e.y = 3);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 4, 3, 0, 0, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'bar_chair_armas'
WHERE r.name = 'The Café' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 4 AND e.y = 3);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 5, 3, 0, 0, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'bar_chair_armas'
WHERE r.name = 'The Café' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 5 AND e.y = 3);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 9, 2, 0, 0, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'table_armas'
WHERE r.name = 'The Café' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 9 AND e.y = 2);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 8, 2, 0, 2, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'small_chair_armas'
WHERE r.name = 'The Café' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 8 AND e.y = 2);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 8, 3, 0, 2, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'small_chair_armas'
WHERE r.name = 'The Café' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 8 AND e.y = 3);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 11, 2, 0, 6, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'small_chair_armas'
WHERE r.name = 'The Café' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 11 AND e.y = 2);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 11, 3, 0, 6, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'small_chair_armas'
WHERE r.name = 'The Café' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 11 AND e.y = 3);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 9, 8, 0, 0, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'table_armas'
WHERE r.name = 'The Café' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 9 AND e.y = 8);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 8, 8, 0, 2, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'small_chair_armas'
WHERE r.name = 'The Café' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 8 AND e.y = 8);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 8, 9, 0, 2, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'small_chair_armas'
WHERE r.name = 'The Café' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 8 AND e.y = 9);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 11, 8, 0, 6, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'small_chair_armas'
WHERE r.name = 'The Café' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 11 AND e.y = 8);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 11, 9, 0, 6, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'small_chair_armas'
WHERE r.name = 'The Café' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 11 AND e.y = 9);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 3, 8, 0, 0, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'table_armas'
WHERE r.name = 'The Café' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 3 AND e.y = 8);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 3, 7, 0, 4, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'bench_armas'
WHERE r.name = 'The Café' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 3 AND e.y = 7);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 3, 10, 0, 0, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'bench_armas'
WHERE r.name = 'The Café' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 3 AND e.y = 10);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 15, 1, 0, 0, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'plant_rose'
WHERE r.name = 'The Café' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 15 AND e.y = 1);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 15, 11, 0, 0, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'plant_rose'
WHERE r.name = 'The Café' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 15 AND e.y = 11);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 13, 1, 0, 0, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'lamp_armas'
WHERE r.name = 'The Café' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 13 AND e.y = 1);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 13, 11, 0, 0, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'lamp_armas'
WHERE r.name = 'The Café' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 13 AND e.y = 11);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 1, 1, 0, 2, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'shelves_norja'
WHERE r.name = 'The Café' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 1 AND e.y = 1);

INSERT INTO habnut_rooms (name, description, owner_id, model_id, category_id, world_id, access_type,
  max_visitors, is_promoted, wallpaper, floor_pattern, landscape_colour)
SELECT 'The Pool', 'Deep end on the left. No running.', u.id, 'model_pool', 1, 'classic', 0, 30, 1, '401', '402', '1.4'
FROM habnut_users u WHERE u.`rank` >= 7
  AND NOT EXISTS (SELECT 1 FROM habnut_rooms WHERE name = 'The Pool' AND world_id = 'classic')
ORDER BY u.id LIMIT 1;

INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 1, 1, 0, 4, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'summer_chair*1'
WHERE r.name = 'The Pool' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 1 AND e.y = 1);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 2, 1, 0, 4, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'summer_chair*1'
WHERE r.name = 'The Pool' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 2 AND e.y = 1);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 9, 1, 0, 4, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'summer_chair*1'
WHERE r.name = 'The Pool' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 9 AND e.y = 1);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 10, 1, 0, 4, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'summer_chair*1'
WHERE r.name = 'The Pool' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 10 AND e.y = 1);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 1, 8, 0, 0, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'summer_chair*1'
WHERE r.name = 'The Pool' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 1 AND e.y = 8);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 2, 8, 0, 0, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'summer_chair*1'
WHERE r.name = 'The Pool' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 2 AND e.y = 8);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 9, 8, 0, 0, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'summer_chair*1'
WHERE r.name = 'The Pool' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 9 AND e.y = 8);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 10, 8, 0, 0, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'summer_chair*1'
WHERE r.name = 'The Pool' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 10 AND e.y = 8);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 11, 1, 0, 0, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'plant_big_cactus'
WHERE r.name = 'The Pool' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 11 AND e.y = 1);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 11, 8, 0, 0, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'plant_big_cactus'
WHERE r.name = 'The Pool' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 11 AND e.y = 8);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 10, 4, 0, 0, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'plant_pineapple'
WHERE r.name = 'The Pool' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 10 AND e.y = 4);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 11, 5, 0, 0, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'plant_pineapple'
WHERE r.name = 'The Pool' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 11 AND e.y = 5);

INSERT INTO habnut_rooms (name, description, owner_id, model_id, category_id, world_id, access_type,
  max_visitors, is_promoted, wallpaper, floor_pattern, landscape_colour)
SELECT 'The Lounge', 'Somewhere quiet to sit and talk.', u.id, 'model_b', 1, 'classic', 0, 25, 1, '305', '108', '1.1'
FROM habnut_users u WHERE u.`rank` >= 7
  AND NOT EXISTS (SELECT 1 FROM habnut_rooms WHERE name = 'The Lounge' AND world_id = 'classic')
ORDER BY u.id LIMIT 1;

INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 5, 4, 0, 0, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'carpet_polar'
WHERE r.name = 'The Lounge' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 5 AND e.y = 4);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 5, 2, 0, 4, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'sofa_polyfon'
WHERE r.name = 'The Lounge' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 5 AND e.y = 2);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 4, 5, 0, 2, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'sofachair_polyfon'
WHERE r.name = 'The Lounge' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 4 AND e.y = 5);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 7, 5, 0, 6, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'sofachair_polyfon'
WHERE r.name = 'The Lounge' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 7 AND e.y = 5);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 5, 5, 0, 0, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'table_polyfon_med'
WHERE r.name = 'The Lounge' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 5 AND e.y = 5);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 5, 7, 0, 0, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'sofa_polyfon'
WHERE r.name = 'The Lounge' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 5 AND e.y = 7);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 11, 4, 0, 0, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'tv_luxus'
WHERE r.name = 'The Lounge' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 11 AND e.y = 4);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 1, 1, 0, 0, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'lamp_basic'
WHERE r.name = 'The Lounge' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 1 AND e.y = 1);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 11, 1, 0, 0, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'lamp_basic'
WHERE r.name = 'The Lounge' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 11 AND e.y = 1);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 1, 10, 0, 0, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'plant_yukka'
WHERE r.name = 'The Lounge' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 1 AND e.y = 10);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 11, 10, 0, 0, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'plant_yukka'
WHERE r.name = 'The Lounge' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 11 AND e.y = 10);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 9, 1, 0, 4, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'shelves_norja'
WHERE r.name = 'The Lounge' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 9 AND e.y = 1);
INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 10, 1, 0, 4, 0 FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'shelves_norja'
WHERE r.name = 'The Lounge' AND r.world_id = 'classic'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items e WHERE e.room_id = r.id AND e.base_id = b.id AND e.x = 10 AND e.y = 1);
