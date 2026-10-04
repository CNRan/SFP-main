package cn.starfallplain.sfpmain.teleport.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 传送列表 GUI 的 Holder（家列表 / 传送点列表共用）。
 * <p>
 * 保存：
 * <ul>
 *   <li>列表类型（家 / 传送点）</li>
 *   <li>当前页码与每页容量</li>
 *   <li>当前页各格对应的名称（用于点击时定位）</li>
 * </ul>
 */
public class TeleportListHolder implements InventoryHolder {

    public enum ListType { HOME, WARP }

    public static final int SLOT_PREV = 45;
    public static final int SLOT_BACK = 48;   // 返回主菜单
    public static final int SLOT_INFO = 49;
    public static final int SLOT_NEXT = 53;

    private Inventory inventory;
    private final ListType listType;
    private final int page;       // 0-based
    private final int pageSize;
    /** 当前页各格（0 ~ pageSize-1）对应的条目名称；无条目为 null */
    private final List<String> entryNames;

    public TeleportListHolder(ListType listType, int page, int pageSize, List<String> entryNames) {
        this.listType = listType;
        this.page = page;
        this.pageSize = pageSize;
        this.entryNames = entryNames != null ? entryNames : new ArrayList<>();
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public ListType getListType() {
        return listType;
    }

    public int getPage() {
        return page;
    }

    public int getPageSize() {
        return pageSize;
    }

    /** 该格对应的条目名称；无则返回 null */
    public String getEntryAt(int slot) {
        if (slot < 0 || slot >= entryNames.size()) return null;
        return entryNames.get(slot);
    }
}
