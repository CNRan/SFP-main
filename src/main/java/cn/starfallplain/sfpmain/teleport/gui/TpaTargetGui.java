package cn.starfallplain.sfpmain.teleport.gui;

import cn.starfallplain.sfpmain.SfpMain;
import cn.starfallplain.sfpmain.config.Messages;
import cn.starfallplain.sfpmain.util.SoundUtil;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家传送目标选择界面（从菜单进入）。
 * <p>
 * 内容区是**在线玩家的头颅**（不含自己）：
 * <ul>
 *   <li>左键 —— 请求传送到他身边（等效 {@code /tpa 他}）</li>
 *   <li>右键 —— 请求他传送到你身边（等效 {@code /tpahere 他}）</li>
 * </ul>
 * 导航行沿用垃圾桶 / 列表界面那一套：45 上一页 / 48 返回主菜单 / 49 信息纸 / 53 下一页。
 * 点击逻辑在 {@link TeleportGuiListener} 里处理。
 */
public final class TpaTargetGui {

    private TpaTargetGui() {
    }

    /**
     * 打开指定页。
     *
     * @param page 0-based 页码
     */
    public static void open(SfpMain plugin, Player viewer, int page) {
        List<UUID> targets = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getUniqueId().equals(viewer.getUniqueId())) targets.add(p.getUniqueId());
        }

        int pageSize = 45;
        int pageCount = Math.max(1, (targets.size() + pageSize - 1) / pageSize);
        if (page < 0) page = 0;
        if (page >= pageCount) page = pageCount - 1;

        List<UUID> pageEntries = new ArrayList<>();
        int start = page * pageSize;
        for (int i = 0; i < pageSize; i++) {
            int idx = start + i;
            pageEntries.add(idx < targets.size() ? targets.get(idx) : null);
        }

        TpaTargetHolder holder = new TpaTargetHolder(page, pageSize, pageEntries);

        Map<String, String> ph = new HashMap<>();
        ph.put("page", String.valueOf(page + 1));
        ph.put("pages", String.valueOf(pageCount));
        ph.put("total", String.valueOf(targets.size()));

        String titleRaw = plugin.getRawMessage("tpa.gui-title",
                "<!i><aqua>选择传送目标</aqua> <gray>- 第 {page}/{pages} 页</gray>");
        Component title = Messages.deserialize(Messages.apply(titleRaw, ph));

        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);

        // 内容格：玩家头颅
        for (int i = 0; i < pageSize; i++) {
            UUID id = pageEntries.get(i);
            if (id == null) continue;
            Player target = Bukkit.getPlayer(id);
            if (target == null) continue;   // 期间离线
            inv.setItem(i, createHead(plugin, target));
        }

        // 导航行
        ItemStack grayPane = createIcon(Material.GRAY_STAINED_GLASS_PANE, "<!i><dark_gray> </dark_gray>");
        for (int slot : new int[]{46, 47, 50, 51, 52}) {
            inv.setItem(slot, grayPane);
        }

        if (page > 0) {
            inv.setItem(TpaTargetHolder.SLOT_PREV, createIcon(Material.SPECTRAL_ARROW,
                    plugin.getRawMessage("teleport.prev", "<!i><yellow>上一页</yellow>")));
        } else {
            inv.setItem(TpaTargetHolder.SLOT_PREV, createIcon(Material.GRAY_STAINED_GLASS_PANE,
                    plugin.getRawMessage("teleport.first", "<!i><dark_gray>首页</dark_gray>")));
        }

        inv.setItem(TpaTargetHolder.SLOT_BACK, createIcon(Material.OAK_DOOR,
                plugin.getRawMessage("common.back-to-menu", "<!i><yellow>返回主菜单</yellow>")));

        inv.setItem(TpaTargetHolder.SLOT_INFO, createInfo(plugin, targets.size(), page + 1, pageCount, ph));

        if (page < pageCount - 1) {
            inv.setItem(TpaTargetHolder.SLOT_NEXT, createIcon(Material.SPECTRAL_ARROW,
                    plugin.getRawMessage("teleport.next", "<!i><yellow>下一页</yellow>")));
        } else {
            inv.setItem(TpaTargetHolder.SLOT_NEXT, createIcon(Material.GRAY_STAINED_GLASS_PANE,
                    plugin.getRawMessage("teleport.last", "<!i><dark_gray>末页</dark_gray>")));
        }

        viewer.openInventory(inv);
        SoundUtil.play(viewer, viewer.getLocation(),
                plugin.getConfigManager().teleport().getTeleportSound(), 0.4f, 1.4f);
    }

    /** 单个玩家头颅：左键 tpa / 右键 tpahere */
    private static ItemStack createHead(SfpMain plugin, Player target) {
        ItemStack item = ItemStack.of(Material.PLAYER_HEAD, 1);
        if (item.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(target);
            meta.displayName(Messages.deserialize("<!i><white>" + target.getName() + "</white>"));

            List<String> rawLore = plugin.getRawMessageList("tpa.gui-entry-lore", List.of(
                    "<!i><gray>所在世界： <white>{world}</white></gray>",
                    "<!i><green>左键</green><gray> 请求传送到他身边</gray>",
                    "<!i><red>右键</red><gray> 请求他传送到你身边</gray>"));
            List<Component> lore = new ArrayList<>(rawLore.size());
            for (String line : rawLore) {
                lore.add(Messages.deserialize(Messages.apply(line,
                        Map.of("world", target.getWorld().getName()))));
            }
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createInfo(SfpMain plugin, int total, int page, int pageCount,
                                        Map<String, String> ph) {
        ItemStack item = ItemStack.of(Material.PAPER, 1);
        if (item.getItemMeta() instanceof ItemMeta meta) {
            meta.displayName(Messages.deserialize(plugin.getRawMessage("tpa.gui-info-name",
                    "<!i><aqua>玩家传送</aqua>")));
            List<String> rawLore = plugin.getRawMessageList("tpa.gui-info-lore", List.of(
                    "<!i><gray>在线玩家： <white>{total}</white></gray>",
                    "<!i><gray>当前页： <white>{page}/{pages}</white></gray>",
                    "<!i><gray>左键 = 我去他那，右键 = 他来我这</gray>"));
            List<Component> lore = new ArrayList<>(rawLore.size());
            for (String line : rawLore) {
                lore.add(Messages.deserialize(Messages.apply(line, ph)));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createIcon(Material material, String rawName) {
        ItemStack item = ItemStack.of(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Messages.deserialize(rawName));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }
}
