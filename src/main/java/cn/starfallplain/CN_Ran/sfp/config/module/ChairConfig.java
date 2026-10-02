package cn.starfallplain.CN_Ran.sfp.config.module;

import cn.starfallplain.CN_Ran.sfp.config.AbstractConfig;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * 椅子配置（chair.yml）。
 */
public final class ChairConfig extends AbstractConfig {

    private boolean enabled;

    private String leftKeyword;
    private String rightKeyword;
    private boolean caseSensitive;
    private List<String> allowedWoods;

    private double seatHeightOffset;
    private boolean standOnBreak;
    private boolean standOnTeleport;
    private boolean ejectOnReclick;

    public ChairConfig(JavaPlugin plugin) {
        super(plugin, "chair.yml");
    }

    @Override
    protected void onLoaded() {
        enabled = getBoolean("enabled", true);
        leftKeyword = getString("sign.left-keyword", "chair-l");
        rightKeyword = getString("sign.right-keyword", "chair-r");
        caseSensitive = getBoolean("sign.case-sensitive", false);
        allowedWoods = getStringList("sign.allowed-woods");
        if (allowedWoods.isEmpty()) {
            allowedWoods = List.of("OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA", "DARK_OAK",
                    "MANGROVE", "CHERRY", "BAMBOO", "CRIMSON", "WARPED");
        }
        seatHeightOffset = getDouble("seat-height-offset", 0.4);
        standOnBreak = getBoolean("stand-on.break", true);
        standOnTeleport = getBoolean("stand-on.teleport", true);
        ejectOnReclick = getBoolean("stand-on.reclick", true);
    }

    public boolean isEnabled() { return enabled; }

    public String getLeftKeyword() { return leftKeyword; }

    public String getRightKeyword() { return rightKeyword; }

    public boolean isCaseSensitive() { return caseSensitive; }

    public List<String> getAllowedWoods() { return allowedWoods; }

    public double getSeatHeightOffset() { return seatHeightOffset; }

    public boolean isStandOnBreak() { return standOnBreak; }

    public boolean isStandOnTeleport() { return standOnTeleport; }

    public boolean isEjectOnReclick() { return ejectOnReclick; }

    /** 关键字匹配（按配置决定是否区分大小写） */
    public boolean matchesLeft(String line) {
        if (line == null) return false;
        return caseSensitive ? line.equals(leftKeyword) : line.equalsIgnoreCase(leftKeyword);
    }

    public boolean matchesRight(String line) {
        if (line == null) return false;
        return caseSensitive ? line.equals(rightKeyword) : line.equalsIgnoreCase(rightKeyword);
    }
}
