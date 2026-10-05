package cn.starfallplain.sfpmain.punish;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 处罚系统的数据访问层（players / punishments / punishment_logs 三张表）。
 * <p>
 * 数据库不可用时静默降级：查询返回 null / 空表，写入返回 false，不向调用方抛异常。
 * 所有过期与否的判定（懒判定）由调用方（{@link PunishManager}）负责，这里只管存取。
 */
public final class PunishStore {

    private final JavaPlugin plugin;
    private final PunishDatabase database;

    public PunishStore(JavaPlugin plugin, PunishDatabase database) {
        this.plugin = plugin;
        this.database = database;
    }

    public PunishDatabase getDatabase() {
        return database;
    }

    // ==================== players（UUID ↔ 玩家名）====================

    /** 记录 / 更新玩家名映射（玩家进服时调用） */
    public boolean upsertPlayer(UUID uuid, String name) {
        if (uuid == null || name == null || !database.isAvailable()) return false;
        String sql = """
                INSERT INTO players (uuid, name, last_seen) VALUES (?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET name = excluded.name, last_seen = excluded.last_seen
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
            database.commit();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().warning("记录玩家名失败：" + e.getMessage());
            return false;
        }
    }

    /** 按玩家名查 UUID（忽略大小写）；查不到返回 null */
    public UUID findUuidByName(String name) {
        if (name == null || !database.isAvailable()) return null;
        String sql = "SELECT uuid FROM players WHERE name = ? COLLATE NOCASE LIMIT 1";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return parseUuid(rs.getString("uuid"));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("按名字查玩家 UUID 失败：" + e.getMessage());
        }
        return null;
    }

    /** 按玩家名取最近记录的名字原样（大小写以最后进服为准）；查不到返回 null */
    public String findNameByName(String name) {
        if (name == null || !database.isAvailable()) return null;
        String sql = "SELECT name FROM players WHERE name = ? COLLATE NOCASE LIMIT 1";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("name");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("按名字查玩家记录失败：" + e.getMessage());
        }
        return null;
    }

    /** 全部历史玩家名（补全用），按名字排序 */
    public List<String> listAllNames() {
        List<String> names = new ArrayList<>();
        if (!database.isAvailable()) return names;
        String sql = "SELECT name FROM players ORDER BY name COLLATE NOCASE";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) names.add(rs.getString("name"));
        } catch (SQLException e) {
            plugin.getLogger().warning("列出历史玩家失败：" + e.getMessage());
        }
        return names;
    }

    // ==================== punishments（当前生效处罚）====================

    /** 写入 / 覆盖一条处罚（同一玩家同一类型只保留一条） */
    public boolean savePunishment(Punishment p) {
        if (p == null || !database.isAvailable()) return false;
        // 先删掉该玩家同类型的旧处罚，避免出现多条生效记录
        deleteActive(p.playerUuid(), p.type());
        String sql = """
                INSERT INTO punishments
                    (punishment_id, player_uuid, player_name, type, reason, operator, created_at, expire_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, p.id());
            ps.setString(2, p.playerUuid().toString());
            ps.setString(3, p.playerName());
            ps.setString(4, p.type().key());
            ps.setString(5, p.reason());
            ps.setString(6, p.operator());
            ps.setLong(7, p.createdAt());
            ps.setLong(8, p.expireAt());
            ps.executeUpdate();
            database.commit();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().warning("保存处罚失败：" + e.getMessage());
            return false;
        }
    }

    /** 查某玩家某类型的生效处罚；没有返回 null */
    public Punishment getActive(UUID uuid, PunishmentType type) {
        if (uuid == null || type == null || !database.isAvailable()) return null;
        String sql = "SELECT * FROM punishments WHERE player_uuid = ? AND type = ? LIMIT 1";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, type.key());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return readPunishment(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("查询处罚失败：" + e.getMessage());
        }
        return null;
    }

    /** 查某玩家全部生效处罚（BAN / MUTE） */
    public List<Punishment> getActiveAll(UUID uuid) {
        List<Punishment> result = new ArrayList<>();
        if (uuid == null || !database.isAvailable()) return result;
        String sql = "SELECT * FROM punishments WHERE player_uuid = ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(readPunishment(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("查询玩家处罚失败：" + e.getMessage());
        }
        return result;
    }

    /** 按处罚 ID 查生效处罚；没有返回 null */
    public Punishment getById(String punishmentId) {
        if (punishmentId == null || !database.isAvailable()) return null;
        String sql = "SELECT * FROM punishments WHERE punishment_id = ? LIMIT 1";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, punishmentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return readPunishment(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("按 ID 查询处罚失败：" + e.getMessage());
        }
        return null;
    }

    /** 处罚 ID 是否已存在（生成时查重用） */
    public boolean idExists(String punishmentId) {
        return getById(punishmentId) != null;
    }

    /** 删除某玩家某类型的生效处罚，返回是否确实删了 */
    public boolean deleteActive(UUID uuid, PunishmentType type) {
        if (uuid == null || type == null || !database.isAvailable()) return false;
        String sql = "DELETE FROM punishments WHERE player_uuid = ? AND type = ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, type.key());
            int affected = ps.executeUpdate();
            database.commit();
            return affected > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("删除处罚失败：" + e.getMessage());
            return false;
        }
    }

    /** 全部生效处罚（用于 /sfpcheck 或列表） */
    public List<Punishment> listActive() {
        List<Punishment> result = new ArrayList<>();
        if (!database.isAvailable()) return result;
        String sql = "SELECT * FROM punishments ORDER BY created_at DESC";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(readPunishment(rs));
        } catch (SQLException e) {
            plugin.getLogger().warning("列出处罚失败：" + e.getMessage());
        }
        return result;
    }

    /** 全部生效处罚中的玩家名（unban/unmute 补全用） */
    public List<String> listActiveNames(PunishmentType type) {
        List<String> names = new ArrayList<>();
        if (!database.isAvailable()) return names;
        String sql = type == null
                ? "SELECT player_name FROM punishments ORDER BY player_name COLLATE NOCASE"
                : "SELECT player_name FROM punishments WHERE type = ? ORDER BY player_name COLLATE NOCASE";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            if (type != null) ps.setString(1, type.key());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) names.add(rs.getString("player_name"));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("列出处罚玩家名失败：" + e.getMessage());
        }
        return names;
    }

    /** 删除全部已过期（expire_at > 0 且 < now）的生效处罚，返回删除条数。启动时清理用 */
    public int purgeExpired(long now) {
        if (!database.isAvailable()) return 0;
        String sql = "DELETE FROM punishments WHERE expire_at > 0 AND expire_at < ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, now);
            int affected = ps.executeUpdate();
            database.commit();
            return affected;
        } catch (SQLException e) {
            plugin.getLogger().warning("清理过期处罚失败：" + e.getMessage());
            return 0;
        }
    }

    // ==================== punishment_logs（历史）====================

    /** 追加一条日志，返回是否成功 */
    public boolean insertLog(PunishLog log) {
        if (log == null || !database.isAvailable()) return false;
        String sql = """
                INSERT INTO punishment_logs
                    (punishment_id, player_uuid, player_name, type, reason, operator,
                     created_at, expire_at, action, action_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, log.punishmentId());
            ps.setString(2, log.playerUuid().toString());
            ps.setString(3, log.playerName());
            ps.setString(4, log.type().key());
            ps.setString(5, log.reason());
            ps.setString(6, log.operator());
            ps.setLong(7, log.createdAt());
            ps.setLong(8, log.expireAt());
            ps.setString(9, log.action().name());
            ps.setLong(10, log.actionAt());
            ps.executeUpdate();
            database.commit();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().warning("写入处罚日志失败：" + e.getMessage());
            return false;
        }
    }

    /** 某玩家的全部处罚历史（按处罚时间倒序） */
    public List<PunishLog> listLogsByPlayer(UUID uuid) {
        List<PunishLog> result = new ArrayList<>();
        if (uuid == null || !database.isAvailable()) return result;
        String sql = "SELECT * FROM punishment_logs WHERE player_uuid = ? ORDER BY created_at DESC";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(readLog(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("查询处罚历史失败：" + e.getMessage());
        }
        return result;
    }

    /** 某处罚 ID 的全部日志（施加 + 解除） */
    public List<PunishLog> listLogsByPunishmentId(String punishmentId) {
        List<PunishLog> result = new ArrayList<>();
        if (punishmentId == null || !database.isAvailable()) return result;
        String sql = "SELECT * FROM punishment_logs WHERE punishment_id = ? ORDER BY action_at";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, punishmentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(readLog(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("按 ID 查询处罚历史失败：" + e.getMessage());
        }
        return result;
    }

    // ==================== 行读取 ====================

    private Punishment readPunishment(ResultSet rs) throws SQLException {
        return new Punishment(
                rs.getString("punishment_id"),
                parseUuid(rs.getString("player_uuid")),
                rs.getString("player_name"),
                PunishmentType.fromKey(rs.getString("type")),
                rs.getString("reason"),
                rs.getString("operator"),
                rs.getLong("created_at"),
                rs.getLong("expire_at"));
    }

    private PunishLog readLog(ResultSet rs) throws SQLException {
        return new PunishLog(
                rs.getLong("log_id"),
                rs.getString("punishment_id"),
                parseUuid(rs.getString("player_uuid")),
                rs.getString("player_name"),
                PunishmentType.fromKey(rs.getString("type")),
                rs.getString("reason"),
                rs.getString("operator"),
                rs.getLong("created_at"),
                rs.getLong("expire_at"),
                PunishAction.fromKey(rs.getString("action")),
                rs.getLong("action_at"));
    }

    private static UUID parseUuid(String value) {
        if (value == null) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
