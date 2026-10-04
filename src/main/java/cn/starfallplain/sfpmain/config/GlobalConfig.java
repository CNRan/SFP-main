package cn.starfallplain.sfpmain.config;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * 全局配置（config.yml）：与具体功能包无关的通用项。
 */
public final class GlobalConfig {

    private final JavaPlugin plugin;

    // 调试模式：开启后输出更详细的日志
    private boolean debug;

    public GlobalConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        this.debug = plugin.getConfig().getBoolean("debug", false);
    }

    public boolean isDebug() { return debug; }

    public void debug(String message) {
        if (debug) plugin.getLogger().info("[DEBUG] " + message);
    }
}
