package cn.starfallplain.CN_Ran.sfp.teleport.gui;

import cn.starfallplain.CN_Ran.sfp.StarfallplainMenu;
import cn.starfallplain.CN_Ran.sfp.config.module.TeleportConfig;
import cn.starfallplain.CN_Ran.sfp.menu.MenuManager;
import cn.starfallplain.CN_Ran.sfp.teleport.TeleportManager;
import cn.starfallplain.CN_Ran.sfp.teleport.TpaManager;
import cn.starfallplain.CN_Ran.sfp.teleport.db.StoredLocation;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 传送列表界面交互监听器：
 * <ul>
 *   <li>左键条目 —— 传送</li>
 *   <li>右键条目 —— 删除（家直接删；传送点需 delwarp 权限）</li>
 *   <li>翻页按钮 —— 上下页</li>
 *   <li>禁止一切物品拖放</li>
 * </ul>
 */
public class TeleportGuiListener implements Listener {

    private final StarfallplainMenu plugin;
    private final TeleportManager manager;

    public TeleportGuiListener(StarfallplainMenu plugin, TeleportManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        // 玩家传送目标界面（内容格是玩家头颅，语义与列表不同，单独处理）
        if (event.getInventory().getHolder() instanceof TpaTargetHolder tpaHolder) {
            event.setCancelled(true);
            handleTpaTarget(event, tpaHolder);
            return;
        }

        if (!(event.getInventory().getHolder() instanceof TeleportListHolder holder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        TeleportConfig config = manager.getConfig();

        // 翻页
        if (slot == TeleportListHolder.SLOT_PREV) {
            if (holder.getPage() > 0) {
                TeleportListGui.open(plugin, player, manager, holder.getListType(), holder.getPage() - 1);
            }
            return;
        }
        if (slot == TeleportListHolder.SLOT_NEXT) {
            int next = holder.getPage() + 1;
            // 越界时 open 内部会自动夹紧，这里仅在有更多页时才翻
            TeleportListGui.open(plugin, player, manager, holder.getListType(), next);
            return;
        }
        if (slot == TeleportListHolder.SLOT_BACK) {
            // 返回主菜单：先关掉本界面，延迟一 tick 再打开，避免在点击事件里直接换 GUI
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) MenuManager.openMainMenu(plugin, player);
            });
            return;
        }
        if (slot == TeleportListHolder.SLOT_INFO) return;
        if (slot >= holder.getPageSize()) return; // 导航区灰板

        String name = holder.getEntryAt(slot);
        if (name == null) return;

        boolean rightClick = event.getClick() == ClickType.RIGHT
                || event.getClick() == ClickType.SHIFT_RIGHT;

        if (holder.getListType() == TeleportListHolder.ListType.HOME) {
            if (rightClick) {
                handleHomeDelete(player, name);
            } else {
                handleHomeTeleport(player, name);
            }
        } else {
            if (rightClick) {
                handleWarpDelete(player, name);
            } else {
                handleWarpTeleport(player, name);
            }
        }
    }

    // ==================== 家 ====================

    private void handleHomeTeleport(Player player, String name) {
        TeleportConfig config = manager.getConfig();
        if (!config.isHomeEnabled()) {
            player.sendMessage(plugin.getMessage("common.feature-disabled", "<red>该功能当前未启用。</red>"));
            return;
        }
        StoredLocation target = manager.getHomeStore().get(player.getUniqueId(), name);
        if (target == null) {
            Map<String, String> ph = new HashMap<>();
            ph.put("name", name);
            plugin.getConfigManager().messages().send(player, "home.not-found",
                    "<red>不存在名为「{name}」的家。</red>", ph);
            return;
        }
        if (!target.worldExists()) {
            player.sendMessage(plugin.getMessage("teleport.world-missing",
                    "<red>目标世界不存在或已被卸载。</red>"));
            return;
        }
        long cd = manager.getCooldownRemaining(player, config.getHomeTeleportCooldownSeconds());
        if (cd > 0) {
            sendCooldown(player, cd);
            return;
        }
        if (manager.teleport(player, target, true)) {
            manager.applyTeleportCooldown(player, config.getHomeTeleportCooldownSeconds());
            Map<String, String> ph = new HashMap<>();
            ph.put("name", name);
            plugin.getConfigManager().messages().send(player, "home.teleport-success",
                    "<green>已传送到家「{name}」。</green>", ph);
        } else {
            player.sendMessage(plugin.getMessage("teleport.failed", "<red>传送失败。</red>"));
        }
    }

    private void handleHomeDelete(Player player, String name) {
        if (!manager.getHomeStore().delete(player.getUniqueId(), name)) {
            Map<String, String> ph = new HashMap<>();
            ph.put("name", name);
            plugin.getConfigManager().messages().send(player, "home.not-found",
                    "<red>不存在名为「{name}」的家。</red>", ph);
            return;
        }
        Map<String, String> ph = new HashMap<>();
        ph.put("name", name);
        plugin.getConfigManager().messages().send(player, "home.del-success",
                "<green>已删除家「{name}」。</green>", ph);
        // 刷新列表（回到首页）
        TeleportListGui.open(plugin, player, manager, TeleportListHolder.ListType.HOME, 0);
    }

    // ==================== 传送点 ====================

    private void handleWarpTeleport(Player player, String name) {
        TeleportConfig config = manager.getConfig();
        if (!config.isWarpEnabled()) {
            player.sendMessage(plugin.getMessage("common.feature-disabled", "<red>该功能当前未启用。</red>"));
            return;
        }
        StoredLocation target = manager.getWarpStore().get(name);
        if (target == null) {
            Map<String, String> ph = new HashMap<>();
            ph.put("name", name);
            plugin.getConfigManager().messages().send(player, "warp.not-found",
                    "<red>不存在名为「{name}」的传送点。</red>", ph);
            return;
        }
        if (!target.worldExists()) {
            player.sendMessage(plugin.getMessage("teleport.world-missing",
                    "<red>目标世界不存在或已被卸载。</red>"));
            return;
        }
        long cd = manager.getCooldownRemaining(player, config.getWarpTeleportCooldownSeconds());
        if (cd > 0) {
            sendCooldown(player, cd);
            return;
        }
        if (manager.teleport(player, target, true)) {
            manager.applyTeleportCooldown(player, config.getWarpTeleportCooldownSeconds());
            Map<String, String> ph = new HashMap<>();
            ph.put("name", name);
            plugin.getConfigManager().messages().send(player, "warp.teleport-success",
                    "<green>已传送到「{name}」。</green>", ph);
        } else {
            player.sendMessage(plugin.getMessage("teleport.failed", "<red>传送失败。</red>"));
        }
    }

    private void handleWarpDelete(Player player, String name) {
        String permission = manager.getConfig().getWarpDeletePermission();
        if (permission != null && !permission.isBlank() && !player.hasPermission(permission)) {
            player.sendMessage(plugin.getMessage("common.no-permission", "<red>你没有权限使用此命令！</red>"));
            return;
        }
        if (!manager.getWarpStore().delete(name)) {
            Map<String, String> ph = new HashMap<>();
            ph.put("name", name);
            plugin.getConfigManager().messages().send(player, "warp.not-found",
                    "<red>不存在名为「{name}」的传送点。</red>", ph);
            return;
        }
        Map<String, String> ph = new HashMap<>();
        ph.put("name", name);
        plugin.getConfigManager().messages().send(player, "warp.del-success",
                "<green>已删除传送点「{name}」。</green>", ph);
        TeleportListGui.open(plugin, player, manager, TeleportListHolder.ListType.WARP, 0);
    }

    // ==================== 工具 ====================

    private void sendCooldown(Player player, long seconds) {
        Map<String, String> ph = new HashMap<>();
        ph.put("seconds", String.valueOf(seconds));
        plugin.getConfigManager().messages().send(player, "teleport.cooldown",
                "<red>传送冷却中，请等待 {seconds} 秒。</red>", ph);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof TeleportListHolder
                || event.getInventory().getHolder() instanceof TpaTargetHolder) {
            event.setCancelled(true);
        }
    }

    // ==================== 玩家传送目标界面 ====================

    /** 左键 = 请求传送到该玩家身边，右键 = 请求该玩家传送到自己身边 */
    private void handleTpaTarget(InventoryClickEvent event, TpaTargetHolder holder) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        if (slot == TpaTargetHolder.SLOT_PREV) {
            if (holder.getPage() > 0) {
                TpaTargetGui.open(plugin, player, holder.getPage() - 1);
            }
            return;
        }
        if (slot == TpaTargetHolder.SLOT_NEXT) {
            // 越界时 open 内部会夹紧
            TpaTargetGui.open(plugin, player, holder.getPage() + 1);
            return;
        }
        if (slot == TpaTargetHolder.SLOT_BACK) {
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) MenuManager.openMainMenu(plugin, player);
            });
            return;
        }
        if (slot == TpaTargetHolder.SLOT_INFO) return;
        if (slot >= holder.getPageSize()) return;   // 导航区灰板

        TpaManager tpaManager = plugin.getTpaManager();
        if (tpaManager == null) return;

        UUID targetId = holder.getEntryAt(slot);
        if (targetId == null) return;
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            player.sendMessage(plugin.getMessage("tpa.target-offline",
                    "<red>该玩家已离线。</red>"));
            TpaTargetGui.open(plugin, player, holder.getPage());   // 刷新列表
            return;
        }

        boolean rightClick = event.getClick() == ClickType.RIGHT
                || event.getClick() == ClickType.SHIFT_RIGHT;
        if (tpaManager.request(player, target, rightClick ? TpaManager.Type.HERE : TpaManager.Type.TO)) {
            player.closeInventory();   // 发起成功就回到游戏，等对方在弹窗/聊天里响应
        }
    }
}
