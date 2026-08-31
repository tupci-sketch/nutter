package com.habnut.emulator.camera;

import com.habnut.emulator.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

public final class CameraService {

    private static final Logger log = LoggerFactory.getLogger(CameraService.class);

    private static final int PURCHASE_COST_DIAMONDS = 1;
    private static final int MAX_PHOTOS_PER_USER    = 50;

    public record Photo(
        long id, long userId, long roomId, String imageToken,
        boolean purchased, long takenAt
    ) {}

    public enum PhotoResult { OK, LIMIT_REACHED, NOT_FOUND, ALREADY_PURCHASED }

    private final DatabaseManager db;

    public CameraService(DatabaseManager db) {
        this.db = db;
    }

    /**
     * Records a photo taken by a user in a room.
     * Returns the generated photo record with a unique token.
     */
    public Optional<Photo> takePhoto(long userId, long roomId, String previewData) throws SQLException {
        try (Connection conn = db.getConnection()) {
            // Count existing photos for this user
            try (PreparedStatement cnt = conn.prepareStatement(
                "SELECT COUNT(*) FROM habnut_photos WHERE user_id=?")) {
                cnt.setLong(1, userId);
                try (ResultSet rs = cnt.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) >= MAX_PHOTOS_PER_USER) return Optional.empty();
                }
            }

            String token = UUID.randomUUID().toString().replace("-", "");
            try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO habnut_photos (user_id, room_id, image_token, preview_data, purchased, taken_at) " +
                "VALUES (?,?,?,?,0,NOW())",
                Statement.RETURN_GENERATED_KEYS)) {
                ins.setLong(1, userId); ins.setLong(2, roomId);
                ins.setString(3, token); ins.setString(4, previewData);
                ins.executeUpdate();
                try (ResultSet keys = ins.getGeneratedKeys()) {
                    if (keys.next()) {
                        long id = keys.getLong(1);
                        return Optional.of(new Photo(id, userId, roomId, token, false,
                            System.currentTimeMillis() / 1000));
                    }
                }
            }
        }
        return Optional.empty();
    }

    public PhotoResult purchasePhoto(long photoId, long userId) throws SQLException {
        try (Connection conn = db.getConnection()) {
            try (PreparedStatement sel = conn.prepareStatement(
                "SELECT user_id, purchased FROM habnut_photos WHERE id=?")) {
                sel.setLong(1, photoId);
                try (ResultSet rs = sel.executeQuery()) {
                    if (!rs.next()) return PhotoResult.NOT_FOUND;
                    if (rs.getLong("user_id") != userId) return PhotoResult.NOT_FOUND;
                    if (rs.getBoolean("purchased")) return PhotoResult.ALREADY_PURCHASED;
                }
            }
            try (PreparedStatement upd = conn.prepareStatement(
                "UPDATE habnut_photos SET purchased=1 WHERE id=?")) {
                upd.setLong(1, photoId); upd.executeUpdate();
            }
            return PhotoResult.OK;
        }
    }

    public List<Photo> listPhotos(long userId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, user_id, room_id, image_token, purchased, UNIX_TIMESTAMP(taken_at) AS taken_at " +
                 "FROM habnut_photos WHERE user_id=? ORDER BY taken_at DESC LIMIT 50")) {
            ps.setLong(1, userId);
            List<Photo> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Photo(rs.getLong("id"), rs.getLong("user_id"),
                        rs.getLong("room_id"), rs.getString("image_token"),
                        rs.getBoolean("purchased"), rs.getLong("taken_at")));
                }
            }
            return list;
        }
    }

    public boolean deletePhoto(long photoId, long userId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM habnut_photos WHERE id=? AND user_id=?")) {
            ps.setLong(1, photoId); ps.setLong(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public int getPurchaseCostDiamonds() { return PURCHASE_COST_DIAMONDS; }
}
