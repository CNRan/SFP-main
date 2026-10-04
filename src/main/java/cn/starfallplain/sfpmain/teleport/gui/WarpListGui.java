package cn.starfallplain.sfpmain.teleport.gui;

import cn.starfallplain.sfpmain.SfpMain;
import cn.starfallplain.sfpmain.teleport.TeleportManager;
import org.bukkit.entity.Player;

/**
 * 传送点列表 GUI 入口（薄封装，固定为 WARP 类型）。
 */
public final class WarpListGui {

    private WarpListGui() {
    }

    public static void open(SfpMain plugin, Player player, TeleportManager manager, int page) {
        TeleportListGui.open(plugin, player, manager, TeleportListHolder.ListType.WARP, page);
    }
}
