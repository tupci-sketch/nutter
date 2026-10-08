-- One furniture definition per classname.
--
-- The client draws a piece of furniture by its classname, which the hotel
-- stores as sprite_id; two rows with the same one are two definitions of the
-- same drawing, and the importer that brings furniture in from an asset build
-- relies on this key to update an item rather than add it a second time.
ALTER TABLE habnut_items_base
    ADD UNIQUE KEY uq_items_base_sprite (sprite_id);
