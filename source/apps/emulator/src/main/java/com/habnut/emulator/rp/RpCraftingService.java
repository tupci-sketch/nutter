package com.habnut.emulator.rp;

import com.habnut.emulator.db.DatabaseManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RpCraftingService {

    private static final Logger log = LoggerFactory.getLogger(RpCraftingService.class);

    public record Recipe(long id, String name, String ingredientsJson,
                         Long resultItemId, int levelRequired, String factionRequired) {}

    private final DatabaseManager db;
    private final ObjectMapper mapper;

    public RpCraftingService(DatabaseManager db) {
        this.db = db;
        this.mapper = new ObjectMapper();
    }

    public List<Recipe> listRecipes() throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, name, ingredients_json, result_item_id, level_required," +
                 " faction_required FROM habnut_rp_crafting_recipes ORDER BY id")) {
            return mapRecipes(ps);
        }
    }

    public Optional<Recipe> findById(long recipeId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, name, ingredients_json, result_item_id, level_required," +
                 " faction_required FROM habnut_rp_crafting_recipes WHERE id=?")) {
            ps.setLong(1, recipeId);
            List<Recipe> list = mapRecipes(ps);
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        }
    }

    public long craft(long charId, long recipeId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO habnut_rp_crafted_items (character_id, recipe_id) VALUES (?,?)",
                 Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, charId);
            ps.setLong(2, recipeId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private List<Recipe> mapRecipes(PreparedStatement ps) throws SQLException {
        List<Recipe> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Object rid = rs.getObject("result_item_id");
                list.add(new Recipe(rs.getLong("id"), rs.getString("name"),
                    rs.getString("ingredients_json"),
                    rid != null ? ((Number) rid).longValue() : null,
                    rs.getInt("level_required"), rs.getString("faction_required")));
            }
        }
        return list;
    }
}
