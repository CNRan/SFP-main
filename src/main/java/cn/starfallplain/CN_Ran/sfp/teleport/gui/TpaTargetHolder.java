package cn.starfallplain.CN_Ran.sfp.teleport.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 玩家传送目标选择界面的 Holder。
 * <p>
 * 与 {@link TeleportListHolder} 一样是 54 格、导航行 45~53，
 * 只是内容格的语义从「家 / 传送点名字」换成「在线玩家 UUID」。
 */
public class TpaTargetHolder implements InventoryHolder {

    public static final int SLOT_PREV = 45;
    public static final int SLOT_BACK = 48;   // 返回主菜单
    public static final int SLOT_INFO = 49;
    public static final int SLOT_NEXT = 53;

    private Inventory inventory;
    /** 0-based 页码 */
    private final int page;
    private final int pageSize;
    /** 当前页各格（0 ~ pageSize-1）对应的玩家 UUID；空格为 null */
    private final List<UUID> entries;

    public TpaTargetHolder(int page, int pageSize, List<UUID> entries) {
        this.page = page;
        this.pageSize = pageSize;
        this.entries = entries != null ? entries : new ArrayList<>();
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public int getPage() {
        return page;
    }

    public int getPageSize() {
        return pageSize;
    }

    /** 该格对应的玩家 UUID；无则返回 null */
    public UUID getEntryAt(int slot) {
        if (slot < 0 || slot >= entries.size()) return null;
        return entries.get(slot);
    }
}
