package cn.starfallplain.sfpmain.teleport.command;

import cn.starfallplain.sfpmain.SfpMain;
import cn.starfallplain.sfpmain.config.module.TeleportConfig;
import cn.starfallplain.sfpmain.teleport.TeleportManager;
import cn.starfallplain.sfpmain.teleport.db.StoredLocation;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * /back —— 返回上一位置（上次传送前 / 死亡点）。
 * 位置数据来自 SQLite 的 last_locations 表。
 * <p>
 * Paper 插件不能用 plugin.yml 声明命令，故改为实现 {@link BasicCommand}，
 * 由主类在 {@code LifecycleEvents.COMMANDS} 中注册。
 */
public class BackCommand implements BasicCommand {

    private final SfpMain plugin;
    private final TeleportManager manager;

    public BackCommand(SfpMain plugin, TeleportManager manager) {
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
        if (!config.isBackEnabled()) {
            player.sendMessage(plugin.getMessage("common.feature-disabled", "<red>该功能当前未启用。</red>"));
            return;
        }
        if (!manager.isStorageAvailable()) {
            player.sendMessage(plugin.getMessage("teleport.storage-error",
                    "<red>传送数据库不可用，请联系管理员。</red>"));
            return;
        }

        StoredLocation target = manager.getBackStore().get(player.getUniqueId());
        if (target == null) {
            player.sendMessage(plugin.getMessage("back.no-location", "<red>没有可返回的位置。</red>"));
            return;
        }
        if (!target.worldExists()) {
            player.sendMessage(plugin.getMessage("teleport.world-missing",
                    "<red>目标世界不存在或已被卸载。</red>"));
            return;
        }

        boolean ok = manager.teleport(player, target);
        if (ok) {
            Map<String, String> ph = new HashMap<>();
            ph.put("location", target.describe());
            plugin.getConfigManager().messages().send(player, "back.success",
                    "<green>已返回上一位置（{location}）。</green>", ph);
        } else {
            player.sendMessage(plugin.getMessage("teleport.failed", "<red>传送失败。</red>"));
        }
    }
}
