package cn.starfallplain.sfpmain.util;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

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

    /** 名称（大写枚举名 / 小写注册名）→ Sound；首次解析时构建 */
    private static Map<String, Sound> cache;

    private SoundUtil() {
    }

    /** 解析音效名，无效返回 null */
    public static Sound resolve(String name) {
        if (name == null || name.isBlank()) return null;
        ensureCache();

        String trimmed = name.trim();
        Sound sound = cache.get(trimmed.toUpperCase(Locale.ROOT));
        if (sound != null) return sound;

        // 再按注册名试（minecraft:entity.enderman.teleport）
        String key = trimmed.toLowerCase(Locale.ROOT);
        if (!key.contains(":")) {
            key = "minecraft:" + key;
        }
        NamespacedKey namespacedKey = NamespacedKey.fromString(key);
        return namespacedKey == null ? null : Registry.SOUNDS.get(namespacedKey);
    }

    /**
     * 构建「名称 → Sound」缓存。
     * 注册名是点分隔的（entity.enderman.teleport），而配置里习惯写枚举名
     * （ENTITY_ENDERMAN_TELEPORT），两种形式都放一份，避免依赖 valueOf（已标记待删除）。
     */
    private static void ensureCache() {
        if (cache != null) return;
        Map<String, Sound> map = new HashMap<>();
        for (Sound sound : Registry.SOUNDS) {
            // 用 Registry#getKey 取键，避免 Sound#getKey()（已标记待删除）
            NamespacedKey key = Registry.SOUNDS.getKey(sound);
            if (key == null) continue;
            map.put(key.getKey().replace('.', '_').toUpperCase(Locale.ROOT), sound);
            map.put(key.getKey().toLowerCase(Locale.ROOT), sound);
        }
        cache = map;
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
