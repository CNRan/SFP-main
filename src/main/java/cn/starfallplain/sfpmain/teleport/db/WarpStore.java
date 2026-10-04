package cn.starfallplain.sfpmain.teleport.db;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 公共传送点（{@code warps} 表）的数据访问。
 * <p>
 * 传送点全服共享，主键为 {@code warp_name}。
 * 数据库不可用时静默降级：查询返回 null / 空表，写入返回 false，不向调用方抛异常。
 */
public final class WarpStore {

    private final JavaPlugin plugin;
    private final Database database;

    public WarpStore(JavaPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
    }

    /** 创建或覆盖一个公共传送点 */
    public boolean save(String name, StoredLocation loc) {
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

    /** 读取某个公共传送点；不存在返回 null */
    public StoredLocation get(String name) {
        if (!database.isAvailable()) return null;
        String sql = "SELECT world, x, y, z, yaw, pitch FROM warps WHERE warp_name = ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return LocationRow.read(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("读取传送点失败：" + e.getMessage());
        }
        return null;
    }

    /** 删除一个公共传送点，返回是否真的删掉了 */
    public boolean delete(String name) {
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

    /** 列出所有公共传送点名（按名称排序，忽略大小写） */
    public List<String> listNames() {
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
}
