package cn.starfallplain.sfpmain.teleport.command;

import cn.starfallplain.sfpmain.SfpMain;
import cn.starfallplain.sfpmain.config.module.TeleportConfig;
import cn.starfallplain.sfpmain.teleport.TeleportManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * /rtp 随机传送命令。
 * <p>
 * 在当前世界（须在 teleport.yml 的 {@code rtp.worlds} 白名单内，默认仅主世界）
 * 的 {@code rtp.radius} 范围内随机找一处安全落点，再走统一的传送流程。
 * <p>
 * 冷却（默认 600 秒 = 10 分钟）与选点过程由 {@link TeleportManager#randomTeleport(Player)}
 * 统一处理，本类只做「玩家 / 权限 / 开关」三项入口校验。
 */
public class RtpCommand implements BasicCommand {

    private final SfpMain plugin;
    private final TeleportManager manager;

    public RtpCommand(SfpMain plugin, TeleportManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public void execute(@NotNull CommandSourceStack source, @NotNull String[] args) {
        CommandSender sender = source.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessage("common.player-only", "<red>该命令只能由玩家执行。</red>"));
            return;
        }
        if (!player.hasPermission("sfpmenu.teleport")) {
            player.sendMessage(plugin.getMessage("common.no-permission", "<red>你没有权限使用此命令！</red>"));
            return;
        }
        TeleportConfig config = manager.getConfig();
        if (!config.isEnabled() || !config.isRtpEnabled()) {
            player.sendMessage(plugin.getMessage("common.feature-disabled", "<red>该功能当前未启用。</red>"));
            return;
        }
        manager.randomTeleport(player);
    }

    @Override
    public Collection<String> suggest(@NotNull CommandSourceStack source, @NotNull String[] args) {
        return List.of();
    }
}
