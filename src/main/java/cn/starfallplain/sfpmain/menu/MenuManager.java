package cn.starfallplain.sfpmain.menu;

import cn.starfallplain.sfpmain.SfpMain;
import cn.starfallplain.sfpmain.config.ConfigManager;
import cn.starfallplain.sfpmain.config.Messages;
import cn.starfallplain.sfpmain.config.module.MenuConfig;
import cn.starfallplain.sfpmain.teleport.TeleportManager;
import cn.starfallplain.sfpmain.teleport.gui.HomeListGui;
import cn.starfallplain.sfpmain.teleport.gui.TpaTargetGui;
import cn.starfallplain.sfpmain.teleport.gui.WarpListGui;
import cn.starfallplain.sfpmain.trashbin.TrashBinCommand;
import cn.starfallplain.sfpmain.trashbin.TrashBinManager;
import cn.starfallplain.sfpmain.ui.DialogMenu;
import cn.starfallplain.sfpmain.ui.DialogTeleportList;
import cn.starfallplain.sfpmain.ui.DialogTpaTarget;
import cn.starfallplain.sfpmain.ui.UiMode;
import cn.starfallplain.sfpmain.ui.UiPreferenceStore;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜单界面构建器（完全由 menu.yml 驱动）。
 * <p>
 * 标题、行数、边框、按钮的槽位/材质/名称/动作全部读取配置；
 * 按钮的可见性同时受对应功能模块开关影响，功能关闭时入口自动隐藏。
 */
public class MenuManager {

    /** 标题从 menu.yml 读取，回退到 config.yml 的旧字段 */
    private static String resolveTitle(ConfigManager cm) {
        String title = cm.menu().getTitle();
        if (title == null || title.isBlank()) {
            title = cm.getPlugin().getConfig().getString("menu-title",
                    "<rainbow><b><i>星落平原 菜单</i></b></rainbow>");
        }
        return title;
    }

    // ==================== 主菜单 ====================

    /**
     * 打开主菜单，统一做「开关 + 打开权限」校验。
     * <p>
     * 供 /menu 命令与各子界面（垃圾桶 / 家列表 / 传送点列表）的「返回主菜单」按钮复用，
     * 保证所有入口行为一致。
     *
     * @return 是否成功打开了菜单（false 表示已向玩家发送了失败提示）
     */
    public static boolean openMainMenu(SfpMain plugin, Player player) {
        if (!canOpen(plugin, player)) return false;
        player.openInventory(createMainMenu(plugin, player));
        return true;
    }

    /** 主菜单的「开关 + 打开权限」校验；箱子 UI 与 dialogUI 共用，保证所有入口行为一致 */
    public static boolean canOpen(SfpMain plugin, Player player) {
        MenuConfig menuConfig = plugin.getConfigManager().menu();
        String permission = menuConfig.getOpenPermission();
        if (permission != null && !permission.isBlank() && !player.hasPermission(permission)) {
            player.sendMessage(plugin.getMessage("common.no-permission", "<red>你没有权限使用此命令！</red>"));
            return false;
        }
        if (!menuConfig.isEnabled()) {
            player.sendMessage(plugin.getMessage("common.feature-disabled", "<red>该功能当前未启用。</red>"));
            return false;
        }
        return true;
    }

    /** 按玩家偏好打开主菜单（dialogUI 或箱子），供 /menu 与各子界面的「返回」共用 */
    public static void openMenuByPreference(SfpMain plugin, Player player) {
        UiPreferenceStore store = plugin.getUiPreferenceStore();
        UiMode mode = store != null ? store.get(player.getUniqueId()) : UiMode.DIALOG;
        if (mode == UiMode.DIALOG) {
            DialogMenu.open(plugin, player);
        } else {
            openMainMenu(plugin, player);
        }
    }

    /**
     * 解析 menu.yml 得到「槽位 → 动作」映射。
     * <p>
     * 与 {@link #createMainMenu} 用的是同一套判定（模块开关、按钮开关、槽位范围），
     * 因此 /sfp test 查出来的绑定结果就是玩家实际点击的结果，不会出现「自检说绑上了、实际没绑」。
     */
    public static Map<Integer, String> resolveSlotActions(MenuConfig menuConfig, ConfigManager cm) {
        Map<Integer, String> slotActions = new LinkedHashMap<>();
        int size = menuConfig.getSize();

        for (MenuConfig.Button button : menuConfig.getButtons().values()) {
            if (!isFeatureEnabled(button.getId(), menuConfig, cm)) continue;
            if (!button.isVisible()) continue;

            int slot = button.getSlot();
            if (slot < 0 || slot >= size) continue;

            String action = resolveAction(button, menuConfig);
            if (action != null) {
                slotActions.put(slot, action);
            }
        }
        return slotActions;
    }

    /** 单个按钮对应的动作串；返回 null 表示该按钮没有任何绑定（点了没反应） */
    public static String resolveAction(MenuConfig.Button button, MenuConfig menuConfig) {
        if (button.getId().equals(menuConfig.getTrashBinButtonId())) return MenuHolder.ACTION_TRASHBIN;
        if (button.getId().equals(menuConfig.getHomeButtonId())) return MenuHolder.ACTION_HOME;
        if (button.getId().equals(menuConfig.getWarpButtonId())) return MenuHolder.ACTION_WARP;
        if (button.getId().equals(menuConfig.getBackButtonId())) return MenuHolder.ACTION_BACK;
        if (button.getId().equals(menuConfig.getTpaButtonId())) return MenuHolder.ACTION_TPA;
        if (button.getId().equals(menuConfig.getUiToggleButtonId())) return MenuHolder.ACTION_TOGGLE_UI;
        if (!button.getCommand().isBlank()) return MenuHolder.CMD_PREFIX + button.getCommand();
        return null;
    }

    /** 返回所有「可见且有动作」的按钮（按 menu.yml 顺序），供 dialogUI 渲染 */
    public static List<MenuConfig.Button> activeButtons(MenuConfig menuConfig, ConfigManager cm) {
        List<MenuConfig.Button> result = new ArrayList<>();
        int size = menuConfig.getSize();
        for (MenuConfig.Button button : menuConfig.getButtons().values()) {
            if (!isFeatureEnabled(button.getId(), menuConfig, cm)) continue;
            if (!button.isVisible()) continue;
            if (button.getSlot() < 0 || button.getSlot() >= size) continue;
            if (resolveAction(button, menuConfig) == null) continue;
            result.add(button);
        }
        return result;
    }

    /**
     * 执行一个按钮动作。箱子 UI 与 dialogUI **共用同一套逻辑**，保证点击结果一致。
     * <p>
     * 调用方（箱子 UI 的点击处理 / dialogUI 的回调）负责在需要时先关闭自己的界面，
     * 这里只负责「把动作做出来」。
     */
    public static void runAction(SfpMain plugin, Player player, String action) {
        if (action == null) return;
        if (action.startsWith(MenuHolder.CMD_PREFIX)) {
            String command = action.substring(MenuHolder.CMD_PREFIX.length());
            Bukkit.getScheduler().runTask(plugin, () -> player.performCommand(command));
            return;
        }
        switch (action) {
            case MenuHolder.ACTION_TRASHBIN -> openTrashBin(plugin, player);
            case MenuHolder.ACTION_HOME -> openHomeList(plugin, player);
            case MenuHolder.ACTION_WARP -> openWarpList(plugin, player);
            case MenuHolder.ACTION_BACK ->
                    Bukkit.getScheduler().runTask(plugin, () -> player.performCommand("back"));
            case MenuHolder.ACTION_TPA -> openTpaTarget(plugin, player);
            case MenuHolder.ACTION_TOGGLE_UI -> toggleUi(plugin, player);
            default -> { /* 未知动作：忽略 */ }
        }
    }

    private static void openTrashBin(SfpMain plugin, Player player) {
        TrashBinManager manager = plugin.getTrashBinManager();
        if (manager == null) {
            player.sendMessage(plugin.getConfigManager().messages().component("trashbin.disabled",
                    "<red>垃圾桶系统未启用。</red>"));
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () ->
                TrashBinCommand.openPage(plugin, player, manager, 0));
    }

    private static void openHomeList(SfpMain plugin, Player player) {
        TeleportManager manager = plugin.getTeleportManager();
        if (manager == null) {
            player.sendMessage(plugin.getConfigManager().messages().component("common.feature-disabled",
                    "<red>该功能当前未启用。</red>"));
            return;
        }
        UiPreferenceStore store = plugin.getUiPreferenceStore();
        UiMode mode = store != null ? store.get(player.getUniqueId()) : UiMode.DIALOG;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (mode == UiMode.DIALOG) {
                DialogTeleportList.open(plugin, player, true, 0);
            } else {
                HomeListGui.open(plugin, player, manager, 0);
            }
        });
    }

    private static void openWarpList(SfpMain plugin, Player player) {
        TeleportManager manager = plugin.getTeleportManager();
        if (manager == null) {
            player.sendMessage(plugin.getConfigManager().messages().component("common.feature-disabled",
                    "<red>该功能当前未启用。</red>"));
            return;
        }
        UiPreferenceStore store = plugin.getUiPreferenceStore();
        UiMode mode = store != null ? store.get(player.getUniqueId()) : UiMode.DIALOG;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (mode == UiMode.DIALOG) {
                DialogTeleportList.open(plugin, player, false, 0);
            } else {
                WarpListGui.open(plugin, player, manager, 0);
            }
        });
    }

    private static void openTpaTarget(SfpMain plugin, Player player) {
        if (plugin.getTpaManager() == null) {
            player.sendMessage(plugin.getConfigManager().messages().component("common.feature-disabled",
                    "<red>该功能当前未启用。</red>"));
            return;
        }
        UiPreferenceStore store = plugin.getUiPreferenceStore();
        UiMode mode = store != null ? store.get(player.getUniqueId()) : UiMode.DIALOG;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (mode == UiMode.DIALOG) {
                DialogTpaTarget.open(plugin, player, 0);
            } else {
                TpaTargetGui.open(plugin, player, 0);
            }
        });
    }

    /** 切换界面样式，并按新偏好重开菜单 */
    private static void toggleUi(SfpMain plugin, Player player) {
        UiPreferenceStore store = plugin.getUiPreferenceStore();
        if (store == null) return;
        UiMode next = store.get(player.getUniqueId()).toggle();
        store.set(player.getUniqueId(), next);
        player.sendMessage(plugin.getConfigManager().messages().component(
                next == UiMode.DIALOG ? "menuui.toggled-dialog" : "menuui.toggled-box",
                next == UiMode.DIALOG
                        ? "<green>界面已切换为 dialogUI（弹窗界面）。</green>"
                        : "<green>界面已切换为箱子界面。</green>"));
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (next == UiMode.DIALOG) {
                DialogMenu.open(plugin, player);
            } else {
                openMainMenu(plugin, player);
            }
        });
    }

    public static Inventory createMainMenu(SfpMain plugin, Player player) {
        ConfigManager cm = plugin.getConfigManager();
        MenuConfig menuConfig = cm.menu();
        Messages messages = cm.messages();

        int size = menuConfig.getSize();
        Map<Integer, String> slotActions = resolveSlotActions(menuConfig, cm);
        MenuHolder holder = new MenuHolder(MenuHolder.MenuType.MAIN, slotActions, menuConfig);

        Component title = Messages.deserialize(resolveTitle(cm));
        Inventory inv = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inv);
        applyBorder(inv, menuConfig);

        // 顶栏玩家头颅（配置可关）
        if (menuConfig.isShowPlayerHead() && menuConfig.getPlayerHeadSlot() < size) {
            inv.setItem(menuConfig.getPlayerHeadSlot(), createPlayerHead(player,
                    messages.raw("menu.head-name", "<!i><yellow>{player}</yellow>")
                            .replace("{player}", player.getName())));
        }

        for (MenuConfig.Button button : menuConfig.getButtons().values()) {
            if (!isFeatureEnabled(button.getId(), menuConfig, cm)) continue;
            if (!button.isVisible()) continue;

            int slot = button.getSlot();
            if (slot < 0 || slot >= size) {
                plugin.getLogger().warning("menu.yml 中按钮 '" + button.getId()
                        + "' 的槽位 " + slot + " 超出菜单范围（0~" + (size - 1) + "），已忽略。");
                continue;
            }
            inv.setItem(slot, createButton(button));
        }

        return inv;
    }

    /** 按钮绑定的功能模块是否启用 */
    private static boolean isFeatureEnabled(String buttonId, MenuConfig menuConfig, ConfigManager cm) {
        if (buttonId.equals(menuConfig.getTrashBinButtonId())) return cm.trashBin().isEnabled();
        if (buttonId.equals(menuConfig.getHomeButtonId())) return cm.teleport().isEnabled() && cm.teleport().isHomeEnabled();
        if (buttonId.equals(menuConfig.getWarpButtonId())) return cm.teleport().isEnabled() && cm.teleport().isWarpEnabled();
        if (buttonId.equals(menuConfig.getBackButtonId())) return cm.teleport().isEnabled() && cm.teleport().isBackEnabled();
        if (buttonId.equals(menuConfig.getTpaButtonId())) return cm.teleport().isEnabled() && cm.teleport().isTpaEnabled();
        return true;
    }

    private static ItemStack createButton(MenuConfig.Button button) {
        ItemStack item = ItemStack.of(button.getMaterial(), 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Messages.deserialize("<!i>" + button.getName()));
            if (button.getLore() != null && !button.getLore().isEmpty()) {
                List<Component> lore = new ArrayList<>();
                for (String line : button.getLore()) {
                    lore.add(Messages.deserialize(line));
                }
                meta.lore(lore);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    // ==================== 工具方法 ====================

    private static void applyBorder(Inventory inv, MenuConfig menuConfig) {
        if (!menuConfig.isFillBorder()) return;
        int size = inv.getSize();
        int rows = size / 9;
        List<Material> materials = menuConfig.getBorderMaterials();
        int index = 0;
        for (int slot = 0; slot < size; slot++) {
            int row = slot / 9;
            int col = slot % 9;
            boolean border = row == 0 || row == rows - 1 || col == 0 || col == 8;
            if (border && inv.getItem(slot) == null) {
                Material material = materials.get(index % materials.size());
                inv.setItem(slot, ItemStack.of(material, 1));
                index++;
            }
        }
    }

    private static ItemStack createPlayerHead(Player player, String rawName) {
        ItemStack head = ItemStack.of(Material.PLAYER_HEAD, 1);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(player);
            meta.displayName(Messages.deserialize(rawName));
            head.setItemMeta(meta);
        }
        return head;
    }
}
