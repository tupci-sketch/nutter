package com.habnut.emulator.garden;

import com.habnut.emulator.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class GardenService {

    private static final Logger log = LoggerFactory.getLogger(GardenService.class);
    private static final int NUT_POINTS_PER_YIELD = 5;

    public enum PlotStage { seed, sprout, growing, mature, ready, withered }

    public record PlantDef(int id, String type, String name, long growthMs,
                           long wateringIntervalMs, int harvestYield, String bonusSeason) {}
    public record Plot(long id, Long ownerUserId, String plantType, String stage,
                       String plantedAt, String wateredAt, String readyAt,
                       String witheredAt, int harvestYield) {}
    public record Goal(long id, String description, String plantType, int targetCount,
                       int currentCount, String rewardDescription, String expiresAt,
                       boolean completed) {}
    public record HarvestResult(boolean ok, String reason, int yield, int nutPointsGranted,
                                boolean goalContributed) {}

    private final DatabaseManager db;

    public GardenService(DatabaseManager db) {
        this.db = db;
    }

    public List<PlantDef> listPlants() throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, type, name, growth_time_ms, watering_interval_ms," +
                 " harvest_yield, seasonal_bonus_season FROM habnut_garden_plants" +
                 " ORDER BY id")) {
            return mapPlantDefs(ps);
        }
    }

    public List<Plot> listPlots() throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, owner_user_id, plant_type, stage, planted_at, watered_at," +
                 " ready_at, withered_at, harvest_yield FROM habnut_garden_plots ORDER BY id")) {
            return mapPlots(ps);
        }
    }

    public List<Plot> getPlotsByOwner(long userId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, owner_user_id, plant_type, stage, planted_at, watered_at," +
                 " ready_at, withered_at, harvest_yield FROM habnut_garden_plots" +
                 " WHERE owner_user_id=? ORDER BY id")) {
            ps.setLong(1, userId);
            return mapPlots(ps);
        }
    }

    public Optional<PlantDef> findPlant(String type) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, type, name, growth_time_ms, watering_interval_ms," +
                 " harvest_yield, seasonal_bonus_season FROM habnut_garden_plants" +
                 " WHERE type=?")) {
            ps.setString(1, type);
            List<PlantDef> list = mapPlantDefs(ps);
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        }
    }

    public String plant(long plotId, long userId, String plantType) throws SQLException {
        try (Connection conn = db.getConnection()) {
            Optional<PlantDef> plantOpt = findPlant(plantType);
            if (plantOpt.isEmpty()) return "unknown_plant";

            try (PreparedStatement ps = conn.prepareStatement(
                     "UPDATE habnut_garden_plots SET owner_user_id=?, plant_type=?," +
                     " stage='seed', planted_at=NOW(), watered_at=NOW()," +
                     " ready_at=DATE_ADD(NOW(), INTERVAL ? SECOND)," +
                     " withered_at=DATE_ADD(NOW(), INTERVAL ? SECOND)," +
                     " harvest_yield=?" +
                     " WHERE id=? AND owner_user_id=? AND (stage IS NULL OR stage='withered')")) {
                PlantDef plant = plantOpt.get();
                ps.setLong(1, userId);
                ps.setString(2, plantType);
                ps.setLong(3, plant.growthMs() / 1000);
                ps.setLong(4, (plant.growthMs() + plant.wateringIntervalMs()) / 1000);
                ps.setInt(5, plant.harvestYield());
                ps.setLong(6, plotId);
                ps.setLong(7, userId);
                if (ps.executeUpdate() == 0) return "plot_unavailable";
            }
            return null;
        }
    }

    public String water(long plotId, long userId) throws SQLException {
        try (Connection conn = db.getConnection()) {
            Optional<PlantDef> plantOpt = getPlotPlant(conn, plotId);
            if (plantOpt.isEmpty()) return "no_plant";

            PlantDef plant = plantOpt.get();
            try (PreparedStatement ps = conn.prepareStatement(
                     "UPDATE habnut_garden_plots SET watered_at=NOW()," +
                     " withered_at=DATE_ADD(NOW(), INTERVAL ? SECOND)" +
                     " WHERE id=? AND owner_user_id=? AND stage NOT IN ('withered','ready')")) {
                ps.setLong(1, plant.wateringIntervalMs() / 1000);
                ps.setLong(2, plotId);
                ps.setLong(3, userId);
                return ps.executeUpdate() > 0 ? null : "cannot_water";
            }
        }
    }

    public HarvestResult harvest(long plotId, long userId) throws SQLException {
        try (Connection conn = db.getConnection()) {
            Plot plot = findPlot(conn, plotId);
            if (plot == null || !"ready".equals(plot.stage())) {
                return new HarvestResult(false, "not_ready", 0, 0, false);
            }
            if (plot.ownerUserId() == null || plot.ownerUserId() != userId) {
                return new HarvestResult(false, "permission_denied", 0, 0, false);
            }

            Optional<PlantDef> plantOpt = findPlant(plot.plantType());
            int baseYield = plantOpt.map(PlantDef::harvestYield).orElse(plot.harvestYield());
            int finalYield = applySeasonBonus(baseYield, plantOpt.orElse(null), conn);

            try (PreparedStatement ps = conn.prepareStatement(
                     "UPDATE habnut_garden_plots SET plant_type=NULL, stage=NULL, planted_at=NULL," +
                     " watered_at=NULL, ready_at=NULL, withered_at=NULL, harvest_yield=0" +
                     " WHERE id=?")) {
                ps.setLong(1, plotId);
                ps.executeUpdate();
            }

            int nutPoints = finalYield * NUT_POINTS_PER_YIELD;
            logHarvest(conn, userId, plotId, plot.plantType(), finalYield, nutPoints);

            boolean goalContrib = contributeToGoal(conn, userId, plot.plantType(), finalYield);

            return new HarvestResult(true, null, finalYield, nutPoints, goalContrib);
        }
    }

    public List<Goal> listActiveGoals() throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, description, plant_type, target_count, current_count," +
                 " reward_description, expires_at, (completed_at IS NOT NULL) completed" +
                 " FROM habnut_garden_goals WHERE expires_at > NOW() ORDER BY id")) {
            return mapGoals(ps);
        }
    }

    public void tickWithering() {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_garden_plots SET stage='withered'" +
                 " WHERE withered_at IS NOT NULL AND withered_at < NOW()" +
                 " AND stage NOT IN ('withered','ready')")) {
            int count = ps.executeUpdate();
            if (count > 0) log.debug("Withered {} plot(s)", count);
        } catch (SQLException e) {
            log.error("Wither tick failed", e);
        }
    }

    public void tickGrowth() {
        try (Connection conn = db.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                     "UPDATE habnut_garden_plots SET stage='ready'" +
                     " WHERE ready_at IS NOT NULL AND ready_at <= NOW()" +
                     " AND stage NOT IN ('ready','withered')")) {
                ps.executeUpdate();
            }
            updateGrowthStages(conn);
        } catch (SQLException e) {
            log.error("Growth tick failed", e);
        }
    }

    private void updateGrowthStages(Connection conn) throws SQLException {
        String sql = """
            UPDATE habnut_garden_plots SET stage = CASE
              WHEN TIMESTAMPDIFF(SECOND, planted_at, NOW()) < (growth_time_ms/4000) THEN 'seed'
              WHEN TIMESTAMPDIFF(SECOND, planted_at, NOW()) < (growth_time_ms/2000) THEN 'sprout'
              WHEN TIMESTAMPDIFF(SECOND, planted_at, NOW()) < (growth_time_ms*3/4000) THEN 'growing'
              ELSE 'mature' END
            JOIN habnut_garden_plants p ON p.type = plant_type
            WHERE stage IN ('seed','sprout','growing','mature')
            AND ready_at > NOW()
            """;
        try (PreparedStatement ps = conn.prepareStatement(
                 "UPDATE habnut_garden_plots gp" +
                 " JOIN habnut_garden_plants p ON p.type = gp.plant_type" +
                 " SET gp.stage = CASE" +
                 "   WHEN TIMESTAMPDIFF(SECOND, gp.planted_at, NOW()) < (p.growth_time_ms/4000) THEN 'seed'" +
                 "   WHEN TIMESTAMPDIFF(SECOND, gp.planted_at, NOW()) < (p.growth_time_ms/2000) THEN 'sprout'" +
                 "   WHEN TIMESTAMPDIFF(SECOND, gp.planted_at, NOW()) < (p.growth_time_ms*3/4000) THEN 'growing'" +
                 "   ELSE 'mature' END" +
                 " WHERE gp.stage IN ('seed','sprout','growing','mature') AND gp.ready_at > NOW()")) {
            ps.executeUpdate();
        }
    }

    private int applySeasonBonus(int baseYield, PlantDef plant, Connection conn) throws SQLException {
        if (plant == null || plant.bonusSeason() == null) return baseYield;
        try (PreparedStatement ps = conn.prepareStatement(
                 "SELECT season FROM habnut_garden_seasons WHERE active=1 LIMIT 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && plant.bonusSeason().equals(rs.getString("season"))) {
                    return baseYield * 2;
                }
            }
        }
        return baseYield;
    }

    private boolean contributeToGoal(Connection conn, long userId, String plantType,
                                      int amount) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, target_count, current_count FROM habnut_garden_goals" +
                 " WHERE (plant_type IS NULL OR plant_type=?) AND completed_at IS NULL" +
                 " AND expires_at > NOW() ORDER BY id LIMIT 1")) {
            ps.setString(1, plantType);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                long goalId      = rs.getLong("id");
                int targetCount  = rs.getInt("target_count");
                int currentCount = rs.getInt("current_count");
                int newCount     = currentCount + amount;

                try (PreparedStatement upd = conn.prepareStatement(
                         "UPDATE habnut_garden_goals SET current_count=?" +
                         (newCount >= targetCount ? ", completed_at=NOW()" : "") +
                         " WHERE id=?")) {
                    upd.setInt(1, newCount); upd.setLong(2, goalId);
                    upd.executeUpdate();
                }
                try (PreparedStatement contrib = conn.prepareStatement(
                         "INSERT INTO habnut_garden_goal_contributions (goal_id, user_id, contribution)" +
                         " VALUES (?,?,?) ON DUPLICATE KEY UPDATE contribution=contribution+?")) {
                    contrib.setLong(1, goalId); contrib.setLong(2, userId);
                    contrib.setInt(3, amount); contrib.setInt(4, amount);
                    contrib.executeUpdate();
                }
                return true;
            }
        }
    }

    private void logHarvest(Connection conn, long userId, long plotId, String plantType,
                             int yield, int nutPoints) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO habnut_garden_harvest_log" +
                 " (user_id, plot_id, plant_type, yield, nut_points_granted)" +
                 " VALUES (?,?,?,?,?)")) {
            ps.setLong(1, userId); ps.setLong(2, plotId); ps.setString(3, plantType);
            ps.setInt(4, yield); ps.setInt(5, nutPoints);
            ps.executeUpdate();
        }
    }

    private Plot findPlot(Connection conn, long plotId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, owner_user_id, plant_type, stage, planted_at, watered_at," +
                 " ready_at, withered_at, harvest_yield FROM habnut_garden_plots WHERE id=?")) {
            ps.setLong(1, plotId);
            List<Plot> list = mapPlots(ps);
            return list.isEmpty() ? null : list.get(0);
        }
    }

    private Optional<PlantDef> getPlotPlant(Connection conn, long plotId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                 "SELECT p.id, p.type, p.name, p.growth_time_ms, p.watering_interval_ms," +
                 " p.harvest_yield, p.seasonal_bonus_season" +
                 " FROM habnut_garden_plots gp" +
                 " JOIN habnut_garden_plants p ON p.type = gp.plant_type" +
                 " WHERE gp.id=?")) {
            ps.setLong(1, plotId);
            List<PlantDef> list = mapPlantDefs(ps);
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        }
    }

    private List<PlantDef> mapPlantDefs(PreparedStatement ps) throws SQLException {
        List<PlantDef> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new PlantDef(rs.getInt("id"), rs.getString("type"),
                    rs.getString("name"), rs.getLong("growth_time_ms"),
                    rs.getLong("watering_interval_ms"), rs.getInt("harvest_yield"),
                    rs.getString("seasonal_bonus_season")));
            }
        }
        return list;
    }

    private List<Plot> mapPlots(PreparedStatement ps) throws SQLException {
        List<Plot> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Object ownerObj = rs.getObject("owner_user_id");
                list.add(new Plot(rs.getLong("id"),
                    ownerObj != null ? ((Number) ownerObj).longValue() : null,
                    rs.getString("plant_type"), rs.getString("stage"),
                    rs.getString("planted_at"), rs.getString("watered_at"),
                    rs.getString("ready_at"), rs.getString("withered_at"),
                    rs.getInt("harvest_yield")));
            }
        }
        return list;
    }

    private List<Goal> mapGoals(PreparedStatement ps) throws SQLException {
        List<Goal> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Goal(rs.getLong("id"), rs.getString("description"),
                    rs.getString("plant_type"), rs.getInt("target_count"),
                    rs.getInt("current_count"), rs.getString("reward_description"),
                    rs.getString("expires_at"), rs.getBoolean("completed")));
            }
        }
        return list;
    }
}
