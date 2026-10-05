package cn.starfallplain.sfpmain.punish;

import cn.starfallplain.sfpmain.SfpMain;
import cn.starfallplain.sfpmain.config.module.PunishConfig;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 处罚系统的监听器：把「惰性到期判定」安放到必要的时机上。
 * <ul>
 *   <li>{@link PlayerJoinEvent} —— 补录玩家名映射；若存在生效封禁（且未过期）则拒绝登录；
 *       有生效禁言则提示。</li>
 *   <li>{@link AsyncChatEvent} —— 发言前检查禁言（此处是禁言真正生效的地方）。</li>
 *   <li>{@link PlayerQuitEvent} —— 刷新 last_seen。</li>
 * </ul>
 * 不注册任何定时任务 —— 过期的处罚只在这些时机（以及查询 / 解禁）被发现并清理。
 */
public class PunishListener implements Listener {

    private final SfpMain plugin;
    private final PunishManager manager;
    private final PunishConfig config;

    public PunishListener(SfpMain plugin, PunishManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.config = manager.getConfig();
    }

    /**
     * 进服：补录名字 → 查封禁（未过期则踢出）→ 查禁言（提示）。
     * <p>
     * 用 LOW 优先级，确保我们在其它插件之后处理；这里用 {@code kick()} 而不是取消事件，
     * 因为踢人消息能自定义、且玩家能明确看到原因。
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        // 1) 补录名字映射（离线目标选择依赖它）
        manager.recordJoin(player);

        // 2) 封禁检查（getActivePunishment 内部会做惰性到期判定）
        Punishment ban = manager.activeBan(player.getUniqueId());
        if (ban != null) {
            config.log("拦截已封禁玩家登录：" + player.getName() + "（处罚 " + ban.id() + "）");
            player.kick(manager.buildBanKickMessage(ban));
            return;
        }

        // 3) 禁言提示（不禁登，只提醒）
        Punishment mute = manager.activeMute(player.getUniqueId());
        if (mute != null) {
            player.sendMessage(config.muteNotice(manager.placeholders(mute)));
        }
    }

    /**
     * 发言：有生效禁言则拦下，并把禁言信息发回给发言者。
     * <p>
     * 用 Paper 的 {@link AsyncChatEvent}（旧的 AsyncPlayerChatEvent 已废弃）。
     * 该事件在<b>异步线程</b>触发，因此这里用纯内存的 {@code cachedMute}（不碰数据库连接），
     * 只做只读判定。缓存由主线程在施加 / 解除 / 到期时同步维护。
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        var player = event.getPlayer();
        Punishment mute = manager.cachedMute(player.getUniqueId());
        if (mute == null) return;
        event.setCancelled(true);
        player.sendMessage(config.muteNotice(manager.placeholders(mute)));
    }

    /** 退出：刷新 last_seen */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        manager.recordQuit(event.getPlayer());
    }
}
