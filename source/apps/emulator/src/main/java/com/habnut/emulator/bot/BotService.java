package com.habnut.emulator.bot;

import com.habnut.emulator.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

public final class BotService {

    private static final Logger log = LoggerFactory.getLogger(BotService.class);

    public enum ChatMode { NORMAL, RANDOM, SEQUENTIAL, REACTION }
    public enum WalkMode { STAND, RANDOM_WALK, FOLLOW_OWNER, PATROL }

    public record Bot(
        long id, long ownerId, String name, String figure, String motto,
        ChatMode chatMode, WalkMode walkMode, String gender,
        Long currentRoomId, int posX, int posY
    ) {}

    public record PlaceResult(boolean ok, String reason, Bot bot) {}

    private final DatabaseManager db;

    public BotService(DatabaseManager db) {
        this.db = db;
    }

    public List<Bot> getOwnerBots(long userId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, owner_id, name, figure, motto, chat_mode, walk_mode, gender, " +
                 "current_room_id, pos_x, pos_y FROM habnut_bots WHERE owner_id=? ORDER BY name")) {
            ps.setLong(1, userId);
            return collectBots(ps);
        }
    }

    public Optional<Bot> getBot(long botId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, owner_id, name, figure, motto, chat_mode, walk_mode, gender, " +
                 "current_room_id, pos_x, pos_y FROM habnut_bots WHERE id=?")) {
            ps.setLong(1, botId);
            List<Bot> list = collectBots(ps);
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        }
    }

    public PlaceResult place(long botId, long ownerId, long roomId, int x, int y) throws SQLException {
        Optional<Bot> opt = getBot(botId);
        if (opt.isEmpty()) return new PlaceResult(false, "not_found", null);
        Bot bot = opt.get();
        if (bot.ownerId() != ownerId) return new PlaceResult(false, "permission_denied", null);
        if (bot.currentRoomId() != null) return new PlaceResult(false, "already_placed", null);

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_bots SET current_room_id=?, pos_x=?, pos_y=? WHERE id=?")) {
            ps.setLong(1, roomId); ps.setInt(2, x); ps.setInt(3, y); ps.setLong(4, botId);
            ps.executeUpdate();
        }
        Bot placed = new Bot(bot.id(), bot.ownerId(), bot.name(), bot.figure(), bot.motto(),
            bot.chatMode(), bot.walkMode(), bot.gender(), roomId, x, y);
        return new PlaceResult(true, null, placed);
    }

    public boolean pickup(long botId, long ownerId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_bots SET current_room_id=NULL, pos_x=0, pos_y=0 WHERE id=? AND owner_id=?")) {
            ps.setLong(1, botId); ps.setLong(2, ownerId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean updateSettings(long botId, long ownerId, String name, String motto,
                                  String chatModeStr, String walkModeStr) throws SQLException {
        ChatMode chatMode = parseEnum(ChatMode.class, chatModeStr, ChatMode.NORMAL);
        WalkMode walkMode = parseEnum(WalkMode.class, walkModeStr, WalkMode.STAND);

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_bots SET name=?, motto=?, chat_mode=?, walk_mode=? WHERE id=? AND owner_id=?")) {
            ps.setString(1, name); ps.setString(2, motto);
            ps.setString(3, chatMode.name()); ps.setString(4, walkMode.name());
            ps.setLong(5, botId); ps.setLong(6, ownerId);
            return ps.executeUpdate() > 0;
        }
    }

    public String handleChat(Bot bot, String trigger) {
        return switch (bot.chatMode()) {
            case NORMAL     -> bot.motto().isEmpty() ? "..." : bot.motto();
            case REACTION   -> "I heard: " + trigger;
            case RANDOM     -> List.of("Hello!", "How are you?", "Welcome!", "Nice to see you!",
                                       "Having fun?").get(new Random().nextInt(5));
            case SEQUENTIAL -> bot.motto();
        };
    }

    private List<Bot> collectBots(PreparedStatement ps) throws SQLException {
        List<Bot> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Bot(
                    rs.getLong("id"), rs.getLong("owner_id"), rs.getString("name"),
                    rs.getString("figure"), rs.getString("motto"),
                    parseEnum(ChatMode.class, rs.getString("chat_mode"), ChatMode.NORMAL),
                    parseEnum(WalkMode.class, rs.getString("walk_mode"), WalkMode.STAND),
                    rs.getString("gender"),
                    rs.getObject("current_room_id", Long.class),
                    rs.getInt("pos_x"), rs.getInt("pos_y")));
            }
        }
        return list;
    }

    private <T extends Enum<T>> T parseEnum(Class<T> cls, String value, T fallback) {
        try { return Enum.valueOf(cls, value.toUpperCase()); }
        catch (Exception e) { return fallback; }
    }
}
