package cn.starfallplain.CN_Ran.sfp.util;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * 音效工具：把配置中的音效名解析为 {@link Sound} 并播放。
 * <p>
 * 支持两种写法：
 * <ul>
 *   <li>Bukkit 枚举名，如 {@code ENTITY_ENDERMAN_TELEPORT}、{@code BLOCK_NOTE_BLOCK_PLING}</li>
 *   <li>注册名，如 {@code minecraft:entity.enderman.teleport}（点分隔）</li>
 * </ul>
 * 名称无效或留空时静默跳过，不影响主流程。
 */
public final class SoundUtil {

    private SoundUtil() {
    }

    /** 解析音效名，无效返回 null */
    @SuppressWarnings("deprecation")
    public static Sound resolve(String name) {
        if (name == null || name.isBlank()) return null;
        // 优先按 Bukkit 枚举名（配置文件里最常用这种写法）
        try {
            return Sound.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            // 不是枚举名，下面按注册名再试
        }
        // 注册名（注意是点分隔，如 minecraft:entity.enderman.teleport）
        String key = name.trim().toLowerCase(Locale.ROOT);
        if (!key.contains(":")) {
            key = "minecraft:" + key;
        }
        NamespacedKey namespacedKey = NamespacedKey.fromString(key);
        if (namespacedKey == null) return null;
        return Registry.SOUNDS.get(namespacedKey);
    }

    /** 在世界某位置播放音效；音效名无效则忽略 */
    public static void play(org.bukkit.World world, Location location, String name, float volume, float pitch) {
        Sound sound = resolve(name);
        if (sound == null || world == null) return;
        world.playSound(location, sound, volume, pitch);
    }

    /** 仅向某个玩家播放音效 */
    public static void play(Player player, Location location, String name, float volume, float pitch) {
        Sound sound = resolve(name);
        if (sound == null || player == null) return;
        player.playSound(location, sound, volume, pitch);
    }
}
