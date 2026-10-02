package cn.starfallplain.CN_Ran.sfp.teleport.db;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 传送数据访问层（DAO）。
 * <p>
 * 提供 home / warp / back 三类数据的增删查，全部走预编译语句（防注入）。
 * 所有方法同步执行；数据库不可用时静默降级（查询返回空、写入忽略），
 * 不抛出异常到主流程。
 */
public final class LocationStore {

    private final JavaPlugin plugin;
    private final Database database;

    public LocationStore(JavaPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
    }

    public boolean isAvailable() {
        return database.isAvailable();
    }

    // ==================== 家（homes） ====================

    /** 设置/覆盖一个家 */
    public boolean saveHome(UUID player, String name, StoredLocation loc) {
        if (loc == null || !database.isAvailable()) return false;
        String sql = """
                INSERT INTO homes (player_uuid, home_name, world, x, y, z, yaw, pitch, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(player_uuid, home_name) DO UPDATE SET
                    world=excluded.world, x=excluded.x, y=excluded.y, z=excluded.z,
                    yaw=excluded.yaw, pitch=excluded.pitch, created_at=excluded.created_at
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, name);
            ps.setString(3, loc.world());
            ps.setDouble(4, loc.x());
            ps.setDouble(5, loc.y());
            ps.setDouble(6, loc.z());
            ps.setFloat(7, loc.yaw());
            ps.setFloat(8, loc.pitch());
            ps.setLong(9, System.currentTimeMillis());
            ps.executeUpdate();
            database.commit();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().warning("保存家失败：" + e.getMessage());
            return false;
        }
    }

    /** 读取指定玩家的某个家 */
    public StoredLocation getHome(UUID player, String name) {
        if (!database.isAvailable()) return null;
        String sql = "SELECT world, x, y, z, yaw, pitch FROM homes WHERE player_uuid = ? AND home_name = ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return readLocation(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("读取家失败：" + e.getMessage());
        }
        return null;
    }

    /** 删除一个家，返回是否真的删掉了 */
    public boolean deleteHome(UUID player, String name) {
        if (!database.isAvailable()) return false;
        String sql = "DELETE FROM homes WHERE player_uuid = ? AND home_name = ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, name);
            int affected = ps.executeUpdate();
            database.commit();
            return affected > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("删除家失败：" + e.getMessage());
            return false;
        }
    }

    /** 列出某玩家的所有家名（按名称排序） */
    public List<String> listHomeNames(UUID player) {
        List<String> names = new ArrayList<>();
        if (!database.isAvailable()) return names;
        String sql = "SELECT home_name FROM homes WHERE player_uuid = ? ORDER BY home_name COLLATE NOCASE";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) names.add(rs.getString("home_name"));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("列出家失败：" + e.getMessage());
        }
        return names;
    }

    /** 某玩家已设置的家数量 */
    public int countHomes(UUID player) {
        if (!database.isAvailable()) return 0;
        String sql = "SELECT COUNT(*) AS c FROM homes WHERE player_uuid = ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("c");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("统计家数量失败：" + e.getMessage());
        }
        return 0;
    }

    // ==================== 传送点（warps） ====================

    /** 设置/覆盖一个公共传送点 */
    public boolean saveWarp(String name, StoredLocation loc) {
        if (loc == null || !database.isAvailable()) return false;
        String sql = """
                INSERT INTO warps (warp_name, world, x, y, z, yaw, pitch, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(warp_name) DO UPDATE SET
                    world=excluded.world, x=excluded.x, y=excluded.y, z=excluded.z,
                    yaw=excluded.yaw, pitch=excluded.pitch, created_at=excluded.created_at
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, loc.world());
            ps.setDouble(3, loc.x());
            ps.setDouble(4, loc.y());
            ps.setDouble(5, loc.z());
            ps.setFloat(6, loc.yaw());
            ps.setFloat(7, loc.pitch());
            ps.setLong(8, System.currentTimeMillis());
            ps.executeUpdate();
            database.commit();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().warning("保存传送点失败：" + e.getMessage());
            return false;
        }
    }

    /** 读取某个公共传送点 */
    public StoredLocation getWarp(String name) {
        if (!database.isAvailable()) return null;
        String sql = "SELECT world, x, y, z, yaw, pitch FROM warps WHERE warp_name = ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return readLocation(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("读取传送点失败：" + e.getMessage());
        }
        return null;
    }

    /** 删除一个公共传送点 */
    public boolean deleteWarp(String name) {
        if (!database.isAvailable()) return false;
        String sql = "DELETE FROM warps WHERE warp_name = ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, name);
            int affected = ps.executeUpdate();
            database.commit();
            return affected > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("删除传送点失败：" + e.getMessage());
            return false;
        }
    }

    /** 列出所有公共传送点名（按名称排序） */
    public List<String> listWarpNames() {
        List<String> names = new ArrayList<>();
        if (!database.isAvailable()) return names;
        String sql = "SELECT warp_name FROM warps ORDER BY warp_name COLLATE NOCASE";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) names.add(rs.getString("warp_name"));
        } catch (SQLException e) {
            plugin.getLogger().warning("列出传送点失败：" + e.getMessage());
        }
        return names;
    }

    // ==================== 返回点（last_locations） ====================

    /** 记录玩家最后位置（/back 使用） */
    public boolean saveLastLocation(UUID player, StoredLocation loc) {
        if (loc == null || !database.isAvailable()) return false;
        String sql = """
                INSERT INTO last_locations (player_uuid, world, x, y, z, yaw, pitch, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(player_uuid) DO UPDATE SET
                    world=excluded.world, x=excluded.x, y=excluded.y, z=excluded.z,
                    yaw=excluded.yaw, pitch=excluded.pitch, updated_at=excluded.updated_at
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, loc.world());
            ps.setDouble(3, loc.x());
            ps.setDouble(4, loc.y());
            ps.setDouble(5, loc.z());
            ps.setFloat(6, loc.yaw());
            ps.setFloat(7, loc.pitch());
            ps.setLong(8, System.currentTimeMillis());
            ps.executeUpdate();
            database.commit();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().warning("保存返回点失败：" + e.getMessage());
            return false;
        }
    }

    /** 读取玩家最后位置 */
    public StoredLocation getLastLocation(UUID player) {
        if (!database.isAvailable()) return null;
        String sql = "SELECT world, x, y, z, yaw, pitch FROM last_locations WHERE player_uuid = ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return readLocation(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("读取返回点失败：" + e.getMessage());
        }
        return null;
    }

    // ==================== 内部工具 ====================

    private StoredLocation readLocation(ResultSet rs) throws SQLException {
        return new StoredLocation(
                rs.getString("world"),
                rs.getDouble("x"),
                rs.getDouble("y"),
                rs.getDouble("z"),
                rs.getFloat("yaw"),
                rs.getFloat("pitch"));
    }
}
