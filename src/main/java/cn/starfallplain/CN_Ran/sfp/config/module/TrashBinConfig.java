package cn.starfallplain.CN_Ran.sfp.config.module;

import cn.starfallplain.CN_Ran.sfp.config.AbstractConfig;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 垃圾桶配置（trashbin.yml）。
 */
public final class TrashBinConfig extends AbstractConfig {

    private boolean enabled;

    // 每页格数（固定 45，可调小以留更多导航格）
    private int pageSize;
    private int maxItems;
    private boolean global;
    private boolean saveImmediately;

    private String openSound;
    private String takeSound;

    public TrashBinConfig(JavaPlugin plugin) {
        super(plugin, "trashbin.yml");
    }

    @Override
    protected void onLoaded() {
        enabled = getBoolean("enabled", true);
        pageSize = getInt("page-size", 45);
        maxItems = getInt("max-items", 486);
        global = getBoolean("global", true);
        saveImmediately = getBoolean("save-immediately", true);
        openSound = getString("sounds.open", "");
        takeSound = getString("sounds.take", "ENTITY_ITEM_PICKUP");
    }

    public boolean isEnabled() { return enabled; }

    public int getPageSize() { return pageSize; }

    public int getMaxItems() { return maxItems; }

    public boolean isGlobal() { return global; }

    public boolean isSaveImmediately() { return saveImmediately; }

    public String getOpenSound() { return openSound; }

    public String getTakeSound() { return takeSound; }
}
