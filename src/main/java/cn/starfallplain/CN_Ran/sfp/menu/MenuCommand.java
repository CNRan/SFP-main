package cn.starfallplain.CN_Ran.sfp.menu;

import cn.starfallplain.CN_Ran.sfp.StarfallplainMenu;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * /menu 与 /m：打开主菜单。权限与提示文案来自 menu.yml。
 * <p>
 * Paper 插件不能用 plugin.yml 声明命令，故改为实现 {@link BasicCommand}，
 * 由主类在 {@code LifecycleEvents.COMMANDS} 中注册（/m 作为别名注册）。
 * <p>
 * 权限不走 {@link BasicCommand#permission()}：那样无权限者眼里该命令直接「不存在」，
 * 给不出提示，故这里手动校验，保留原 permission-message 的提示行为。
 */
public class MenuCommand implements BasicCommand {

    private final StarfallplainMenu plugin;

    public MenuCommand(StarfallplainMenu plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(@NotNull CommandSourceStack source, @NotNull String[] args) {
        CommandSender sender = source.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessage("common.player-only", "<red>该命令只能由玩家执行。</red>"));
            return;
        }

        if (!player.hasPermission("sfpmenu.player")) {
            player.sendMessage(plugin.getMessage("common.no-permission", "<red>你没有权限使用此命令！</red>"));
            return;
        }

        // 开关与打开权限的校验统一在 MenuManager.openMainMenu 里做，
        // 与各子界面的「返回主菜单」按钮共用同一套行为
        MenuManager.openMainMenu(plugin, player);
    }
}
