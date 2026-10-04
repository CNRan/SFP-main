package cn.starfallplain.sfpmain.display;

import cn.starfallplain.sfpmain.config.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 显示文本渲染（Tab 列表 / 计分板共用）。
 * <p>
 * <b>渲染顺序很关键</b>（踩过坑）：
 * <ol>
 *   <li>先替换内置占位符（{@code {player}} 等，值本身是安全的纯文本）</li>
 *   <li>把模板交给 MiniMessage 解析成 {@link Component} —— 这样模板自己的标签结构一定正确</li>
 *   <li>最后在**组件上**做 PlaceholderAPI 替换（{@code Component#replaceText}），
 *       返回值作为纯组件插入，**不再参与 MiniMessage 解析**</li>
 * </ol>
 * 如果反过来（先把 PAPI 替换进字符串、再整体 MiniMessage 解析），
 * 像 spark 这类**返回值自带颜色码**（含 {@code §r}）的扩展会把外层标签的重置掉，
 * 于是 {@code </white></gray>} 这类闭合标签会以字面形式显示出来。
 * <p>
 * PlaceholderAPI 用**反射**调用：PAPI 是可选依赖，直接引用其类会在「服务器没装 PAPI」时
 * 类加载失败；返回值的传统色码（{@code §a}）交给 {@link LegacyComponentSerializer} 解析，
 * 因此 spark 的着色能正确渲染。
 */
public final class DisplayText {

    private static final Pattern PAPI_PATTERN = Pattern.compile("%[^%]{1,64}%");

    /** 只缓存成功解析到的方法（PAPI 后装也能生效） */
    private static Method papiMethod;

    private DisplayText() {
    }

    /** 渲染单行 */
    public static Component render(Player player, String template) {
        if (template == null) return Component.empty();
        Component component = Messages.deserialize(fillBuiltin(player, template));
        return replacePlaceholders(player, component);
    }

    /** 渲染多行为一个组件（换行连接） */
    public static Component renderLines(Player player, List<String> templates) {
        Component result = Component.empty();
        boolean first = true;
        for (String line : templates) {
            if (!first) {
                result = result.append(Component.newline());
            }
            result = result.append(render(player, line));
            first = false;
        }
        return result;
    }

    /** 内置占位符（不依赖任何插件） */
    private static String fillBuiltin(Player player, String template) {
        return template
                .replace("{player}", player.getName())
                .replace("{world}", player.getWorld().getName())
                .replace("{x}", String.valueOf(player.getLocation().getBlockX()))
                .replace("{y}", String.valueOf(player.getLocation().getBlockY()))
                .replace("{z}", String.valueOf(player.getLocation().getBlockZ()))
                .replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("{max}", String.valueOf(Bukkit.getMaxPlayers()))
                .replace("{tps}", formatTps())
                .replace("{mspt}", formatMspt());
    }

    /** 当前 TPS（最近 1 分钟的平均值，保留 1 位小数） */
    private static String formatTps() {
        double[] tps = Bukkit.getTPS();
        double value = tps.length > 0 ? tps[0] : 20.0;
        return String.format(Locale.ROOT, "%.1f", value);
    }

    /** 当前 MSPT（每 tick 平均耗时，保留 1 位小数）；取不到时返回 - */
    private static String formatMspt() {
        try {
            return String.format(Locale.ROOT, "%.1f", Bukkit.getAverageTickTime());
        } catch (Throwable t) {
            return "-";
        }
    }

    /** 在已解析的组件上替换 PAPI 占位符 */
    private static Component replacePlaceholders(Player player, Component component) {
        Method method = resolvePapi();
        if (method == null) return component;
        return component.replaceText(config -> config
                .match(PAPI_PATTERN)
                .replacement((match, builder) -> {
                    String value = invokePapi(method, player, match.group());
                    // 返回值可能自带 § 颜色码（spark 就会），按传统色码解析成组件
                    return LegacyComponentSerializer.legacySection().deserialize(value);
                }));
    }

    private static String invokePapi(Method method, Player player, String placeholder) {
        try {
            Object result = method.invoke(null, player, placeholder);
            return result instanceof String s ? s : placeholder;
        } catch (Throwable t) {
            return placeholder;
        }
    }

    private static Method resolvePapi() {
        if (papiMethod != null) return papiMethod;
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return null;
        try {
            Class<?> clazz = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            papiMethod = clazz.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
        } catch (Throwable t) {
            return null;
        }
        return papiMethod;
    }
}
