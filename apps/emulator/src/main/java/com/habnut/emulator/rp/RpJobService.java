package com.habnut.emulator.rp;

import com.habnut.emulator.db.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RpJobService {

    public record Job(long id, String title, String description, Long factionId,
                      int salary, String salaryInterval, int maxOpenings, boolean enabled) {}
    public record Shift(long id, long charId, long jobId, String clockInAt, String clockOutAt,
                        int payAmount, boolean paid) {}

    private final DatabaseManager db;

    public RpJobService(DatabaseManager db) {
        this.db = db;
    }

    public List<Job> listEnabled() throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, title, description, faction_id, salary, salary_interval," +
                 " max_openings, enabled FROM habnut_rp_jobs WHERE enabled=1 ORDER BY id")) {
            return mapJobs(ps);
        }
    }

    public Optional<Job> findById(long jobId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, title, description, faction_id, salary, salary_interval," +
                 " max_openings, enabled FROM habnut_rp_jobs WHERE id=?")) {
            ps.setLong(1, jobId);
            List<Job> list = mapJobs(ps);
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        }
    }

    public boolean applyForJob(long charId, long jobId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO habnut_rp_job_applications (character_id, job_id) VALUES (?,?)")) {
            ps.setLong(1, charId);
            ps.setLong(2, jobId);
            ps.executeUpdate();
            return true;
        }
    }

    public Optional<Shift> getActiveShift(long charId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, character_id, job_id, clock_in_at, clock_out_at, pay_amount, paid_at" +
                 " FROM habnut_rp_shifts WHERE character_id=? AND clock_out_at IS NULL LIMIT 1")) {
            ps.setLong(1, charId);
            return mapShift(ps);
        }
    }

    public Shift clockIn(long charId, long jobId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO habnut_rp_shifts (character_id, job_id) VALUES (?,?)",
                 Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, charId);
            ps.setLong(2, jobId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return getShiftById(conn, keys.getLong(1));
            }
        }
    }

    public Optional<Shift> clockOut(long charId) throws SQLException {
        try (Connection conn = db.getConnection()) {
            Optional<Shift> active = getActiveShift(charId);
            if (active.isEmpty()) return Optional.empty();
            Shift s = active.get();
            Optional<Job> job = findById(s.jobId());
            int pay = job.map(Job::salary).orElse(0);
            try (PreparedStatement ps = conn.prepareStatement(
                     "UPDATE habnut_rp_shifts SET clock_out_at=NOW(), pay_amount=? WHERE id=?")) {
                ps.setInt(1, pay);
                ps.setLong(2, s.id());
                ps.executeUpdate();
            }
            return Optional.of(new Shift(s.id(), s.charId(), s.jobId(), s.clockInAt(),
                "NOW", pay, false));
        }
    }

    private Shift getShiftById(Connection conn, long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, character_id, job_id, clock_in_at, clock_out_at, pay_amount, paid_at" +
                 " FROM habnut_rp_shifts WHERE id=?")) {
            ps.setLong(1, id);
            return mapShift(ps).orElseThrow();
        }
    }

    private Optional<Shift> mapShift(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return Optional.empty();
            return Optional.of(new Shift(rs.getLong("id"), rs.getLong("character_id"),
                rs.getLong("job_id"), rs.getString("clock_in_at"),
                rs.getString("clock_out_at"), rs.getInt("pay_amount"),
                rs.getString("paid_at") != null));
        }
    }

    private List<Job> mapJobs(PreparedStatement ps) throws SQLException {
        List<Job> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Object fid = rs.getObject("faction_id");
                list.add(new Job(rs.getLong("id"), rs.getString("title"),
                    rs.getString("description"),
                    fid != null ? ((Number) fid).longValue() : null,
                    rs.getInt("salary"), rs.getString("salary_interval"),
                    rs.getInt("max_openings"), rs.getBoolean("enabled")));
            }
        }
        return list;
    }
}
