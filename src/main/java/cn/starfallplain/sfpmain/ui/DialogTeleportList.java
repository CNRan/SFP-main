package cn.starfallplain.sfpmain.ui;

import cn.starfallplain.sfpmain.SfpMain;
import cn.starfallplain.sfpmain.config.Messages;
import cn.starfallplain.sfpmain.menu.MenuManager;
import cn.starfallplain.sfpmain.teleport.TeleportManager;
import cn.starfallplain.sfpmain.teleport.db.StoredLocation;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * dialogUI 版的家列表 / 传送点列表（两者共用一个实现，{@code home} 区分数据源）。
 * <p>
 * 一页显示 {@value #PAGE_SIZE} 个条目，底部「上一页 / 返回主菜单 / 下一页」按钮翻页
 * （点翻页 = 重开一个弹窗）。点击条目 = 传送，统一走
 * {@link TeleportManager#teleportHome} / {@link TeleportManager#teleportWarp}，
 * 与命令、箱子 UI 行为一致。
 * <p>
 * 家列表的条目点击后会再弹「传送 / 删除」选择框（dialogUI 按钮无左右键，
 * 用两步交互补上删除），传送点条目直接传送。
 */
public final class DialogTeleportList {

    private static final int PAGE_SIZE = 6;
    private static final Duration LIFETIME = Duration.ofMinutes(10);

    private DialogTeleportList() {
    }

    /**
     * @param home true = 家列表，false = 传送点列表
     * @param page 0-based 页码
     */
    public static void open(SfpMain plugin, Player player, boolean home, int page) {
        TeleportManager manager = plugin.getTeleportManager();
        if (manager == null) {
            player.sendMessage(plugin.getMessage("common.feature-disabled", "<red>该功能当前未启用。</red>"));
            return;
        }

        List<String> names = home
                ? manager.getHomeStore().listNames(player.getUniqueId())
                : manager.getWarpStore().listNames();

        if (names.isEmpty()) {
            player.sendMessage(plugin.getMessage(home ? "home.none" : "warp.none",
                    home ? "<yellow>你还没有设置任何家。</yellow>" : "<yellow>当前没有任何传送点。</yellow>"));
            return;
        }

        int totalPages = Math.max(1, (names.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        final int currentPage = Math.max(0, Math.min(page, totalPages - 1));

        int start = currentPage * PAGE_SIZE;
        List<String> pageNames = names.subList(start, Math.min(start + PAGE_SIZE, names.size()));

        ClickCallback.Options options = ClickCallback.Options.builder().uses(1).lifetime(LIFETIME).build();
        List<ActionButton> buttons = new ArrayList<>();

        for (String name : pageNames) {
            StoredLocation loc = home
                    ? manager.getHomeStore().get(player.getUniqueId(), name)
                    : manager.getWarpStore().get(name);
            Component label = Messages.deserialize("<white>" + name + "</white>");
            Component tooltip = loc != null ? Messages.deserialize("<gray>" + loc.describe() + "</gray>") : null;

            final String targetName = name;
            ActionButton.Builder builder = ActionButton.builder(label);
            if (tooltip != null) {
                builder.tooltip(tooltip);
            }
            builder.action(DialogAction.customClick((view, audience) -> {
                if (audience instanceof Player p && p.isOnline()) {
                    if (home) {
                        openHomeActionDialog(plugin, p, manager, targetName, options);
                    } else {
                        manager.teleportWarp(p, targetName);
                    }
                }
            }, options));
            buttons.add(builder.build());
        }

        // 导航：上一页 / 返回主菜单 / 下一页
        if (currentPage > 0) {
            buttons.add(navButton(plugin, "teleport.prev", "<!i><yellow>上一页</yellow>",
                    player, home, currentPage - 1, options));
        }
        buttons.add(ActionButton.builder(Messages.deserialize("<!i><aqua>返回主菜单</aqua>"))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player p && p.isOnline()) {
                        MenuManager.openMenuByPreference(plugin, p);
                    }
                }, options))
                .build());
        if (currentPage < totalPages - 1) {
            buttons.add(navButton(plugin, "teleport.next", "<!i><yellow>下一页</yellow>",
                    player, home, currentPage + 1, options));
        }

        Component title = Messages.deserialize("<light_purple>" + (home ? "我的家" : "传送点")
                + "</light_purple> <gray>- 第 " + (currentPage + 1) + "/" + totalPages + " 页</gray>");

        Dialog dialog = Dialog.create(builder -> builder
                .empty()
                .base(DialogBase.builder(title)
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.multiAction(buttons).columns(1).build()));

        player.showDialog(dialog);
    }

    /**
     * 点击家条目后弹出「传送 / 删除」选择框。
     * <p>
     * dialogUI 的按钮没有左右键之分，所以用两步交互补上「删除自己的家」，
     * 与箱子 UI 的「左键传送 / 右键删除」功能对等。
     */
    private static void openHomeActionDialog(SfpMain plugin, Player player,
                                             TeleportManager manager, String name,
                                             ClickCallback.Options options) {
        Component title = Messages.deserialize("<aqua>我的家</aqua>");
        Component body = Messages.deserialize("<yellow>" + name + "</yellow> <gray>—— 选择操作</gray>");

        ActionButton teleport = ActionButton.builder(Messages.deserialize("<green>传送</green>"))
                .tooltip(Messages.deserialize("<gray>传送到这个家</gray>"))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player p && p.isOnline()) {
                        manager.teleportHome(p, name);
                    }
                }, options))
                .build();
        ActionButton delete = ActionButton.builder(Messages.deserialize("<red>删除这个家</red>"))
                .tooltip(Messages.deserialize("<gray>等同 /delhome " + name + "</gray>"))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player p && p.isOnline()) {
                        manager.deleteHome(p, name);
                    }
                }, options))
                .build();

        Dialog dialog = Dialog.create(builder -> builder
                .empty()
                .base(DialogBase.builder(title)
                        .canCloseWithEscape(true)
                        .body(List.of(DialogBody.plainMessage(body)))
                        .build())
                .type(DialogType.confirmation(teleport, delete)));

        player.showDialog(dialog);
    }

    private static ActionButton navButton(SfpMain plugin, String msgKey, String fallback,
                                          Player player, boolean home, int page,
                                          ClickCallback.Options options) {
        return ActionButton.builder(Messages.deserialize(plugin.getRawMessage(msgKey, fallback)))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player p && p.isOnline()) {
                        open(plugin, p, home, page);
                    }
                }, options))
                .build();
    }
}
