package cn.starfallplain.sfpmain.config.module;

import cn.starfallplain.sfpmain.config.AbstractConfig;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 假人配置（bot.yml）。
 * <p>
 * 假人是**真正的玩家实体**（NMS ServerPlayer），不是盔甲架之类的近似物：
 * 它会出现在在线玩家列表里，并天然保持周围区块加载。详见 bot 包的 NmsBotFactory。
 */
public final class BotConfig extends AbstractConfig {

    private boolean enabled;
    private boolean restoreOnStart;
    private String skinSource;
    private int maxNameLength;
    private int maxPerPlayer;

    public BotConfig(JavaPlugin plugin) {
        super(plugin, "bot.yml");
    }

    @Override
    protected void onLoaded() {
        enabled = getBoolean("enabled", true);
        restoreOnStart = getBoolean("restore-on-start", true);
        skinSource = getString("skin-source", "");
        maxNameLength = Math.max(1, Math.min(16, getInt("max-name-length", 16)));
        maxPerPlayer = Math.max(0, getInt("max-per-player", 3));
    }

    public boolean isEnabled() { return enabled; }

    /** 启动时是否按 bots.yml 自动重建假人 */
    public boolean isRestoreOnStart() { return restoreOnStart; }

    /**
     * 皮肤来源：
     * <ul>
     *   <li>留空 —— 用创建者自己的皮肤（控制台创建时为默认皮肤）</li>
     *   <li>{@code none} —— 默认皮肤</li>
     *   <li>玩家名 —— 用该玩家的皮肤</li>
     * </ul>
     */
    public String getSkinSource() { return skinSource; }

    /** 名字最大长度（Minecraft 玩家名限制为 16） */
    public int getMaxNameLength() { return maxNameLength; }

    /** 每位玩家最多创建几个假人（0 表示不限） */
    public int getMaxPerPlayer() { return maxPerPlayer; }
}
