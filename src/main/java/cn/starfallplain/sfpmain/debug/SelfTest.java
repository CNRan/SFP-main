package cn.starfallplain.sfpmain.debug;

import cn.starfallplain.sfpmain.SfpMain;
import cn.starfallplain.sfpmain.chair.ChairManager;
import cn.starfallplain.sfpmain.clean.FloorCleanManager;
import cn.starfallplain.sfpmain.config.ConfigManager;
import cn.starfallplain.sfpmain.config.module.MenuConfig;
import cn.starfallplain.sfpmain.menu.MenuClockManager;
import cn.starfallplain.sfpmain.menu.MenuHolder;
import cn.starfallplain.sfpmain.menu.MenuManager;
import cn.starfallplain.sfpmain.teleport.TeleportManager;
import cn.starfallplain.sfpmain.teleport.db.Database;
import cn.starfallplain.sfpmain.trashbin.TrashBinManager;
import cn.starfallplain.sfpmain.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 功能自检（{@code /sfp test}）：把配置、菜单、数据层、传送内容、各模块运行态过一遍，
 * 逐项给出 <b>[OK] / [注意] / [错误]</b> 并汇总。
 * <p>
 * 设计原则：
 * <ul>
 *   <li>只读为主 —— 唯一会写数据的是数据层的「增删改查回归」，用哨兵 UUID + 哨兵名称，
 *       且无论成功失败都会清理，不碰真实玩家数据。</li>
 *   <li>能查出「编译期看不出来、只有真跑才会踩」的问题：按钮没绑动作、槽位越界、
 *       表结构版本不对、家/传送点指向已删除的世界、文案键缺失、配置数值越界。</li>
 *   <li>输出写死在本类里（诊断条目多且动态，不进 messages.yml），门槛为 {@code sfpmenu.admin}。</li>
 * </ul>
 */
public final class SelfTest {

    /** 每张表期望的列（schema 漂移检测） */
    private static final Map<String, String[]> EXPECTED_COLUMNS = new LinkedHashMap<>();

    static {
        EXPECTED_COLUMNS.put("homes", new String[]{
                "player_uuid", "home_name", "world", "x", "y", "z", "yaw", "pitch", "created_at"});
        EXPECTED_COLUMNS.put("warps", new String[]{
                "warp_name", "world", "x", "y", "z", "yaw", "pitch", "created_at"});
        EXPECTED_COLUMNS.put("last_locations", new String[]{
                "player_uuid", "world", "x", "y", "z", "yaw", "pitch", "updated_at"});
    }

    /** messages.yml 里必须存在的键（缺了不会报错，只会静默退回代码内置文案，所以专门查一遍） */
    private static final String[] REQUIRED_MESSAGE_KEYS = {
            "common.no-permission", "common.player-only", "common.feature-disabled", "common.back-to-menu",
            "menu.head-name",
            "trashbin.title", "trashbin.disabled", "trashbin.prev", "trashbin.next",
            "clean.reminder", "clean.result",
            "home.teleport-success", "home.set-success", "home.not-found",
            "warp.teleport-success", "warp.set-success", "warp.not-found",
            "back.no-location", "back.success",
            "teleport.delayed", "teleport.cancelled", "teleport.cooldown", "teleport.world-missing",
            "tpa.request-to", "tpa.request-here", "tpa.gui-title", "tpa.no-request",
    };

    private SelfTest() {
    }

    /** 运行全部自检并输出报告 */
    public static void run(SfpMain plugin, CommandSender sender) {
        Report r = new Report(sender);
        DbDebug.send(sender, "<dark_gray>========== 星落平原 自检 ==========</dark_gray>");
        checkConfig(plugin, r);
        checkMenu(plugin, r, sender);
        checkStorage(plugin, r);
        checkTeleportData(plugin, r);
        checkModules(plugin, r, sender);
        DbDebug.send(sender, "<dark_gray>------------------------------------</dark_gray>");
        DbDebug.send(sender, "<gray>结果：</gray><green>通过 " + r.ok + "</green>　<yellow>注意 "
                + r.warn + "</yellow>　<red>错误 " + r.fail + "</red>");
    }

    // ==================== 配置 ====================

    private static void checkConfig(SfpMain plugin, Report r) {
        r.section("配置");
        ConfigManager cm = plugin.getConfigManager();

        Map<String, Boolean> loaded = new LinkedHashMap<>();
        loaded.put("config.yml", cm.global() != null);
        loaded.put("messages.yml", cm.messages() != null);
        loaded.put("menu.yml", cm.menu() != null && cm.menu().raw() != null);
        loaded.put("clean.yml", cm.clean() != null && cm.clean().raw() != null);
        loaded.put("trashbin.yml", cm.trashBin() != null && cm.trashBin().raw() != null);
        loaded.put("chair.yml", cm.chair() != null && cm.chair().raw() != null);
        loaded.put("teleport.yml", cm.teleport() != null && cm.teleport().raw() != null);
        List<String> notLoaded = new ArrayList<>();
        loaded.forEach((name, okFlag) -> {
            if (!okFlag) notLoaded.add(name);
        });
        if (notLoaded.isEmpty()) {
            r.ok("7 个配置文件均已加载");
        } else {
            r.fail("以下配置未能加载：" + String.join(", ", notLoaded));
        }

        var tp = cm.teleport();
        List<String> badTp = new ArrayList<>();
        if (tp.getDelaySeconds() < 0) badTp.add("teleport.delay-seconds 为负");
        if (tp.getHomeSetCooldownSeconds() < 0) badTp.add("home.set-cooldown-seconds 为负");
        if (tp.getHomeTeleportCooldownSeconds() < 0) badTp.add("home.teleport-cooldown-seconds 为负");
        if (tp.getWarpTeleportCooldownSeconds() < 0) badTp.add("warp.teleport-cooldown-seconds 为负");
        if (tp.getHomeMaxHomes() < -1) badTp.add("home.max-homes 小于 -1");
        if (tp.getSafeSearchDistance() <= 0) badTp.add("teleport.safe-search-distance 应大于 0");
        if (tp.getDbFile() == null || tp.getDbFile().isBlank()) badTp.add("database.file 为空");
        if (badTp.isEmpty()) r.ok("teleport.yml 数值合法");
        else r.warn("teleport.yml：" + String.join("；", badTp));

        var clean = cm.clean();
        if (clean.getCycleSeconds() < 60) {
            r.warn("clean.cycle-seconds = " + clean.getCycleSeconds() + "，小于 60 会被代码强制提到 60");
        } else {
            r.ok("clean.cycle-seconds = " + clean.getCycleSeconds() + " 秒");
        }
        List<Integer> warns = clean.getWarnSeconds();
        if (warns == null || warns.isEmpty()) {
            r.warn("clean.warn-seconds 为空，清扫前不会有任何提醒");
        } else {
            List<Integer> invalid = warns.stream()
                    .filter(s -> s <= 0 || s >= clean.getCycleSeconds()).toList();
            if (invalid.isEmpty()) r.ok("clean.warn-seconds = " + warns + "，都在周期内");
            else r.warn("clean.warn-seconds 中这些值不在 (0, 周期) 内，永远不会触发：" + invalid);
        }

        List<String> missingKeys = new ArrayList<>();
        for (String key : REQUIRED_MESSAGE_KEYS) {
            if (!cm.messages().has(key)) missingKeys.add(key);
        }
        if (missingKeys.isEmpty()) {
            r.ok("messages.yml 关键文案键齐全（查了 " + REQUIRED_MESSAGE_KEYS.length + " 个）");
        } else {
            r.warn("messages.yml 缺少 " + missingKeys.size() + " 个键（会退回代码内置文案）："
                    + String.join(", ", missingKeys));
        }

        // 音效名有效性：resolve 返回 null 就是「无声」，是静默 bug 高发处
        List<String> badSounds = new ArrayList<>();
        checkSound(cm.teleport().getWaitStartSound(), "teleport.sounds.start", badSounds);
        checkSound(cm.teleport().getWaitTickSound(), "teleport.sounds.tick", badSounds);
        checkSound(cm.teleport().getTeleportSound(), "teleport.sounds.teleport", badSounds);
        checkSound(cm.trashBin().getTakeSound(), "trashbin.sounds.take", badSounds);
        if (badSounds.isEmpty()) {
            r.ok("配置的音效名全部有效");
        } else {
            r.fail("以下音效名无法解析（会无声）：" + String.join(", ", badSounds));
        }
    }

    /** 留空 = 静音（合法）；非空但解析失败 = 有问题 */
    private static void checkSound(String name, String label, List<String> bad) {
        if (name == null || name.isBlank()) return;
        if (SoundUtil.resolve(name) == null) {
            bad.add(label + "(" + name + ")");
        }
    }

    // ==================== 菜单 ====================

    private static void checkMenu(SfpMain plugin, Report r, CommandSender sender) {
        r.section("菜单");
        ConfigManager cm = plugin.getConfigManager();
        MenuConfig menu = cm.menu();

        if (!menu.isEnabled()) {
            r.warn("menu.yml 的 enabled 为 false，/menu 会提示未启用");
            return;
        }
        int size = menu.getSize();
        r.ok("菜单尺寸 " + menu.getRows() + " 行（" + size + " 格）");

        List<String> outOfRange = new ArrayList<>();
        List<String> unbound = new ArrayList<>();
        List<String> infoOnly = new ArrayList<>();
        for (MenuConfig.Button b : menu.getButtons().values()) {
            if (b.getSlot() < 0 || b.getSlot() >= size) {
                outOfRange.add(b.getId() + "(" + b.getSlot() + ")");
            }
            if (b.isVisible() && MenuManager.resolveAction(b, menu) == null) {
                // 有说明文案的按钮属于「纯展示」，是设计如此；两者都没有才算真问题
                if (b.getLore() != null && !b.getLore().isEmpty()) infoOnly.add(b.getId());
                else unbound.add(b.getId());
            }
        }
        if (outOfRange.isEmpty()) r.ok("所有按钮槽位都在 0~" + (size - 1) + " 内");
        else r.fail("按钮槽位越界（不会出现在菜单里）：" + String.join(", ", outOfRange));
        if (unbound.isEmpty()) r.ok("启用的按钮都绑定了动作（缺少动作的只作说明展示）");
        else r.warn("这些按钮既没绑动作也没有说明文案，点了完全没反应：" + String.join(", ", unbound));
        if (!infoOnly.isEmpty()) {
            DbDebug.send(sender, "<dark_gray>  · 仅作说明展示（有 lore、无动作）："
                    + String.join(", ", infoOnly) + "</dark_gray>");
        }

        Map<Integer, String> actions = MenuManager.resolveSlotActions(menu, cm);
        DbDebug.send(sender, "<gray>实际生效的按钮 " + actions.size() + " 个：</gray>");
        for (Map.Entry<Integer, String> e : actions.entrySet()) {
            DbDebug.send(sender, "<dark_gray>  · 槽位 </dark_gray><white>" + e.getKey() + "</white>"
                    + "<dark_gray> → </dark_gray><gray>" + describeAction(e.getValue()) + "</gray>");
        }

        if (sender instanceof Player player) {
            List<String> lack = new ArrayList<>();
            if (!player.hasPermission("sfpmenu.player")) lack.add("sfpmenu.player");
            if (!player.hasPermission("sfpmenu.teleport")) lack.add("sfpmenu.teleport");
            String openPerm = menu.getOpenPermission();
            if (openPerm != null && !openPerm.isBlank() && !player.hasPermission(openPerm)) {
                lack.add(openPerm);
            }
            if (lack.isEmpty()) r.ok("你的权限可以完整使用菜单");
            else r.warn("你缺少权限：" + String.join(", ", lack));
        } else {
            DbDebug.send(sender, "<gray>（控制台执行，跳过权限检查）</gray>");
        }

        // dialogUI 构建冒烟：跑一遍 Dialog API 调用链，验证在服务器环境不抛异常
        try {
            if (cn.starfallplain.sfpmain.ui.DialogMenu.build(plugin) != null) {
                r.ok("dialogUI 主菜单构建正常（" + actions.size() + " 个按钮）");
            } else {
                r.warn("dialogUI 主菜单没有可用按钮");
            }
        } catch (Throwable t) {
            r.fail("dialogUI 主菜单构建失败：" + t);
        }

        checkMenuClock(plugin, r);
    }

    /** 菜单钟：合成配方是否已注册、物品能否构建并被自身识别 */
    private static void checkMenuClock(SfpMain plugin, Report r) {
        MenuConfig menu = plugin.getConfigManager().menu();
        if (!menu.isMenuClockEnabled()) {
            r.warn("菜单钟已按配置关闭（menu-clock.enabled=false）");
            return;
        }
        MenuClockManager manager = plugin.getMenuClockManager();
        if (manager == null) {
            r.warn("菜单钟管理器未创建（主菜单可能被关闭）");
            return;
        }
        r.ok("菜单钟材质 " + menu.getMenuClockMaterial().name()
                + (menu.isMenuClockGlint() ? "（带附魔光效）" : ""));

        if (menu.isMenuClockRecipeEnabled()) {
            NamespacedKey key = new NamespacedKey(plugin, "menu_clock");
            boolean registered = Bukkit.getRecipe(key) != null;
            if (registered) {
                r.ok("菜单钟合成配方已注册：" + describeIngredients(menu) + " → 菜单钟");
            } else {
                r.fail("菜单钟合成配方未注册（玩家无法合成；检查是否与其他插件配方键冲突）");
            }
        } else {
            r.warn("菜单钟合成配方已按配置关闭（recipe.enabled=false）");
        }

        // 物品构建 + 自识别冒烟
        try {
            ItemStack item = manager.createItem();
            if (manager.isMenuClock(item)) {
                r.ok("菜单钟物品构建与识别正常");
            } else {
                r.fail("菜单钟物品构建后无法被识别（PDC 标记异常）");
            }
        } catch (Throwable t) {
            r.fail("菜单钟物品构建失败：" + t);
        }
    }

    private static String describeIngredients(MenuConfig menu) {
        StringJoiner joiner = new StringJoiner(" + ");
        for (Material material : menu.getMenuClockIngredients()) {
            joiner.add(material.name());
        }
        return joiner.toString();
    }

    /** 把内部动作串翻译成人话 */
    static String describeAction(String action) {
        if (action == null) return "无动作";
        if (action.startsWith(MenuHolder.CMD_PREFIX)) {
            return "执行命令 /" + action.substring(MenuHolder.CMD_PREFIX.length());
        }
        return switch (action) {
            case MenuHolder.ACTION_TRASHBIN -> "打开垃圾桶";
            case MenuHolder.ACTION_HOME -> "打开家列表";
            case MenuHolder.ACTION_WARP -> "打开传送点列表";
            case MenuHolder.ACTION_BACK -> "执行 /back";
            case MenuHolder.ACTION_TPA -> "打开玩家传送目标选择界面";
            case MenuHolder.ACTION_TOGGLE_UI -> "切换界面样式（dialogUI / 箱子）";
            default -> action;
        };
    }

    // ==================== 数据层 ====================

    private static void checkStorage(SfpMain plugin, Report r) {
        r.section("传送数据层");
        TeleportManager manager = plugin.getTeleportManager();
        if (manager == null) {
            r.warn("传送系统未启用（teleport.yml 的 enabled 为 false），跳过数据层检查");
            return;
        }
        if (!manager.isStorageAvailable()) {
            r.fail("数据库不可连接 —— /back /home /warp 全部不可用，请看启动日志");
            return;
        }

        Database db = manager.getDatabase();
        r.ok("数据库可连接：" + db.getFile().getName() + "（" + (db.getFile().length() / 1024) + " KB）");

        Connection c = db.getConnection();
        int actual = pragmaInt(c, "PRAGMA user_version");
        int expected = Database.expectedSchemaVersion();
        if (actual == expected) {
            r.ok("表结构版本 v" + actual + "，与代码一致");
        } else if (actual < expected) {
            r.fail("表结构版本 v" + actual + " 低于代码期望的 v" + expected + "，升级没生效（看启动日志里的迁移报错）");
        } else {
            r.warn("表结构版本 v" + actual + " 高于代码期望的 v" + expected
                    + "（可能由更新版本的插件写入；本插件不会改动结构）");
        }

        for (Map.Entry<String, String[]> e : EXPECTED_COLUMNS.entrySet()) {
            List<String> cols = columns(c, e.getKey());
            if (cols.isEmpty()) {
                r.fail("缺少表 " + e.getKey());
                continue;
            }
            List<String> missing = new ArrayList<>();
            for (String col : e.getValue()) {
                if (!cols.contains(col)) missing.add(col);
            }
            if (missing.isEmpty()) r.ok("表 " + e.getKey() + " 列齐全（" + cols.size() + " 列）");
            else r.fail("表 " + e.getKey() + " 缺少列：" + String.join(", ", missing));
        }

        String integrity = pragmaText(c, "PRAGMA integrity_check");
        if ("ok".equalsIgnoreCase(integrity)) r.ok("integrity_check 通过");
        else r.fail("integrity_check 未通过：" + integrity);

        String crud = DbDebug.crudSelfTest(plugin);
        if (crud == null) r.ok("增删改查回归通过（家 / 传送点 / 返回点；哨兵数据已清理）");
        else r.fail("增删改查回归失败：" + crud);
    }

    // ==================== 传送内容 ====================

    private static void checkTeleportData(SfpMain plugin, Report r) {
        r.section("传送内容");
        TeleportManager manager = plugin.getTeleportManager();
        if (manager == null || !manager.isStorageAvailable()) return;
        Connection c = manager.getDatabase().getConnection();

        List<String> broken = new ArrayList<>();
        collectBrokenWorlds(c, "SELECT home_name, world FROM homes", "家 ", broken, r);
        collectBrokenWorlds(c, "SELECT warp_name, world FROM warps", "传送点 ", broken, r);
        if (broken.isEmpty()) r.ok("所有家 / 传送点指向的世界都存在");
        else r.warn("这些条目指向不存在或未加载的世界（传送时会提示失败）：" + String.join(", ", broken));

        int max = plugin.getConfigManager().teleport().getHomeMaxHomes();
        if (max < 0) {
            r.ok("家数量无上限（max-homes = -1）");
            return;
        }
        List<String> over = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT player_uuid, COUNT(*) AS c FROM homes GROUP BY player_uuid HAVING c > ?")) {
            ps.setInt(1, max);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    over.add(rs.getString("player_uuid") + "(" + rs.getInt("c") + " 个)");
                }
            }
        } catch (SQLException e) {
            r.fail("统计超限玩家失败：" + e.getMessage());
            return;
        }
        if (over.isEmpty()) r.ok("没有玩家的家数量超过上限（" + max + "）");
        else r.warn("这些玩家的家数量已超上限（通常是后来调小了 max-homes）：" + String.join(", ", over));
    }

    private static void collectBrokenWorlds(Connection c, String sql, String label,
                                             List<String> broken, Report r) {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String world = rs.getString("world");
                if (Bukkit.getWorld(world) == null) {
                    broken.add(label + rs.getString(1) + "@" + world);
                }
            }
        } catch (SQLException e) {
            r.fail("查询失败：" + e.getMessage());
        }
    }

    // ==================== 功能模块 ====================

    private static void checkModules(SfpMain plugin, Report r, CommandSender sender) {
        r.section("功能模块");
        boolean papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
        boolean dominion = Bukkit.getPluginManager().getPlugin("Dominion") != null;
        r.ok("前置依赖：PlaceholderAPI " + (papi ? "已安装" : "未安装")
                + "，Dominion " + (dominion ? "已安装" : "未安装"));
        if (!papi) r.warn("缺少 PlaceholderAPI：%stf_cleantime% 占位符不可用");
        if (!dominion) r.warn("缺少 Dominion：菜单里的领地按钮点击无效果");

        printRuntime(plugin, sender);
    }

    /**
     * 打印各模块开关与实时运行态。{@code /sfp status} 与 {@code /sfp test} 共用，
     * 保证两处看到的信息完全一致。
     */
    static void printRuntime(SfpMain plugin, CommandSender sender) {
        boolean papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
        for (String line : plugin.getConfigManager().describeState(papi)) {
            DbDebug.send(sender, "<dark_gray>" + line.trim() + "</dark_gray>");
        }

        FloorCleanManager clean = plugin.getFloorCleanManager();
        DbDebug.send(sender, "<gray>  · 扫地：</gray>" + (clean == null
                ? "<dark_gray>未启动</dark_gray>"
                : "<white>下次清扫还有 " + clean.formatTime() + "</white>"));

        TrashBinManager trash = plugin.getTrashBinManager();
        DbDebug.send(sender, "<gray>  · 垃圾桶：</gray>" + (trash == null
                ? "<dark_gray>未启动</dark_gray>"
                : "<white>" + trash.size() + " 件 / " + trash.getPageCount() + " 页</white>"));

        ChairManager chair = plugin.getChairManager();
        DbDebug.send(sender, "<gray>  · 椅子：</gray>" + (chair == null
                ? "<dark_gray>未启动</dark_gray>"
                : "<white>当前 " + chair.sittingCount() + " 人在座</white>"));

        TeleportManager tp = plugin.getTeleportManager();
        DbDebug.send(sender, "<gray>  · 传送：</gray>" + (tp == null
                ? "<dark_gray>未启动</dark_gray>"
                : "<white>等待中的延迟传送 " + tp.pendingCount() + " 笔</white>"));

        cn.starfallplain.sfpmain.teleport.TpaManager tpa = plugin.getTpaManager();
        DbDebug.send(sender, "<gray>  · 玩家传送（/tpa）：</gray>" + (tpa == null
                ? "<dark_gray>未启动</dark_gray>"
                : (tpa.isEnabled()
                        ? "<white>待处理请求 " + tpa.pendingCount() + " 笔</white>"
                        : "<dark_gray>已关闭（teleport.yml 的 tpa.enabled）</dark_gray>")));

        cn.starfallplain.sfpmain.bot.BotManager bot = plugin.getBotManager();
        DbDebug.send(sender, "<gray>  · 假人（/bot）：</gray>" + (bot == null
                ? "<dark_gray>未启动</dark_gray>"
                : "<white>" + bot.size() + " 个（已加入 " + bot.onlineCount() + "）</white>"));
    }

    // ==================== 工具 ====================

    private static List<String> columns(Connection c, String table) {
        List<String> cols = new ArrayList<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) cols.add(rs.getString("name"));
        } catch (SQLException ignored) {
            // 表不存在等情况，返回空列表由调用方报错
        }
        return cols;
    }

    private static int pragmaInt(Connection c, String sql) {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : -1;
        } catch (SQLException e) {
            return -1;
        }
    }

    private static String pragmaText(Connection c, String sql) {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : "";
        } catch (SQLException e) {
            return "(失败：" + e.getMessage() + ")";
        }
    }

    /** 计数并输出的小工具 */
    private static final class Report {
        private final CommandSender sender;
        private int ok;
        private int warn;
        private int fail;

        Report(CommandSender sender) {
            this.sender = sender;
        }

        void section(String name) {
            DbDebug.send(sender, " ");
            DbDebug.send(sender, "<gold>【" + name + "】</gold>");
        }

        void ok(String message) {
            ok++;
            DbDebug.send(sender, "<green>[OK]</green> <gray>" + message + "</gray>");
        }

        void warn(String message) {
            warn++;
            DbDebug.send(sender, "<yellow>[注意]</yellow> <gray>" + message + "</gray>");
        }

        void fail(String message) {
            fail++;
            DbDebug.send(sender, "<red>[错误]</red> <gray>" + message + "</gray>");
        }
    }
}
