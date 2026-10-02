package cn.starfallplain.CN_Ran.sfp.teleport;

import cn.starfallplain.CN_Ran.sfp.StarfallplainMenu;
import cn.starfallplain.CN_Ran.sfp.config.module.TeleportConfig;
import cn.starfallplain.CN_Ran.sfp.teleport.db.Database;
import cn.starfallplain.CN_Ran.sfp.teleport.db.LocationStore;
import cn.starfallplain.CN_Ran.sfp.teleport.db.StoredLocation;
import cn.starfallplain.CN_Ran.sfp.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 传送核心管理器。
 * <p>
 * 统一负责：
 * <ul>
 *   <li>SQLite 数据层（{@link Database} + {@link LocationStore}）的生命周期</li>
 *   <li>/back 位置记录（死亡、传送前、切换世界、退出）</li>
 *   <li>实际传送（含安全落点、冷却、音效、跨世界判定）</li>
 *   <li>延迟传送（teleport.delay-seconds &gt; 0 时等待并支持移动打断）</li>
 * </ul>
 * 命令与 GUI 都通过本类发起传送，保证行为一致。
 */
public final class TeleportManager {

    private final StarfallplainMenu plugin;
    private final TeleportConfig config;
    private final Database database;
    private final LocationStore store;

    /** 传送冷却：玩家 UUID → 冷却结束时间（毫秒） */
    private final Map<UUID, Long> teleportCooldowns = new HashMap<>();
    /** 设置家冷却：玩家 UUID → 冷却结束时间（毫秒） */
    private final Map<UUID, Long> setHomeCooldowns = new HashMap<>();
    /** 内部传送标记：玩家 UUID → 标记过期时间（毫秒） */
    private final Map<UUID, Long> internalTeleportFlags = new HashMap<>();
    /** 延迟传送队列：玩家 UUID → 待执行的传送任务 */
    private final Map<UUID, PendingTeleport> pendingTeleports = new HashMap<>();

    /**
     * 一笔待执行的延迟传送。
     *
     * @param destination 目标位置（已完成安全落点解析）
     * @param start       发起传送时的位置（用于判定是否移动）
     * @param task        计划任务（打断时需取消）
     * @param worldName   目标世界名（执行前再校验一次世界是否存在）
     */
    private record PendingTeleport(Location destination, Location start,
                                   BukkitTask task, String worldName) {
    }

    public TeleportManager(StarfallplainMenu plugin, TeleportConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.database = new Database(plugin, config.getDbFile(), config.isAutoCommit());
        this.store = new LocationStore(plugin, database);
    }

    public TeleportConfig getConfig() {
        return config;
    }

    public LocationStore getStore() {
        return store;
    }

    /** 数据层是否可用 */
    public boolean isStorageAvailable() {
        return store.isAvailable();
    }

    /** 关闭数据库连接（插件卸载时调用） */
    public void shutdown() {
        // 取消所有等待中的延迟传送
        for (PendingTeleport pending : pendingTeleports.values()) {
            if (pending.task() != null) pending.task().cancel();
        }
        pendingTeleports.clear();
        database.close();
    }

    // ==================== 传送冷却 ====================

    /**
     * 检查冷却，未冷却时返回 0；冷却中返回剩余秒数。
     */
    public long getCooldownRemaining(Player player, int cooldownSeconds) {
        if (cooldownSeconds <= 0) return 0;
        Long until = teleportCooldowns.get(player.getUniqueId());
        if (until == null) return 0;
        long remainMs = until - System.currentTimeMillis();
        if (remainMs <= 0) return 0;
        // 向上取整秒
        return (remainMs + 999) / 1000;
    }

    private void markCooldown(Player player, int cooldownSeconds) {
        if (cooldownSeconds <= 0) return;
        teleportCooldowns.put(player.getUniqueId(),
                System.currentTimeMillis() + cooldownSeconds * 1000L);
    }

    public long getSetHomeCooldownRemaining(Player player) {
        int seconds = config.getHomeSetCooldownSeconds();
        if (seconds <= 0) return 0;
        Long until = setHomeCooldowns.get(player.getUniqueId());
        if (until == null) return 0;
        long remainMs = until - System.currentTimeMillis();
        if (remainMs <= 0) return 0;
        return (remainMs + 999) / 1000;
    }

    public void markSetHomeCooldown(Player player) {
        int seconds = config.getHomeSetCooldownSeconds();
        if (seconds <= 0) return;
        setHomeCooldowns.put(player.getUniqueId(),
                System.currentTimeMillis() + seconds * 1000L);
    }

    // ==================== 记录位置（/back） ====================

    /** 记录玩家当前位置作为 /back 目标 */
    public void recordLastLocation(Player player) {
        if (!config.isBackEnabled()) return;
        StoredLocation loc = StoredLocation.of(player.getLocation());
        if (loc != null) {
            store.saveLastLocation(player.getUniqueId(), loc);
        }
    }

    /** 记录指定位置（用于传送前 / 切换世界等事件） */
    public void recordSpecificLocation(Player player, Location location) {
        if (!config.isBackEnabled()) return;
        StoredLocation loc = StoredLocation.of(location);
        if (loc != null) {
            store.saveLastLocation(player.getUniqueId(), loc);
        }
    }

    /**
     * 判断是否为本插件内部发起的传送（避免 /back 自身反复覆盖记录）。
     * 以短时间内是否由 manager 发起为标志。
     */
    public boolean isInternalTeleport(Player player) {
        Long until = internalTeleportFlags.get(player.getUniqueId());
        if (until == null) return false;
        if (until < System.currentTimeMillis()) {
            internalTeleportFlags.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    /** 标记一次内部传送（短暂窗口内忽略 /back 记录） */
    private void markInternalTeleport(Player player) {
        internalTeleportFlags.put(player.getUniqueId(), System.currentTimeMillis() + 1000L);
    }

    /** 玩家退出时按配置记录 */
    public void recordOnQuit(Player player) {
        if (config.isBackEnabled() && config.isBackRecordQuit()) {
            recordLastLocation(player);
        }
    }

    // ==================== 传送执行 ====================

    /**
     * 将玩家传送到目标位置（带安全落点、可选延迟与音效）。
     * <p>
     * 若 teleport.yml 的 {@code teleport.delay-seconds} 大于 0，
     * 则先发出倒计时提示、延迟执行；期间玩家移动或再次传送会打断。
     *
     * @param target            目标 StoredLocation
     * @param crossWorldAllowed 是否允许跨世界（调用方按功能决定）
     * @return 传送是否成功发起（延迟模式下为「已开始等待」）
     */
    public boolean teleport(Player player, StoredLocation target, boolean crossWorldAllowed) {
        if (target == null) return false;
        if (!target.worldExists()) return false;
        if (!crossWorldAllowed && !config.isAllowCrossWorld()) {
            // 仅允许同世界
            if (!player.getWorld().getName().equals(target.world())) {
                return false;
            }
        }

        Location dest = target.toLocation();
        if (dest == null) return false;

        if (config.isSafeLocation()) {
            dest = findSafeLocation(dest);
        }

        int delay = config.getDelaySeconds();
        if (delay <= 0) {
            executeTeleport(player, dest);
            return true;
        }
        startDelayedTeleport(player, dest, delay);
        return true;
    }

    /** 立即执行传送（记录 /back、标记内部传送、播放音效） */
    private void executeTeleport(Player player, Location dest) {
        // 传送前记录原位置（用于再次 /back）
        if (config.isBackEnabled() && config.isBackRecordTeleport()) {
            recordLastLocation(player);
        }
        // 标记内部传送，避免监听器再次覆盖 /back 记录
        markInternalTeleport(player);
        player.teleport(dest);
        SoundUtil.play(player, player.getLocation(), config.getTeleportSound(), 0.7f, 1.0f);
    }

    /**
     * 发起一笔延迟传送：提示玩家并安排计划任务。
     * 若该玩家已有等待中的传送，先静默取消旧的。
     */
    private void startDelayedTeleport(Player player, Location dest, int delaySeconds) {
        cancelPending(player, false);

        Location start = player.getLocation().clone();
        String worldName = dest.getWorld() != null ? dest.getWorld().getName() : null;

        Map<String, String> ph = new HashMap<>();
        ph.put("seconds", String.valueOf(delaySeconds));
        plugin.getConfigManager().messages().send(player, "teleport.delayed",
                "<yellow>将在 {seconds} 秒后传送，移动将打断。</yellow>", ph);

        UUID uuid = player.getUniqueId();
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PendingTeleport current = pendingTeleports.remove(uuid);
            if (current == null) return; // 已被打断
            if (!player.isOnline()) return;
            // 执行前再校验世界是否仍存在
            if (current.worldName() == null || Bukkit.getWorld(current.worldName()) == null) {
                player.sendMessage(plugin.getMessage("teleport.world-missing",
                        "<red>目标世界不存在或已被卸载。</red>"));
                return;
            }
            executeTeleport(player, current.destination());
        }, delaySeconds * 20L);

        pendingTeleports.put(uuid, new PendingTeleport(dest, start, task, worldName));
    }

    /**
     * 取消等待中的延迟传送。
     *
     * @param notify 是否向玩家发送「已取消」提示
     * @return 是否确实取消了一笔等待中的传送
     */
    public boolean cancelPending(Player player, boolean notify) {
        PendingTeleport pending = pendingTeleports.remove(player.getUniqueId());
        if (pending == null) return false;
        if (pending.task() != null) pending.task().cancel();
        if (notify && player.isOnline()) {
            player.sendMessage(plugin.getMessage("teleport.cancelled",
                    "<red>传送已取消（你移动了）。</red>"));
        }
        return true;
    }

    /** 该玩家是否有等待中的延迟传送 */
    public boolean hasPending(Player player) {
        return pendingTeleports.containsKey(player.getUniqueId());
    }

    /**
     * 处理玩家移动事件：若移动跨越了方块坐标，则打断等待中的传送。
     * 只看方块坐标，避免转头 / 微小抖动误打断。
     *
     * @return 是否因此次移动取消了传送
     */
    public boolean handleMove(Player player, Location to) {
        PendingTeleport pending = pendingTeleports.get(player.getUniqueId());
        if (pending == null) return false;
        Location start = pending.start();
        if (start == null) return false;
        boolean movedBlock = start.getBlockX() != to.getBlockX()
                || start.getBlockY() != to.getBlockY()
                || start.getBlockZ() != to.getBlockZ();
        if (movedBlock) {
            cancelPending(player, true);
            return true;
        }
        return false;
    }

    /**
     * 从给定位置出发寻找安全落点：
     * 若脚下或身位是实心方块，则向上/向下搜索合适位置。
     */
    private Location findSafeLocation(Location loc) {
        World world = loc.getWorld();
        if (world == null) return loc;

        int maxDist = config.getSafeSearchDistance();
        int baseX = loc.getBlockX();
        int baseZ = loc.getBlockZ();
        int startY = loc.getBlockY();

        // 先检查原位置是否可用
        if (isSafe(world, baseX, startY, baseZ)) {
            return loc;
        }

        // 从原位置上下交替搜索
        for (int d = 1; d <= maxDist; d++) {
            int upY = startY + d;
            if (world.getMaxHeight() > upY && isSafe(world, baseX, upY, baseZ)) {
                return new Location(world, loc.getX(), upY, loc.getZ(), loc.getYaw(), loc.getPitch());
            }
            int downY = startY - d;
            if (downY > world.getMinHeight() && isSafe(world, baseX, downY, baseZ)) {
                return new Location(world, loc.getX(), downY, loc.getZ(), loc.getYaw(), loc.getPitch());
            }
        }
        // 找不到合适位置就返回原样（交由服务端处理）
        return loc;
    }

    /** 判断某坐标是否适合站立（脚下实心、身位和头部可通行） */
    private boolean isSafe(World world, int x, int y, int z) {
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        Block below = world.getBlockAt(x, y - 1, z);
        // 脚下需要有支撑（非空气且不可穿过）
        if (below.getType() == Material.AIR || below.isPassable()) return false;
        // 身位与头部需要可通行
        return feet.isPassable() && head.isPassable();
    }

    /**
     * 通用传送封装：传送到一个 Bukkit Location（会先转成 StoredLocation）。
     *
     * @return 成功返回 true；世界名缺失或安全位置解析失败返回 false
     */
    public boolean teleportToLocation(Player player, Location loc) {
        StoredLocation stored = StoredLocation.of(loc);
        return teleport(player, stored, true);
    }

    /**
     * 传送后标记冷却（由命令层在确认传送成功后调用）。
     */
    public void applyTeleportCooldown(Player player, int cooldownSeconds) {
        markCooldown(player, cooldownSeconds);
    }
}
