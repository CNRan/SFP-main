package cn.starfallplain.CN_Ran.sfp.teleport.db;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * 持久化的位置数据（与 Bukkit {@link Location} 互转）。
 * <p>
 * 存世界名而非 World 引用，避免跨重启失效；读取时按名解析，
 * 世界已删除时返回 null（调用方自行提示）。
 */
public record StoredLocation(String world, double x, double y, double z, float yaw, float pitch) {

    /** 从 Bukkit Location 构建（保留朝向） */
    public static StoredLocation of(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        return new StoredLocation(loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
    }

    /**
     * 还原为 Bukkit Location。
     *
     * @return 世界不存在时返回 null
     */
    public Location toLocation() {
        World w = Bukkit.getWorld(world);
        if (w == null) return null;
        return new Location(w, x, y, z, yaw, pitch);
    }

    /** 世界是否仍然存在 */
    public boolean worldExists() {
        return Bukkit.getWorld(world) != null;
    }

    /** 简短的坐标文本（用于消息展示），如 "world 120, 64, -30" */
    public String describe() {
        return world + " " + Math.round(x) + ", " + Math.round(y) + ", " + Math.round(z);
    }
}
