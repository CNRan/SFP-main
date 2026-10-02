package cn.starfallplain.CN_Ran.sfp.teleport.gui;

import cn.starfallplain.CN_Ran.sfp.StarfallplainMenu;
import cn.starfallplain.CN_Ran.sfp.teleport.TeleportManager;
import org.bukkit.entity.Player;

/**
 * 传送点列表 GUI 入口（薄封装，固定为 WARP 类型）。
 */
public final class WarpListGui {

    private WarpListGui() {
    }

    public static void open(StarfallplainMenu plugin, Player player, TeleportManager manager, int page) {
        TeleportListGui.open(plugin, player, manager, TeleportListHolder.ListType.WARP, page);
    }
}
