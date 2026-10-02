package cn.starfallplain.CN_Ran.sfp.config.module;

import cn.starfallplain.CN_Ran.sfp.config.AbstractConfig;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 传送系统配置（teleport.yml）。
 * <p>
 * 覆盖 /back、/home、/warp 三个子功能的开关与参数，
 * 以及 SQLite 数据库与通用传送参数。
 */
public final class TeleportConfig extends AbstractConfig {

    // 总开关
    private boolean enabled;

    // 数据库
    private String dbFile;
    private boolean autoCommit;

    // back
    private boolean backEnabled;
    private boolean backRecordDeath;
    private boolean backRecordTeleport;
    private boolean backRecordWorldChange;
    private boolean backRecordQuit;

    // home
    private boolean homeEnabled;
    private int homeMaxHomes;
    private int homeSetCooldownSeconds;
    private int homeTeleportCooldownSeconds;

    // warp
    private boolean warpEnabled;
    private String warpSetPermission;
    private String warpDeletePermission;
    private int warpTeleportCooldownSeconds;

    // 通用传送
    private boolean allowCrossWorld;
    private int delaySeconds;
    private String teleportSound;
    private boolean safeLocation;
    private int safeSearchDistance;

    public TeleportConfig(JavaPlugin plugin) {
        super(plugin, "teleport.yml");
    }

    @Override
    protected void onLoaded() {
        enabled = getBoolean("enabled", true);

        dbFile = getString("database.file", "teleport.db");
        autoCommit = getBoolean("database.auto-commit", true);

        backEnabled = getBoolean("back.enabled", true);
        backRecordDeath = getBoolean("back.record-death", true);
        backRecordTeleport = getBoolean("back.record-teleport", true);
        backRecordWorldChange = getBoolean("back.record-world-change", true);
        backRecordQuit = getBoolean("back.record-quit", false);

        homeEnabled = getBoolean("home.enabled", true);
        homeMaxHomes = getInt("home.max-homes", 5);
        homeSetCooldownSeconds = getInt("home.set-cooldown-seconds", 5);
        homeTeleportCooldownSeconds = getInt("home.teleport-cooldown-seconds", 3);

        warpEnabled = getBoolean("warp.enabled", true);
        warpSetPermission = getString("warp.set-permission", "sfpmenu.warp.set");
        warpDeletePermission = getString("warp.delete-permission", "sfpmenu.warp.delete");
        warpTeleportCooldownSeconds = getInt("warp.teleport-cooldown-seconds", 3);

        allowCrossWorld = getBoolean("teleport.allow-cross-world", true);
        delaySeconds = Math.max(0, getInt("teleport.delay-seconds", 0));
        teleportSound = getString("teleport.sound", "ENTITY_ENDERMAN_TELEPORT");
        safeLocation = getBoolean("teleport.safe-location", true);
        safeSearchDistance = Math.max(0, getInt("teleport.safe-search-distance", 5));
    }

    // ==================== 访问器 ====================

    public boolean isEnabled() { return enabled; }

    public String getDbFile() { return dbFile; }

    public boolean isAutoCommit() { return autoCommit; }

    public boolean isBackEnabled() { return backEnabled; }

    public boolean isBackRecordDeath() { return backRecordDeath; }

    public boolean isBackRecordTeleport() { return backRecordTeleport; }

    public boolean isBackRecordWorldChange() { return backRecordWorldChange; }

    public boolean isBackRecordQuit() { return backRecordQuit; }

    public boolean isHomeEnabled() { return homeEnabled; }

    /** 家数量上限；-1 表示无限制 */
    public int getHomeMaxHomes() { return homeMaxHomes; }

    public int getHomeSetCooldownSeconds() { return homeSetCooldownSeconds; }

    public int getHomeTeleportCooldownSeconds() { return homeTeleportCooldownSeconds; }

    public boolean isWarpEnabled() { return warpEnabled; }

    public String getWarpSetPermission() { return warpSetPermission; }

    public String getWarpDeletePermission() { return warpDeletePermission; }

    public int getWarpTeleportCooldownSeconds() { return warpTeleportCooldownSeconds; }

    public boolean isAllowCrossWorld() { return allowCrossWorld; }

    public int getDelaySeconds() { return delaySeconds; }

    public String getTeleportSound() { return teleportSound; }

    public boolean isSafeLocation() { return safeLocation; }

    public int getSafeSearchDistance() { return safeSearchDistance; }
}
