package com.habnut.emulator.pet;

import com.habnut.emulator.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

public final class PetService {

    private static final Logger log = LoggerFactory.getLogger(PetService.class);

    // Stat decay per hour (applied lazily on access)
    private static final int HUNGER_DECAY_PH  = 5;
    private static final int THIRST_DECAY_PH  = 7;
    private static final int HAPPY_DECAY_PH   = 3;
    private static final int MAX_STAT         = 100;
    private static final int FEED_RESTORE     = 30;
    private static final int DRINK_RESTORE    = 30;
    private static final int XP_PER_COMMAND   = 5;

    private final DatabaseManager db;

    public PetService(DatabaseManager db) {
        this.db = db;
    }

    // --- Records ---

    public record Pet(
        long id, long ownerId, String name, String petType, int level, int xp,
        int hunger, int thirst, int happiness, String figureData,
        Long currentRoomId, int posX, int posY, long updatedAt
    ) {}

    public record PlaceResult(boolean ok, String reason, Pet pet) {}
    public record CommandResult(boolean ok, String response, int xpGained) {}

    // --- Queries ---

    public List<Pet> getOwnerPets(long userId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, owner_id, name, pet_type, level, xp, hunger, thirst, happiness, " +
                 "figure_data, current_room_id, pos_x, pos_y, UNIX_TIMESTAMP(updated_at) AS updated_at " +
                 "FROM habnut_pets WHERE owner_id=? ORDER BY name")) {
            ps.setLong(1, userId);
            return collectPets(ps, conn);
        }
    }

    public Optional<Pet> getPet(long petId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, owner_id, name, pet_type, level, xp, hunger, thirst, happiness, " +
                 "figure_data, current_room_id, pos_x, pos_y, UNIX_TIMESTAMP(updated_at) AS updated_at " +
                 "FROM habnut_pets WHERE id=?")) {
            ps.setLong(1, petId);
            List<Pet> list = collectPets(ps, conn);
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        }
    }

    public PlaceResult place(long petId, long ownerId, long roomId, int x, int y) throws SQLException {
        Optional<Pet> opt = getPet(petId);
        if (opt.isEmpty()) return new PlaceResult(false, "not_found", null);
        Pet pet = opt.get();
        if (pet.ownerId() != ownerId) return new PlaceResult(false, "permission_denied", null);
        if (pet.currentRoomId() != null) return new PlaceResult(false, "already_placed", null);

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_pets SET current_room_id=?, pos_x=?, pos_y=?, updated_at=NOW() WHERE id=?")) {
            ps.setLong(1, roomId); ps.setInt(2, x); ps.setInt(3, y); ps.setLong(4, petId);
            ps.executeUpdate();
        }
        Pet placed = new Pet(pet.id(), pet.ownerId(), pet.name(), pet.petType(), pet.level(),
            pet.xp(), pet.hunger(), pet.thirst(), pet.happiness(), pet.figureData(),
            roomId, x, y, System.currentTimeMillis() / 1000);
        return new PlaceResult(true, null, placed);
    }

    public boolean pickup(long petId, long ownerId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_pets SET current_room_id=NULL, pos_x=0, pos_y=0, updated_at=NOW() " +
                 "WHERE id=? AND owner_id=?")) {
            ps.setLong(1, petId); ps.setLong(2, ownerId);
            return ps.executeUpdate() > 0;
        }
    }

    public CommandResult handleCommand(long petId, long userId, String command) throws SQLException {
        Optional<Pet> opt = getPet(petId);
        if (opt.isEmpty()) return new CommandResult(false, "pet_not_found", 0);
        Pet pet = opt.get();
        if (pet.ownerId() != userId) return new CommandResult(false, "permission_denied", 0);

        String response = switch (command.toLowerCase()) {
            case "sit"   -> pet.name() + " sits down!";
            case "stand" -> pet.name() + " stands up!";
            case "sleep" -> pet.name() + " curls up and sleeps.";
            case "beg"   -> pet.name() + " begs cutely!";
            case "jump"  -> pet.name() + " leaps into the air!";
            case "fetch" -> pet.name() + " fetches the ball!";
            case "speak" -> pet.name() + " says woof!";
            case "free"  -> pet.name() + " is free to roam!";
            default      -> pet.name() + " doesn't understand that command yet.";
        };

        int xpGained = grantXp(petId, XP_PER_COMMAND);
        return new CommandResult(true, response, xpGained);
    }

    public Map<String, Integer> feed(long petId, long userId) throws SQLException {
        Optional<Pet> opt = getPet(petId);
        if (opt.isEmpty()) return Map.of();
        Pet pet = opt.get();
        if (pet.ownerId() != userId) return Map.of();

        int newHunger = Math.min(MAX_STAT, pet.hunger() + FEED_RESTORE);
        int newHappy  = Math.min(MAX_STAT, pet.happiness() + 5);
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_pets SET hunger=?, happiness=?, updated_at=NOW() WHERE id=?")) {
            ps.setInt(1, newHunger); ps.setInt(2, newHappy); ps.setLong(3, petId);
            ps.executeUpdate();
        }
        return Map.of("hunger", newHunger, "happiness", newHappy);
    }

    public Map<String, Integer> decayStats(Pet pet) throws SQLException {
        long nowSec   = System.currentTimeMillis() / 1000;
        long elapsed  = nowSec - pet.updatedAt();
        long hoursElapsed = elapsed / 3600;
        if (hoursElapsed < 1) return Map.of();

        int newHunger = Math.max(0, pet.hunger()    - (int)(hoursElapsed * HUNGER_DECAY_PH));
        int newThirst = Math.max(0, pet.thirst()    - (int)(hoursElapsed * THIRST_DECAY_PH));
        int newHappy  = Math.max(0, pet.happiness() - (int)(hoursElapsed * HAPPY_DECAY_PH));

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_pets SET hunger=?, thirst=?, happiness=?, updated_at=NOW() WHERE id=?")) {
            ps.setInt(1, newHunger); ps.setInt(2, newThirst); ps.setInt(3, newHappy);
            ps.setLong(4, pet.id());
            ps.executeUpdate();
        }
        return Map.of("hunger", newHunger, "thirst", newThirst, "happiness", newHappy);
    }

    private int grantXp(long petId, int amount) throws SQLException {
        try (Connection conn = db.getConnection()) {
            int currentXp, currentLevel;
            try (PreparedStatement sel = conn.prepareStatement(
                "SELECT xp, level FROM habnut_pets WHERE id=?")) {
                sel.setLong(1, petId);
                try (ResultSet rs = sel.executeQuery()) {
                    if (!rs.next()) return 0;
                    currentXp    = rs.getInt("xp");
                    currentLevel = rs.getInt("level");
                }
            }
            int newXp = currentXp + amount;
            int newLevel = currentLevel;
            int xpNeeded = levelThreshold(currentLevel);
            boolean leveled = false;
            if (newXp >= xpNeeded && currentLevel < 20) {
                newXp -= xpNeeded;
                newLevel++;
                leveled = true;
            }
            try (PreparedStatement upd = conn.prepareStatement(
                "UPDATE habnut_pets SET xp=?, level=?, updated_at=NOW() WHERE id=?")) {
                upd.setInt(1, newXp); upd.setInt(2, newLevel); upd.setLong(3, petId);
                upd.executeUpdate();
            }
            return leveled ? newLevel : 0;
        }
    }

    private int levelThreshold(int level) {
        return 100 + level * 50;
    }

    private List<Pet> collectPets(PreparedStatement ps, Connection conn) throws SQLException {
        List<Pet> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Pet(
                    rs.getLong("id"), rs.getLong("owner_id"), rs.getString("name"),
                    rs.getString("pet_type"), rs.getInt("level"), rs.getInt("xp"),
                    rs.getInt("hunger"), rs.getInt("thirst"), rs.getInt("happiness"),
                    rs.getString("figure_data"),
                    rs.getObject("current_room_id", Long.class),
                    rs.getInt("pos_x"), rs.getInt("pos_y"), rs.getLong("updated_at")));
            }
        }
        return list;
    }
}
