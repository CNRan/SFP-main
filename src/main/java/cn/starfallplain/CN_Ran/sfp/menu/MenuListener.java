package cn.starfallplain.CN_Ran.sfp.menu;

import cn.starfallplain.CN_Ran.sfp.StarfallplainMenu;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * 箱子 UI 的菜单点击处理。
 * <p>
 * 点击行为由 MenuManager 写入的槽位动作映射驱动，实际执行统一走
 * {@link MenuManager#runAction} —— 与 dialogUI 的回调**共用同一套动作逻辑**，
 * 因此新增/调整按钮只需改 menu.yml，两种界面自动保持一致。
 */
public class MenuListener implements Listener {

    private final StarfallplainMenu plugin;

    public MenuListener(StarfallplainMenu plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        String action = holder.getSlotActions().get(slot);
        if (action == null) return;

        // 箱子 UI：先关掉菜单，再执行动作（打开子界面 / 执行命令 / 切换样式等）
        player.closeInventory();
        MenuManager.runAction(plugin, player, action);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }
}
