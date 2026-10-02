package cn.starfallplain.CN_Ran.sfp.menu;

import cn.starfallplain.CN_Ran.sfp.StarfallplainMenu;
import cn.starfallplain.CN_Ran.sfp.config.ConfigManager;
import cn.starfallplain.CN_Ran.sfp.config.Messages;
import cn.starfallplain.CN_Ran.sfp.config.module.MenuConfig;
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

    public static Inventory createMainMenu(StarfallplainMenu plugin, Player player) {
        ConfigManager cm = plugin.getConfigManager();
        MenuConfig menuConfig = cm.menu();
        Messages messages = cm.messages();

        int size = menuConfig.getSize();
        Map<Integer, String> slotActions = new LinkedHashMap<>();
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

            // 记录点击动作
            if (button.getId().equals(menuConfig.getTrashBinButtonId())) {
                slotActions.put(slot, MenuHolder.ACTION_TRASHBIN);
            } else if (button.getId().equals(menuConfig.getHomeButtonId())) {
                slotActions.put(slot, MenuHolder.ACTION_HOME);
            } else if (button.getId().equals(menuConfig.getWarpButtonId())) {
                slotActions.put(slot, MenuHolder.ACTION_WARP);
            } else if (button.getId().equals(menuConfig.getBackButtonId())) {
                slotActions.put(slot, MenuHolder.ACTION_BACK);
            } else if (!button.getCommand().isBlank()) {
                slotActions.put(slot, MenuHolder.CMD_PREFIX + button.getCommand());
            }
        }

        return inv;
    }

    /** 按钮绑定的功能模块是否启用 */
    private static boolean isFeatureEnabled(String buttonId, MenuConfig menuConfig, ConfigManager cm) {
        if (buttonId.equals(menuConfig.getTrashBinButtonId())) return cm.trashBin().isEnabled();
        if (buttonId.equals(menuConfig.getHomeButtonId())) return cm.teleport().isEnabled() && cm.teleport().isHomeEnabled();
        if (buttonId.equals(menuConfig.getWarpButtonId())) return cm.teleport().isEnabled() && cm.teleport().isWarpEnabled();
        if (buttonId.equals(menuConfig.getBackButtonId())) return cm.teleport().isEnabled() && cm.teleport().isBackEnabled();
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
