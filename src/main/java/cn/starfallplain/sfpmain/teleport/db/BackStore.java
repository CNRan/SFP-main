package cn.starfallplain.sfpmain.teleport.db;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * 返回点（{@code last_locations} 表）的数据访问：每位玩家一行，覆盖式写入。
 * <p>
 * 因此 {@code /back} 只回退一层，不是历史栈 —— 若以后要做多层回退，
 * 需要把这张表改成「玩家 + 序号」的形式（那属于表结构变更，见 {@link Database} 的迁移说明）。
 * <p>
 * 数据库不可用时静默降级：查询返回 null、写入返回 false，不向调用方抛异常。
 */
public final class BackStore {

    private final JavaPlugin plugin;
    private final Database database;

    public BackStore(JavaPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
    }

    /** 记录玩家最后位置（覆盖旧值） */
    public boolean save(UUID player, StoredLocation loc) {
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

    /** 读取玩家最后位置；没有记录返回 null */
    public StoredLocation get(UUID player) {
        if (!database.isAvailable()) return null;
        String sql = "SELECT world, x, y, z, yaw, pitch FROM last_locations WHERE player_uuid = ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return LocationRow.read(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("读取返回点失败：" + e.getMessage());
        }
        return null;
    }
}
