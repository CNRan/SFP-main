package cn.starfallplain.sfpmain.trashbin;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * 垃圾桶 GUI 的 Holder，用于在 InventoryClickEvent 中识别垃圾桶界面。
 * 保存当前页码与页容量，便于翻页和取出物品时定位。
 */
public class TrashBinHolder implements InventoryHolder {

    public static final int SLOT_PREV = 45;   // 上一页
    public static final int SLOT_BACK = 48;   // 返回主菜单
    public static final int SLOT_INFO = 49;   // 页码信息
    public static final int SLOT_NEXT = 53;   // 下一页

    private Inventory inventory;
    private final int page; // 0-based
    private final int pageSize;

    public TrashBinHolder(int page, int pageSize) {
        this.page = page;
        this.pageSize = pageSize;
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
}
