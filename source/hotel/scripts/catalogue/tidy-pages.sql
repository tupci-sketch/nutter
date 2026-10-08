-- Catalogue pages whose names came through as internal codes get proper
-- names; an empty top-level tab is hidden. Safe to run again.
UPDATE catalog_pages SET caption = 'Neon Punk' WHERE caption_save = 'neonpunk_c20';
UPDATE catalog_pages SET caption = 'Gothic Cafe' WHERE caption_save = 'gothiccafe_c20';
UPDATE catalog_pages SET caption = 'Sunset Cafe' WHERE caption_save = 'sunsetcafe_c20';
UPDATE catalog_pages SET caption = 'Zen Garden 2020' WHERE caption_save = 'zengarden_c20';
UPDATE catalog_pages SET caption = 'Rainy Day 2020' WHERE caption_save = 'rainyday_c20';
UPDATE catalog_pages SET caption = 'Habnut 20' WHERE caption_save = 'habbo20_c20';
UPDATE catalog_pages SET caption = 'Dark Elegant' WHERE caption_save = 'darkelegant_c20';
UPDATE catalog_pages SET caption = 'Dark Modern' WHERE caption_save = 'darkmodern_c20';
UPDATE catalog_pages SET caption = 'Black Rose Gold' WHERE caption = 'Blackrosegold';
UPDATE catalog_pages SET caption = 'Cyberpunk' WHERE caption_save = 'cyberpunk20';
UPDATE catalog_pages SET caption = 'Garden 2021' WHERE caption_save = 'garden21';
UPDATE catalog_pages SET caption = 'Art of India' WHERE caption_save = 'temp_art_n_india';
UPDATE catalog_pages SET caption = 'New Furniture', page_text1 = 'Fresh from the workshop: the newest furniture in Habnut.'
 WHERE caption_save = 'new_release';
UPDATE catalog_pages SET visible = '0' WHERE caption_save = 'habbicons'
 AND NOT EXISTS (SELECT 1 FROM catalog_items c WHERE c.page_id = catalog_pages.id);

-- The 2020 furniture lines sat in a staff-only "New Furniture" folder:
-- they belong in Furni By Line with everything else. A line already there
-- under the same name keeps its place and the staff copy stays put.
UPDATE catalog_pages s
  JOIN catalog_pages line ON line.caption_save = 'furni_by_line'
  JOIN catalog_pages staff ON staff.caption_save = 'new_release' AND s.parent_id = staff.id
  SET s.parent_id = line.id, s.min_rank = 1
  WHERE s.caption_save IN ('neonpunk_c20', 'gothiccafe_c20', 'sunsetcafe_c20', 'zengarden_c20', 'rainyday_c20',
                           'habbo20_c20', 'darkelegant_c20', 'darkmodern_c20')
    AND NOT EXISTS (SELECT 1 FROM (SELECT parent_id, caption FROM catalog_pages) o WHERE o.parent_id = line.id AND o.caption = s.caption);
-- Furni By Line in alphabetical order, Top Picks first.
UPDATE catalog_pages p JOIN (
    SELECT k.id, ROW_NUMBER() OVER (ORDER BY k.caption_save <> 'top_picks', k.caption) n
    FROM catalog_pages k JOIN catalog_pages line ON line.caption_save = 'furni_by_line' AND k.parent_id = line.id
  ) o ON o.id = p.id
  SET p.order_num = o.n;
UPDATE catalog_pages p SET p.visible = '0' WHERE p.caption_save = 'new_release'
  AND NOT EXISTS (SELECT 1 FROM (SELECT parent_id FROM catalog_pages) k WHERE k.parent_id = p.id)
  AND NOT EXISTS (SELECT 1 FROM catalog_items c WHERE c.page_id = p.id);

-- A line split over two pages of the same name under the same folder
-- becomes one: the newer page's offers move to the older one (unless it
-- already sells them) and the newer page is hidden.
CREATE TEMPORARY TABLE habnut_dup_pages AS
  SELECT p.id dup_id, (SELECT MIN(o.id) FROM catalog_pages o WHERE o.parent_id = p.parent_id AND o.caption = p.caption AND o.visible = '1') keep_id
  FROM catalog_pages p
  WHERE p.visible = '1' AND p.page_layout = 'default_3x3'
    AND EXISTS (SELECT 1 FROM catalog_pages o WHERE o.parent_id = p.parent_id AND o.caption = p.caption AND o.id < p.id AND o.visible = '1')
    AND NOT EXISTS (SELECT 1 FROM catalog_pages k WHERE k.parent_id = p.id);
UPDATE catalog_items c JOIN habnut_dup_pages d ON c.page_id = d.dup_id SET c.page_id = d.keep_id
  WHERE NOT EXISTS (SELECT 1 FROM (SELECT page_id, item_ids FROM catalog_items) k WHERE k.page_id = d.keep_id AND k.item_ids = c.item_ids);
UPDATE catalog_pages p JOIN habnut_dup_pages d ON d.dup_id = p.id SET p.visible = '0';
DROP TEMPORARY TABLE habnut_dup_pages;
