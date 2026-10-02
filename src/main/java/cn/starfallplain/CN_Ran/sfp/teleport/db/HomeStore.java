package cn.starfallplain.CN_Ran.sfp.teleport.db;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 个人家（{@code homes} 表）的数据访问。
 * <p>
 * 一位玩家可有多个家，主键为 {@code (player_uuid, home_name)}，所以「按玩家查询」都有索引覆盖。
 * 数据库不可用时静默降级：查询返回 null / 空表，写入返回 false，不向调用方抛异常。
 */
public final class HomeStore {

    private final JavaPlugin plugin;
    private final Database database;

    public HomeStore(JavaPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
    }

    /** 设置或覆盖一个家 */
    public boolean save(UUID player, String name, StoredLocation loc) {
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

    /** 读取指定玩家的某个家；不存在返回 null */
    public StoredLocation get(UUID player, String name) {
        if (!database.isAvailable()) return null;
        String sql = "SELECT world, x, y, z, yaw, pitch FROM homes WHERE player_uuid = ? AND home_name = ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return LocationRow.read(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("读取家失败：" + e.getMessage());
        }
        return null;
    }

    /** 删除一个家，返回是否真的删掉了 */
    public boolean delete(UUID player, String name) {
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

    /** 列出某玩家的所有家名（按名称排序，忽略大小写） */
    public List<String> listNames(UUID player) {
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
    public int count(UUID player) {
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
}
