package cn.starfallplain.sfpmain.menu;

import cn.starfallplain.sfpmain.SfpMain;
import cn.starfallplain.sfpmain.config.Messages;
import cn.starfallplain.sfpmain.config.module.MenuConfig;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * 玩家进服提示：告诉新来的玩家「可以合成菜单钟」。
 * <p>
 * 文案来自 menu.yml 的 {@code menu-clock.join-notice}（可多行，MiniMessage）。
 * 只有在<b>主菜单与菜单钟都启用</b>时才会提示 —— 否则合出来的钟点了没反应，提示反而是误导。
 * <p>
 * 发送时延后 1 tick：玩家刚进服时会有一串登录/欢迎消息，推迟一 tick 能让本提示落在最后，
 * 不被其它插件的欢迎语淹没。
 */
public class MenuClockJoinListener implements Listener {

    private final SfpMain plugin;
    private final MenuConfig config;

    public MenuClockJoinListener(SfpMain plugin, MenuConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!config.isEnabled() || !config.isMenuClockEnabled()) return;
        var lines = config.getMenuClockJoinNotice();
        if (lines == null || lines.isEmpty()) return;

        var player = event.getPlayer();
        // 延后 1 tick：避免与登录消息 / 其它插件的欢迎提示挤在一起
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            for (String line : lines) {
                if (line == null || line.isBlank()) continue;
                player.sendMessage(Messages.deserialize(line));
            }
        });
    }
}
