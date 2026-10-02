package cn.starfallplain.CN_Ran.sfp.menu;

import cn.starfallplain.CN_Ran.sfp.StarfallplainMenu;
import cn.starfallplain.CN_Ran.sfp.config.ConfigManager;
import cn.starfallplain.CN_Ran.sfp.teleport.TeleportManager;
import cn.starfallplain.CN_Ran.sfp.teleport.gui.HomeListGui;
import cn.starfallplain.CN_Ran.sfp.teleport.gui.TpaTargetGui;
import cn.starfallplain.CN_Ran.sfp.teleport.gui.WarpListGui;
import cn.starfallplain.CN_Ran.sfp.trashbin.TrashBinCommand;
import cn.starfallplain.CN_Ran.sfp.trashbin.TrashBinManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * 菜单点击处理。点击行为由 MenuManager 写入的槽位动作映射驱动，
 * 新增/调整按钮只需改 menu.yml，无需改本类。
 */
public class MenuListener implements Listener {

    private final StarfallplainMenu plugin;
    private final ConfigManager configManager;

    public MenuListener(StarfallplainMenu plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
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

        if (action.startsWith(MenuHolder.CMD_PREFIX)) {
            String command = action.substring(MenuHolder.CMD_PREFIX.length());
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> player.performCommand(command));
            return;
        }

        if (MenuHolder.ACTION_TRASHBIN.equals(action)) {
            handleTrashBin(player);
            return;
        }

        if (MenuHolder.ACTION_HOME.equals(action)) {
            handleTeleportList(player, true);
            return;
        }

        if (MenuHolder.ACTION_WARP.equals(action)) {
            handleTeleportList(player, false);
            return;
        }

        if (MenuHolder.ACTION_BACK.equals(action)) {
            handleBack(player);
            return;
        }

        if (MenuHolder.ACTION_TPA.equals(action)) {
            handleTpaTarget(player);
            return;
        }

        if (MenuHolder.ACTION_TOGGLE_UI.equals(action)) {
            handleUiToggle(player);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    private void handleTrashBin(Player player) {
        TrashBinManager manager = plugin.getTrashBinManager();
        if (manager == null) {
            player.sendMessage(configManager.messages().component("trashbin.disabled",
                    "<red>垃圾桶系统未启用。</red>"));
            return;
        }
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () ->
                TrashBinCommand.openPage(plugin, player, manager, 0));
    }

    /** 打开家列表 / 传送点列表 */
    private void handleTeleportList(Player player, boolean home) {
        TeleportManager manager = plugin.getTeleportManager();
        if (manager == null) {
            player.sendMessage(configManager.messages().component("common.feature-disabled",
                    "<red>该功能当前未启用。</red>"));
            return;
        }
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (home) {
                HomeListGui.open(plugin, player, manager, 0);
            } else {
                WarpListGui.open(plugin, player, manager, 0);
            }
        });
    }

    /** 菜单里的返回按钮：等效于执行 /back */
    private void handleBack(Player player) {
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            player.performCommand("back");
        });
    }

    /** 菜单里的玩家传送按钮：打开「选择传送目标」界面 */
    private void handleTpaTarget(Player player) {
        if (plugin.getTpaManager() == null) {
            player.sendMessage(configManager.messages().component("common.feature-disabled",
                    "<red>该功能当前未启用。</red>"));
            return;
        }
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) TpaTargetGui.open(plugin, player, 0);
        });
    }

    /** 菜单里的界面样式切换按钮：切换偏好后重新打开菜单 */
    private void handleUiToggle(Player player) {
        cn.starfallplain.CN_Ran.sfp.ui.UiPreferenceStore store = plugin.getUiPreferenceStore();
        if (store == null) return;
        cn.starfallplain.CN_Ran.sfp.ui.UiMode next = store.get(player.getUniqueId()).toggle();
        store.set(player.getUniqueId(), next);
        player.sendMessage(configManager.messages().component(
                next == cn.starfallplain.CN_Ran.sfp.ui.UiMode.DIALOG ? "menuui.toggled-dialog" : "menuui.toggled-box",
                next == cn.starfallplain.CN_Ran.sfp.ui.UiMode.DIALOG
                        ? "<green>界面已切换为 dialogUI（弹窗界面）。</green>"
                        : "<green>界面已切换为箱子界面。</green>"));
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) MenuManager.openMainMenu(plugin, player);
        });
    }
}
