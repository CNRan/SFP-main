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

    // tpa（玩家间传送请求）
    private boolean tpaEnabled;
    private int tpaExpireSeconds;
    private int tpaRequestCooldownSeconds;
    private int tpaTeleportCooldownSeconds;

    // 通用传送
    private int delaySeconds;
    private int particleIntervalTicks;
    private boolean safeLocation;
    private int safeSearchDistance;
    private String waitStartSound;
    private String waitTickSound;
    private String teleportSound;

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

        tpaEnabled = getBoolean("tpa.enabled", true);
        tpaExpireSeconds = Math.max(5, getInt("tpa.expire-seconds", 60));
        tpaRequestCooldownSeconds = Math.max(0, getInt("tpa.request-cooldown-seconds", 3));
        tpaTeleportCooldownSeconds = Math.max(0, getInt("tpa.teleport-cooldown-seconds", 3));

        delaySeconds = Math.max(0, getInt("teleport.delay-seconds", 3));
        particleIntervalTicks = Math.max(1, getInt("teleport.particle-interval-ticks", 20));
        safeLocation = getBoolean("teleport.safe-location", true);
        safeSearchDistance = Math.max(0, getInt("teleport.safe-search-distance", 5));
        waitStartSound = getString("teleport.sounds.start", "BLOCK_NOTE_BLOCK_PLING");
        waitTickSound = getString("teleport.sounds.tick", "BLOCK_NOTE_BLOCK_HAT");
        teleportSound = getString("teleport.sounds.teleport", "ENTITY_ENDERMAN_TELEPORT");
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

    public boolean isTpaEnabled() { return tpaEnabled; }

    /** tpa 请求有效期（秒），至少 5 */
    public int getTpaExpireSeconds() { return tpaExpireSeconds; }

    public int getTpaRequestCooldownSeconds() { return tpaRequestCooldownSeconds; }

    public int getTpaTeleportCooldownSeconds() { return tpaTeleportCooldownSeconds; }

    public int getDelaySeconds() { return delaySeconds; }

    /** 等待期间刷粒子的间隔（tick） */
    public int getParticleIntervalTicks() { return particleIntervalTicks; }

    public boolean isSafeLocation() { return safeLocation; }

    public int getSafeSearchDistance() { return safeSearchDistance; }

    public String getWaitStartSound() { return waitStartSound; }

    public String getWaitTickSound() { return waitTickSound; }

    public String getTeleportSound() { return teleportSound; }
}
