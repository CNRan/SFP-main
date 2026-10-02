package cn.starfallplain.CN_Ran.sfp.ui;

import cn.starfallplain.CN_Ran.sfp.StarfallplainMenu;
import cn.starfallplain.CN_Ran.sfp.config.Messages;
import cn.starfallplain.CN_Ran.sfp.menu.MenuManager;
import cn.starfallplain.CN_Ran.sfp.teleport.TpaManager;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * dialogUI 版的「选择传送目标」界面（在线玩家列表，不含自己）。
 * <p>
 * 一页 {@value #PAGE_SIZE} 个玩家，底部「上一页 / 返回主菜单 / 下一页」翻页。
 * 点击玩家 = 弹出「传送方向」选择框（传送到他身边 / 让他传送到你身边），
 * 与箱子 UI 的「左键 tpa / 右键 tpahere」功能对等（dialogUI 按钮无左右键，故用两步交互）。
 */
public final class DialogTpaTarget {

    private static final int PAGE_SIZE = 6;
    private static final Duration LIFETIME = Duration.ofMinutes(10);

    private DialogTpaTarget() {
    }

    public static void open(StarfallplainMenu plugin, Player player, int page) {
        TpaManager tpaManager = plugin.getTpaManager();
        if (tpaManager == null) {
            player.sendMessage(plugin.getMessage("common.feature-disabled", "<red>该功能当前未启用。</red>"));
            return;
        }

        List<Player> targets = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getUniqueId().equals(player.getUniqueId())) {
                targets.add(p);
            }
        }

        if (targets.isEmpty()) {
            player.sendMessage(plugin.getMessage("tpa.no-targets", "<yellow>当前没有其他在线玩家。</yellow>"));
            return;
        }

        int totalPages = Math.max(1, (targets.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        final int currentPage = Math.max(0, Math.min(page, totalPages - 1));

        int start = currentPage * PAGE_SIZE;
        List<Player> pageTargets = targets.subList(start, Math.min(start + PAGE_SIZE, targets.size()));

        ClickCallback.Options options = ClickCallback.Options.builder().uses(1).lifetime(LIFETIME).build();
        List<ActionButton> buttons = new ArrayList<>();

        for (Player target : pageTargets) {
            final Player chosen = target;
            Component label = Messages.deserialize("<white>" + target.getName() + "</white>");
            Component tooltip = Messages.deserialize("<gray>" + target.getWorld().getName() + "</gray>");

            buttons.add(ActionButton.builder(label)
                    .tooltip(tooltip)
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player p && p.isOnline()) {
                            openDirectionDialog(plugin, p, chosen, tpaManager, options);
                        }
                    }, options))
                    .build());
        }

        if (currentPage > 0) {
            buttons.add(navButton(plugin, player, currentPage - 1, "<!i><yellow>上一页</yellow>", options));
        }
        buttons.add(ActionButton.builder(Messages.deserialize("<!i><aqua>返回主菜单</aqua>"))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player p && p.isOnline()) {
                        MenuManager.openMenuByPreference(plugin, p);
                    }
                }, options))
                .build());
        if (currentPage < totalPages - 1) {
            buttons.add(navButton(plugin, player, currentPage + 1, "<!i><yellow>下一页</yellow>", options));
        }

        Component title = Messages.deserialize("<aqua>选择传送目标</aqua> <gray>- 第 "
                + (currentPage + 1) + "/" + totalPages + " 页</gray>");

        Dialog dialog = Dialog.create(builder -> builder
                .empty()
                .base(DialogBase.builder(title)
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.multiAction(buttons).columns(1).build()));

        player.showDialog(dialog);
    }

    /**
     * 选定玩家后弹出「传送方向」选择框：传送到他身边（/tpa）/ 让他传送到你身边（/tpahere）。
     * <p>
     * dialogUI 的按钮没有左右键之分，所以用两步交互补齐 tpa / tpahere 两种方向，
     * 与箱子 UI 的「左键 tpa / 右键 tpahere」功能对等。
     */
    private static void openDirectionDialog(StarfallplainMenu plugin, Player player, Player target,
                                            TpaManager tpaManager, ClickCallback.Options options) {
        Component title = Messages.deserialize("<aqua>玩家传送</aqua>");
        Component body = Messages.deserialize("<yellow>" + target.getName() + "</yellow> <gray>—— 选择传送方向</gray>");

        ActionButton to = ActionButton.builder(Messages.deserialize("<green>传送到他身边</green>"))
                .tooltip(Messages.deserialize("<gray>等同 /tpa " + target.getName() + "</gray>"))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player p && p.isOnline()) {
                        tpaManager.request(p, target, TpaManager.Type.TO);
                    }
                }, options))
                .build();
        ActionButton here = ActionButton.builder(Messages.deserialize("<aqua>让他传送到你身边</aqua>"))
                .tooltip(Messages.deserialize("<gray>等同 /tpahere " + target.getName() + "</gray>"))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player p && p.isOnline()) {
                        tpaManager.request(p, target, TpaManager.Type.HERE);
                    }
                }, options))
                .build();

        Dialog dialog = Dialog.create(builder -> builder
                .empty()
                .base(DialogBase.builder(title)
                        .canCloseWithEscape(true)
                        .body(List.of(DialogBody.plainMessage(body)))
                        .build())
                .type(DialogType.confirmation(to, here)));

        player.showDialog(dialog);
    }

    private static ActionButton navButton(StarfallplainMenu plugin, Player player, int page,
                                          String label, ClickCallback.Options options) {
        return ActionButton.builder(Messages.deserialize(label))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player p && p.isOnline()) {
                        open(plugin, p, page);
                    }
                }, options))
                .build();
    }
}
