package cn.starfallplain.CN_Ran.sfp.clean;

import cn.starfallplain.CN_Ran.sfp.StarfallplainMenu;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

/**
 * PAPI 占位符：扫地倒计时。
 * %stf_cleantime%（带颜色）/ %stf_cleantime_plain%（纯文本）
 */
public class CleanTimePlaceholder extends PlaceholderExpansion {

    private final StarfallplainMenu plugin;

    public CleanTimePlaceholder(StarfallplainMenu plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "stf";
    }

    @Override
    public @NotNull String getAuthor() {
        return "CN_Ran";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.1.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        FloorCleanManager manager = plugin.getFloorCleanManager();
        if (manager == null) return "";

        if (params.equalsIgnoreCase("cleantime")) {
            return manager.getColoredTimeMini();
        }
        if (params.equalsIgnoreCase("cleantime_plain")) {
            return manager.formatTime();
        }
        return null;
    }
}
