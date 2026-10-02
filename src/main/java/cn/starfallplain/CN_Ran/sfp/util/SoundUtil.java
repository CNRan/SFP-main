package cn.starfallplain.CN_Ran.sfp.util;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * 音效工具：把配置中的音效名（如 "ENTITY_PLAYER_LEVELUP" 或
 * "minecraft:entity.player.levelup"）解析为 Sound 并播放。
 * 名称无效或留空时静默跳过，不影响主流程。
 */
public final class SoundUtil {

    private SoundUtil() {
    }

    /** 解析音效名，无效返回 null */
    public static Sound resolve(String name) {
        if (name == null || name.isBlank()) return null;
        String key = name.trim().toLowerCase();
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
