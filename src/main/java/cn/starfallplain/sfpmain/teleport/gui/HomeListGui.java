package cn.starfallplain.sfpmain.teleport.gui;

import cn.starfallplain.sfpmain.SfpMain;
import cn.starfallplain.sfpmain.teleport.TeleportManager;
import org.bukkit.entity.Player;

/**
 * 家列表 GUI 入口（薄封装，固定为 HOME 类型）。
 */
public final class HomeListGui {

    private HomeListGui() {
    }

    public static void open(SfpMain plugin, Player player, TeleportManager manager, int page) {
        TeleportListGui.open(plugin, player, manager, TeleportListHolder.ListType.HOME, page);
    }
}
