-- Habnut — demo world
--
-- Applied only by `habnutctl dev up`, never by an install. It fills a local
-- hotel with enough to look at and poke immediately: accounts you can sign in
-- as, rooms with furniture already in them, people to be friends with, money
-- to spend, and something on the forum.
--
-- Without this, a correctly installed local hotel is an empty lobby with
-- nobody in it, which tells you nothing about whether it works.
--
-- The password on every demo account is the word password. These accounts are
-- for a hotel running on your own machine and nowhere else.
--
-- No string literal here contains a backslash, for the same reason as base.sql.

-- ─── People ─────────────────────────────────────────────────────────────────
--
-- The hash is bcrypt of 'password' at cost 10. Written out rather than
-- generated so this file seeds the same accounts every time, on either
-- platform, with nothing but a database to hand.

INSERT INTO habnut_users
  (username, email, password_hash, figure, gender, motto, rank,
   email_verified, credits, diamonds, nut_points, achievement_score,
   membership_tier, member_since)
SELECT 'tupci', 'tupci@icloud.com',
       '$2y$10$K/m4y0kKgdMgA2UOV2.oS.jJLfUx2.JeLrK3Agi/aEvQ4ZAQNsx.S',
       'hd-180-1.ch-210-66.lg-280-110.sh-300-91.ha-1012-110',
       'M', 'Running the place', 7,
       1, 50000, 500, 10000, 420,
       'gold', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM habnut_users WHERE username = 'tupci');

INSERT INTO habnut_users
  (username, email, password_hash, figure, gender, motto, rank,
   email_verified, credits, diamonds, nut_points, achievement_score, member_since)
SELECT 'Robbie', 'robbie@habnut.test',
       '$2y$10$K/m4y0kKgdMgA2UOV2.oS.jJLfUx2.JeLrK3Agi/aEvQ4ZAQNsx.S',
       'hd-185-2.ch-215-66.lg-285-110.sh-305-62',
       'M', 'First one in every morning', 1,
       1, 500, 5, 120, 35, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM habnut_users WHERE username = 'Robbie');

INSERT INTO habnut_users
  (username, email, password_hash, figure, gender, motto, rank,
   email_verified, credits, diamonds, nut_points, achievement_score, member_since)
SELECT 'Marnie', 'marnie@habnut.test',
       '$2y$10$K/m4y0kKgdMgA2UOV2.oS.jJLfUx2.JeLrK3Agi/aEvQ4ZAQNsx.S',
       'hd-600-1.ch-635-70.lg-716-66.sh-907-62.ha-3129-73',
       'F', 'Collecting rare sofas', 2,
       1, 1200, 20, 640, 88, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM habnut_users WHERE username = 'Marnie');

INSERT INTO habnut_users
  (username, email, password_hash, figure, gender, motto, rank,
   email_verified, credits, diamonds, nut_points, achievement_score, member_since)
SELECT 'Hal', 'hal@habnut.test',
       '$2y$10$K/m4y0kKgdMgA2UOV2.oS.jJLfUx2.JeLrK3Agi/aEvQ4ZAQNsx.S',
       'hd-190-3.ch-220-82.lg-275-73.sh-290-80',
       'M', 'Ask me if you need a hand', 4,
       1, 2000, 50, 900, 150, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM habnut_users WHERE username = 'Hal');

-- ─── Friends ────────────────────────────────────────────────────────────────
--
-- A pair is stored once, lower id first, so each is inserted in that order
-- rather than as written here.

INSERT INTO habnut_friends (user_a, user_b, accepted)
SELECT LEAST(a.id, b.id), GREATEST(a.id, b.id), 1
FROM habnut_users a, habnut_users b
WHERE a.username = 'tupci' AND b.username = 'Robbie'
  AND NOT EXISTS (
    SELECT 1 FROM habnut_friends f
    WHERE f.user_a = LEAST(a.id, b.id) AND f.user_b = GREATEST(a.id, b.id));

INSERT INTO habnut_friends (user_a, user_b, accepted)
SELECT LEAST(a.id, b.id), GREATEST(a.id, b.id), 1
FROM habnut_users a, habnut_users b
WHERE a.username = 'tupci' AND b.username = 'Marnie'
  AND NOT EXISTS (
    SELECT 1 FROM habnut_friends f
    WHERE f.user_a = LEAST(a.id, b.id) AND f.user_b = GREATEST(a.id, b.id));

INSERT INTO habnut_friends (user_a, user_b, accepted)
SELECT LEAST(a.id, b.id), GREATEST(a.id, b.id), 1
FROM habnut_users a, habnut_users b
WHERE a.username = 'Robbie' AND b.username = 'Marnie'
  AND NOT EXISTS (
    SELECT 1 FROM habnut_friends f
    WHERE f.user_a = LEAST(a.id, b.id) AND f.user_b = GREATEST(a.id, b.id));

-- ─── Rooms ──────────────────────────────────────────────────────────────────

INSERT INTO habnut_rooms
  (name, description, owner_id, model_id, category_id, world_id, access_type,
   max_visitors, is_promoted, wallpaper, floor_pattern, landscape_colour)
SELECT 'Hotel Lobby', 'Where everybody arrives.', u.id, 'model_lobby', 1, 'classic', 0,
       50, 1, '201', '301', '1.1'
FROM habnut_users u
WHERE u.username = 'tupci'
  AND NOT EXISTS (SELECT 1 FROM habnut_rooms WHERE name = 'Hotel Lobby');

INSERT INTO habnut_rooms
  (name, description, owner_id, model_id, category_id, world_id, access_type,
   max_visitors, is_promoted, wallpaper, floor_pattern, landscape_colour)
SELECT 'The Grand Hall', 'High ceilings and a long table.', u.id, 'model_hall', 1, 'classic', 0,
       40, 1, '110', '205', '1.3'
FROM habnut_users u
WHERE u.username = 'tupci'
  AND NOT EXISTS (SELECT 1 FROM habnut_rooms WHERE name = 'The Grand Hall');

INSERT INTO habnut_rooms
  (name, description, owner_id, model_id, category_id, world_id, access_type,
   max_visitors, wallpaper, floor_pattern, landscape_colour)
SELECT 'Marnie''s Front Room', 'Mind the rug.', u.id, 'model_a', 1, 'classic', 0,
       25, '305', '108', '1.1'
FROM habnut_users u
WHERE u.username = 'Marnie'
  AND NOT EXISTS (SELECT 1 FROM habnut_rooms WHERE name = 'Marnie''s Front Room');

INSERT INTO habnut_rooms
  (name, description, owner_id, model_id, category_id, world_id, access_type,
   max_visitors, is_promoted, wallpaper, floor_pattern, landscape_colour)
SELECT 'The Pool', 'Deep end on the left.', u.id, 'model_pool', 1, 'classic', 0,
       30, 1, '401', '402', '1.4'
FROM habnut_users u
WHERE u.username = 'tupci'
  AND NOT EXISTS (SELECT 1 FROM habnut_rooms WHERE name = 'The Pool');

-- ─── Furniture, already standing in the rooms ───────────────────────────────
--
-- One statement per piece, naming the room and the tile. Longer than a staging
-- table, but a staging table would need DROP TEMPORARY TABLE, which MariaDB
-- understands and H2 does not — and this file has to apply identically in both
-- so the test is testing the thing that ships.

INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 6, 3, 0, 4, 0
FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'sofa_red'
WHERE r.name = 'Hotel Lobby'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items existing
                  WHERE existing.room_id = r.id AND existing.x = 6 AND existing.y = 3);

INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 9, 3, 0, 4, 0
FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'sofa_red'
WHERE r.name = 'Hotel Lobby'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items existing
                  WHERE existing.room_id = r.id AND existing.x = 9 AND existing.y = 3);

INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 7, 5, 0, 2, 0
FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'table_wood'
WHERE r.name = 'Hotel Lobby'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items existing
                  WHERE existing.room_id = r.id AND existing.x = 7 AND existing.y = 5);

INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 4, 2, 0, 2, 0
FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'plant_fern'
WHERE r.name = 'Hotel Lobby'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items existing
                  WHERE existing.room_id = r.id AND existing.x = 4 AND existing.y = 2);

INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 12, 2, 0, 2, 0
FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'plant_fern'
WHERE r.name = 'Hotel Lobby'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items existing
                  WHERE existing.room_id = r.id AND existing.x = 12 AND existing.y = 2);

INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 5, 7, 0, 2, 0
FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'lamp_standing'
WHERE r.name = 'Hotel Lobby'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items existing
                  WHERE existing.room_id = r.id AND existing.x = 5 AND existing.y = 7);

INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 7, 8, 0, 2, 0
FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'rug_blue'
WHERE r.name = 'Hotel Lobby'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items existing
                  WHERE existing.room_id = r.id AND existing.x = 7 AND existing.y = 8);

INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 5, 4, 0, 2, 0
FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'table_wood'
WHERE r.name = 'The Grand Hall'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items existing
                  WHERE existing.room_id = r.id AND existing.x = 5 AND existing.y = 4);

INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 7, 4, 0, 2, 0
FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'table_wood'
WHERE r.name = 'The Grand Hall'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items existing
                  WHERE existing.room_id = r.id AND existing.x = 7 AND existing.y = 4);

INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 5, 3, 0, 4, 0
FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'chair_basic'
WHERE r.name = 'The Grand Hall'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items existing
                  WHERE existing.room_id = r.id AND existing.x = 5 AND existing.y = 3);

INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 6, 3, 0, 4, 0
FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'chair_basic'
WHERE r.name = 'The Grand Hall'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items existing
                  WHERE existing.room_id = r.id AND existing.x = 6 AND existing.y = 3);

INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 7, 3, 0, 4, 0
FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'chair_basic'
WHERE r.name = 'The Grand Hall'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items existing
                  WHERE existing.room_id = r.id AND existing.x = 7 AND existing.y = 3);

INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 10, 2, 0, 2, 0
FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'fireplace'
WHERE r.name = 'The Grand Hall'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items existing
                  WHERE existing.room_id = r.id AND existing.x = 10 AND existing.y = 2);

INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 12, 6, 0, 2, 0
FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'stage_block'
WHERE r.name = 'The Grand Hall'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items existing
                  WHERE existing.room_id = r.id AND existing.x = 12 AND existing.y = 6);

INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 13, 6, 0, 2, 0
FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'stage_block'
WHERE r.name = 'The Grand Hall'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items existing
                  WHERE existing.room_id = r.id AND existing.x = 13 AND existing.y = 6);

INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 3, 3, 0, 4, 0
FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'sofa_red'
WHERE r.name = 'Marnie''s Front Room'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items existing
                  WHERE existing.room_id = r.id AND existing.x = 3 AND existing.y = 3);

INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 3, 5, 0, 2, 0
FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'table_small'
WHERE r.name = 'Marnie''s Front Room'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items existing
                  WHERE existing.room_id = r.id AND existing.x = 3 AND existing.y = 5);

INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 4, 4, 0, 2, 0
FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'rug_blue'
WHERE r.name = 'Marnie''s Front Room'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items existing
                  WHERE existing.room_id = r.id AND existing.x = 4 AND existing.y = 4);

INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 2, 2, 0, 2, 0
FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'lamp_standing'
WHERE r.name = 'Marnie''s Front Room'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items existing
                  WHERE existing.room_id = r.id AND existing.x = 2 AND existing.y = 2);

INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 7, 6, 0, 2, 0
FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'bed_single'
WHERE r.name = 'Marnie''s Front Room'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items existing
                  WHERE existing.room_id = r.id AND existing.x = 7 AND existing.y = 6);

INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 2, 1, 0, 2, 0
FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'chair_plasto'
WHERE r.name = 'The Pool'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items existing
                  WHERE existing.room_id = r.id AND existing.x = 2 AND existing.y = 1);

INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 3, 1, 0, 2, 0
FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'chair_plasto'
WHERE r.name = 'The Pool'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items existing
                  WHERE existing.room_id = r.id AND existing.x = 3 AND existing.y = 1);

INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 9, 7, 0, 2, 0
FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'dice_wooden'
WHERE r.name = 'The Pool'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items existing
                  WHERE existing.room_id = r.id AND existing.x = 9 AND existing.y = 7);

INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 10, 1, 0, 2, 0
FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'teleport_pad'
WHERE r.name = 'The Pool'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items existing
                  WHERE existing.room_id = r.id AND existing.x = 10 AND existing.y = 1);

INSERT INTO habnut_floor_items (base_id, room_id, owner_id, x, y, z, rotation, state)
SELECT b.id, r.id, r.owner_id, 1, 7, 0, 2, 0
FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'roller_blue'
WHERE r.name = 'The Pool'
  AND NOT EXISTS (SELECT 1 FROM habnut_floor_items existing
                  WHERE existing.room_id = r.id AND existing.x = 1 AND existing.y = 7);

-- A poster for the lobby wall, so wall items are visible too.
INSERT INTO habnut_wall_items
  (base_id, room_id, owner_id, wall_x, wall_y, wall_position, sx, sy, state)
SELECT b.id, r.id, r.owner_id, 2, 1, ':w=2,1 l=3,2 l', 3, 2, 0
FROM habnut_rooms r
JOIN habnut_items_base b ON b.sprite_id = 'poster_hotel'
WHERE r.name = 'Hotel Lobby'
  AND NOT EXISTS (
    SELECT 1 FROM habnut_wall_items existing WHERE existing.room_id = r.id);

-- ─── Something in the inventory ─────────────────────────────────────────────

INSERT INTO habnut_items_inventory (base_id, owner_id, type)
SELECT b.id, u.id, 'floor'
FROM habnut_users u
JOIN habnut_items_base b ON b.sprite_id IN
  ('chair_basic', 'table_small', 'plant_fern', 'dice_wooden', 'poster_hotel')
WHERE u.username = 'tupci'
  AND NOT EXISTS (
    SELECT 1 FROM habnut_items_inventory existing
    WHERE existing.owner_id = u.id AND existing.base_id = b.id);

-- ─── Something on the forum ─────────────────────────────────────────────────

INSERT INTO habnut_forum_threads (category_id, group_id, author_id, title, pinned, locked)
SELECT (SELECT MIN(id) FROM habnut_forum_categories), NULL, u.id,
       'Welcome to the hotel', 1, 0
FROM habnut_users u
WHERE u.username = 'tupci'
  AND EXISTS (SELECT 1 FROM habnut_forum_categories)
  AND NOT EXISTS (
    SELECT 1 FROM habnut_forum_threads WHERE title = 'Welcome to the hotel');

INSERT INTO habnut_forum_posts (thread_id, author_id, body)
SELECT t.id, t.author_id,
       'This hotel is running on your own machine. Sign in as tupci, open the hotel and have a look round. Everything you can see is in the database, so you can change any of it and watch what happens.'
FROM habnut_forum_threads t
WHERE t.title = 'Welcome to the hotel'
  AND NOT EXISTS (SELECT 1 FROM habnut_forum_posts WHERE thread_id = t.id);

UPDATE habnut_forum_threads t
SET t.reply_count = 1,
    t.last_post_id = (SELECT MIN(p.id) FROM habnut_forum_posts p WHERE p.thread_id = t.id),
    t.last_poster_id = t.author_id
WHERE t.title = 'Welcome to the hotel'
  AND t.reply_count = 0
  AND EXISTS (SELECT 1 FROM habnut_forum_posts p WHERE p.thread_id = t.id);
