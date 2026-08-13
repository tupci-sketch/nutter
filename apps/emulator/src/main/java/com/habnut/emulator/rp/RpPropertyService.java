package com.habnut.emulator.rp;

import com.habnut.emulator.db.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RpPropertyService {

    public record Property(long id, String name, String type, int price, Integer rentPerWeek,
                           Long ownerCharId, long roomId, String address, boolean forSale) {}

    private final DatabaseManager db;
    private final RpCharacterService charService;

    public RpPropertyService(DatabaseManager db, RpCharacterService charService) {
        this.db = db;
        this.charService = charService;
    }

    public List<Property> listForSale() throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, name, type, price, rent_per_week, owner_character_id," +
                 " room_id, address, for_sale FROM habnut_rp_properties" +
                 " WHERE for_sale=1 ORDER BY price")) {
            return mapProps(ps);
        }
    }

    public Optional<Property> findById(long propId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, name, type, price, rent_per_week, owner_character_id," +
                 " room_id, address, for_sale FROM habnut_rp_properties WHERE id=?")) {
            ps.setLong(1, propId);
            List<Property> list = mapProps(ps);
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        }
    }

    public String buy(long propId, long charId) throws SQLException {
        Optional<Property> opt = findById(propId);
        if (opt.isEmpty()) return "not_found";
        Property p = opt.get();
        if (!p.forSale()) return "not_for_sale";

        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                boolean paid = charService.adjustCash(conn, charId, -p.price());
                if (!paid) {
                    conn.rollback();
                    return "insufficient_cash";
                }
                try (PreparedStatement ps = conn.prepareStatement(
                         "UPDATE habnut_rp_properties SET owner_character_id=?, for_sale=0" +
                         " WHERE id=? AND for_sale=1")) {
                    ps.setLong(1, charId);
                    ps.setLong(2, propId);
                    if (ps.executeUpdate() == 0) {
                        conn.rollback();
                        return "not_for_sale";
                    }
                }
                conn.commit();
                return null;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private List<Property> mapProps(PreparedStatement ps) throws SQLException {
        List<Property> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Object oid = rs.getObject("owner_character_id");
                Object rp  = rs.getObject("rent_per_week");
                list.add(new Property(rs.getLong("id"), rs.getString("name"),
                    rs.getString("type"), rs.getInt("price"),
                    rp != null ? ((Number) rp).intValue() : null,
                    oid != null ? ((Number) oid).longValue() : null,
                    rs.getLong("room_id"), rs.getString("address"),
                    rs.getBoolean("for_sale")));
            }
        }
        return list;
    }
}
