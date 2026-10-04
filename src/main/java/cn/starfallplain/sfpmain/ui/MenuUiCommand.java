package cn.starfallplain.sfpmain.ui;

import cn.starfallplain.sfpmain.SfpMain;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code /menuui [dialogui|box]} —— 切换自己的界面样式。
 * <p>
 * 无参数时显示当前样式与用法；带参数时切换并即时生效（下次打开菜单就是新样式）。
 * 权限 {@code sfpmenu.player}（普通玩家都能切）。
 * 主菜单里也有一个等价的切换按钮，两者共用 {@link UiPreferenceStore}。
 */
public class MenuUiCommand implements BasicCommand {

    private static final List<String> MODES = List.of("dialogui", "box");

    private final SfpMain plugin;

    public MenuUiCommand(SfpMain plugin) {
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

        UiPreferenceStore store = plugin.getUiPreferenceStore();
        if (store == null) {
            player.sendMessage(plugin.getMessage("common.feature-disabled", "<red>该功能当前未启用。</red>"));
            return;
        }

        if (args.length == 0) {
            UiMode current = store.get(player.getUniqueId());
            Map<String, String> ph = new HashMap<>();
            ph.put("mode", current == UiMode.DIALOG ? "dialogUI（弹窗）" : "箱子界面");
            plugin.getConfigManager().messages().send(player, "menuui.current",
                    "<gray>当前界面样式：<white>{mode}</white>。切换：/menuui dialogui 或 /menuui box</gray>", ph);
            return;
        }

        String key = args[0].toLowerCase();
        if (!MODES.contains(key)) {
            plugin.getConfigManager().messages().send(player, "menuui.usage",
                    "<red>用法：/menuui dialogui 或 /menuui box</red>", Map.of());
            return;
        }

        UiMode mode = UiMode.fromKey(key);
        store.set(player.getUniqueId(), mode);
        plugin.getConfigManager().messages().send(player,
                mode == UiMode.DIALOG ? "menuui.toggled-dialog" : "menuui.toggled-box",
                mode == UiMode.DIALOG
                        ? "<green>界面已切换为 dialogUI（弹窗界面），下次打开菜单生效。</green>"
                        : "<green>界面已切换为箱子界面，下次打开菜单生效。</green>",
                Map.of());
    }

    @Override
    public Collection<String> suggest(@NotNull CommandSourceStack source, @NotNull String[] args) {
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase();
        return MODES.stream().filter(m -> m.startsWith(prefix)).toList();
    }
}
