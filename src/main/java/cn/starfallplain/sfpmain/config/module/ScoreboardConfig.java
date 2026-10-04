package cn.starfallplain.sfpmain.config.module;

import cn.starfallplain.sfpmain.config.AbstractConfig;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * 计分板配置（scoreboard.yml）：标题、行、刷新间隔。
 */
public final class ScoreboardConfig extends AbstractConfig {

    /** 右侧计分板最多 15 行（客户端限制） */
    public static final int MAX_LINES = 15;

    private boolean enabled;
    private int refreshTicks;
    private String title;
    private List<String> lines;

    public ScoreboardConfig(JavaPlugin plugin) {
        super(plugin, "scoreboard.yml");
    }

    @Override
    protected void onLoaded() {
        enabled = getBoolean("enabled", true);
        refreshTicks = Math.max(1, getInt("refresh-ticks", 20));
        title = getString("title", "<gradient:aqua:light_purple><b>星落平原</b></gradient>");
        lines = getStringList("lines");
        if (lines.size() > MAX_LINES) {
            plugin.getLogger().warning("scoreboard.yml 配置了 " + lines.size()
                    + " 行，超出右侧上限 " + MAX_LINES + " 行，多余的会被忽略。");
            lines = List.copyOf(lines.subList(0, MAX_LINES));
        }
    }

    public boolean isEnabled() { return enabled; }

    public int getRefreshTicks() { return refreshTicks; }

    public String getTitle() { return title; }

    public List<String> getLines() { return lines; }
}
