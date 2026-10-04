package cn.starfallplain.sfpmain.display;

import cn.starfallplain.sfpmain.SfpMain;
import cn.starfallplain.sfpmain.config.ConfigManager;
import cn.starfallplain.sfpmain.config.module.TabConfig;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Tab 列表（按 Tab 弹出的玩家列表）的头部 / 底部文字。
 * <p>
 * 内容与刷新频率全部来自 tab.yml；每秒（默认）为所有在线玩家刷新一次。
 */
public class TabManager {

    private final TabConfig config;
    private final BukkitTask task;

    public TabManager(SfpMain plugin, ConfigManager configManager) {
        this.config = configManager.tab();
        int period = Math.max(1, config.getRefreshTicks());
        // 延后 1 秒开始，避开插件启用栈
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 20L, period);
    }

    private void refresh() {
        if (config.getHeader().isEmpty() && config.getFooter().isEmpty()) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Paper：Tab 头尾走 Adventure 的 Audience#sendPlayerListHeaderAndFooter
            player.sendPlayerListHeaderAndFooter(
                    DisplayText.renderLines(player, config.getHeader()),
                    DisplayText.renderLines(player, config.getFooter()));
        }
    }

    public void shutdown() {
        if (task != null) task.cancel();
    }
}
