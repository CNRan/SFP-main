package cn.starfallplain.sfpmain.config.module;

import cn.starfallplain.sfpmain.config.AbstractConfig;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Tab 列表配置（tab.yml）：头部 / 底部多行文字、刷新间隔。
 */
public final class TabConfig extends AbstractConfig {

    private boolean enabled;
    private int refreshTicks;
    private List<String> header;
    private List<String> footer;

    public TabConfig(JavaPlugin plugin) {
        super(plugin, "tab.yml");
    }

    @Override
    protected void onLoaded() {
        enabled = getBoolean("enabled", true);
        refreshTicks = Math.max(1, getInt("refresh-ticks", 20));
        header = getStringList("header");
        footer = getStringList("footer");
    }

    public boolean isEnabled() { return enabled; }

    public int getRefreshTicks() { return refreshTicks; }

    public List<String> getHeader() { return header; }

    public List<String> getFooter() { return footer; }
}
