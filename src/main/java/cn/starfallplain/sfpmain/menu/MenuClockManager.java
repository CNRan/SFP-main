package cn.starfallplain.sfpmain.menu;

import cn.starfallplain.sfpmain.SfpMain;
import cn.starfallplain.sfpmain.config.ConfigManager;
import cn.starfallplain.sfpmain.config.Messages;
import cn.starfallplain.sfpmain.config.module.MenuConfig;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * 菜单钟：把「泥土」之类的材料放进合成格，合出一个手持右键即可打开菜单的钟形物品。
 * <p>
 * 物品与配方全部由 menu.yml 的 {@code menu-clock} 段驱动：
 * 材质、显示名、描述、附魔光效、触发方式、配方材料都可调。
 * <p>
 * 识别方式用 {@link org.bukkit.persistence.PersistentDataContainer}（PDC）打标记，
 * 而不是比对材质或显示名 —— 这样即便之后改了材质/名称，玩家手里已有的菜单钟仍然有效，
 * 也不会误认普通钟。
 */
public final class MenuClockManager {

    private final SfpMain plugin;
    private final MenuConfig config;

    /** 合成配方键（注册 / 注销用） */
    private final NamespacedKey recipeKey;
    /** 物品标记键（识别「这是菜单钟」） */
    private final NamespacedKey itemKey;

    public MenuClockManager(SfpMain plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.config = configManager.menu();
        this.recipeKey = new NamespacedKey(plugin, "menu_clock");
        this.itemKey = new NamespacedKey(plugin, "menu_clock_item");
    }

    public MenuConfig getConfig() {
        return config;
    }

    // ==================== 配方 ====================

    /**
     * 注册合成配方。先移除同名旧配方再添加，保证 /reload 或重复调用时不会报「配方已存在」。
     */
    public void registerRecipe() {
        if (!config.isMenuClockRecipeEnabled()) {
            plugin.getLogger().info("菜单钟合成配方：已按配置关闭（仍可通过其它方式获得菜单钟物品）。");
            return;
        }
        // 幂等：先清掉可能残留的同名配方
        Bukkit.removeRecipe(recipeKey);

        ShapelessRecipe recipe = new ShapelessRecipe(recipeKey, createItem());
        for (Material ingredient : config.getMenuClockIngredients()) {
            recipe.addIngredient(ingredient);
        }
        boolean ok = Bukkit.addRecipe(recipe);
        if (ok) {
            plugin.getLogger().info("菜单钟合成配方已注册（" + describeIngredients() + " → 菜单钟）。");
        } else {
            plugin.getLogger().warning("菜单钟合成配方注册失败（可能与其他插件的配方键冲突）。");
        }
    }

    /** 注销合成配方（插件卸载时调用；否则会留下一个合出来但没反应的物品） */
    public void unregisterRecipe() {
        Bukkit.removeRecipe(recipeKey);
    }

    private String describeIngredients() {
        StringJoiner joiner = new StringJoiner(" + ");
        for (Material material : config.getMenuClockIngredients()) {
            joiner.add(material.name());
        }
        return joiner.toString();
    }

    // ==================== 物品 ====================

    /**
     * 构建一个菜单钟物品（带 PDC 标记与可选附魔光效）。
     */
    public ItemStack createItem() {
        ItemStack item = new ItemStack(config.getMenuClockMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(Messages.deserialize(config.getMenuClockName()));

        List<String> loreLines = config.getMenuClockLore();
        if (loreLines != null && !loreLines.isEmpty()) {
            List<Component> lore = new ArrayList<>(loreLines.size());
            for (String line : loreLines) {
                lore.add(Messages.deserialize(line));
            }
            meta.lore(lore);
        }

        // 附魔光效（不消耗真实附魔，仅视觉）
        if (config.isMenuClockGlint()) {
            meta.setEnchantmentGlintOverride(true);
        }

        // 打上标记，供右键识别
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 判断物品是否为菜单钟。
     * 只认 PDC 标记，不比对材质，避免改动材质配置后老物品失效。
     */
    public boolean isMenuClock(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null
                && meta.getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }
}
