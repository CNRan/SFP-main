package cn.starfallplain.sfpmain.teleport;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 玩家退出时清理与其相关的 tpa 请求。
 * <p>
 * 请求是会话态（不入库），玩家一走就必须作废：否则对方点「接受」时会指向一个已离线的人。
 * 单独一个很薄的监听器，避免把 tpa 的清理逻辑塞进 {@link TeleportListener}。
 */
public class TpaListener implements Listener {

    private final TpaManager tpaManager;

    public TpaListener(TpaManager tpaManager) {
        this.tpaManager = tpaManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        tpaManager.onQuit(event.getPlayer());
    }
}
