package cn.starfallplain.CN_Ran.sfp;

import cn.starfallplain.CN_Ran.sfp.chair.ChairListener;
import cn.starfallplain.CN_Ran.sfp.chair.ChairManager;
import cn.starfallplain.CN_Ran.sfp.clean.CleanTimePlaceholder;
import cn.starfallplain.CN_Ran.sfp.clean.FloorCleanManager;
import cn.starfallplain.CN_Ran.sfp.config.ConfigManager;
import cn.starfallplain.CN_Ran.sfp.debug.SfpCommand;
import cn.starfallplain.CN_Ran.sfp.menu.MenuCommand;
import cn.starfallplain.CN_Ran.sfp.menu.MenuListener;
import cn.starfallplain.CN_Ran.sfp.teleport.TeleportListener;
import cn.starfallplain.CN_Ran.sfp.teleport.TeleportManager;
import cn.starfallplain.CN_Ran.sfp.teleport.TpaListener;
import cn.starfallplain.CN_Ran.sfp.teleport.TpaManager;
import cn.starfallplain.CN_Ran.sfp.teleport.command.BackCommand;
import cn.starfallplain.CN_Ran.sfp.teleport.command.HomeCommand;
import cn.starfallplain.CN_Ran.sfp.teleport.command.TpaCommand;
import cn.starfallplain.CN_Ran.sfp.teleport.command.WarpCommand;
import cn.starfallplain.CN_Ran.sfp.teleport.gui.TeleportGuiListener;
import cn.starfallplain.CN_Ran.sfp.trashbin.TrashBinCommand;
import cn.starfallplain.CN_Ran.sfp.trashbin.TrashBinListener;
import cn.starfallplain.CN_Ran.sfp.trashbin.TrashBinManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * 星落平原主菜单插件主类。
 * <p>
 * 仅保留插件自研功能（菜单、自动扫地、垃圾桶、椅子、传送）与 Dominion 领地入口；
 * 不再依赖 HuskHomes / Vault 等经济与传送插件。
 * 所有功能均由配置文件驱动，开关关闭的模块不会被实例化、不注册监听器。
 * <p>
 * 本插件是 <b>Paper 插件</b>（paper-plugin.yml）：命令不能在描述文件里声明，
 * 统一在 {@link #registerCommands()} 中通过 {@code LifecycleEvents.COMMANDS} 注册。
 */
public final class StarfallplainMenu extends JavaPlugin {

    private ConfigManager configManager;

    private FloorCleanManager floorCleanManager;
    private ChairManager chairManager;
    private TrashBinManager trashBinManager;
    private TeleportManager teleportManager;
    /** 玩家间传送请求（/tpa /tpahere /tpaccept /tpdeny），随传送系统一同创建 */
    private TpaManager tpaManager;

    @Override
    public void onEnable() {
        // 1) 加载全部配置（含各模块开关）
        configManager = new ConfigManager(this);

        boolean papiOk = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;

        // 2) 按开关装配各功能模块（垃圾桶须先于扫地创建；传送须先于命令注册）
        setupTrashBin();
        setupFloorClean();
        setupChair();
        setupTeleport();
        setupMenu();

        // 3) 注册命令（依赖上面已建好的各管理器，故放在最后）
        registerCommands();

        // 4) PAPI 占位符
        setupPlaceholders(papiOk);

        // 5) 汇总启动状态
        getLogger().info("============== 星落平原 功能状态 ==============");
        for (String line : configManager.describeState(papiOk)) {
            getLogger().info(line);
        }
        getLogger().info("==============================================");
        getLogger().info("Starfallplain Menu 已启用！");
    }

    @Override
    public void onDisable() {
        // 保存垃圾桶数据，避免直接关服丢失
        if (trashBinManager != null) {
            trashBinManager.save();
        }
        // 关闭传送数据库连接
        if (teleportManager != null) {
            teleportManager.shutdown();
        }
        // 取消未过期的 tpa 请求任务
        if (tpaManager != null) {
            tpaManager.shutdown();
        }
        getLogger().info("Starfallplain Menu 已禁用！");
    }

    // ==================== 启动装配 ====================

    /**
     * 注册全部命令。
     * <p>
     * Paper 插件不支持 plugin.yml 的 {@code commands} 段，改为注册到本插件自己的
     * {@code LifecycleEvents.COMMANDS} 生命周期事件：Paper 会在需要时（含 /reload）
     * 重新触发该事件，命令随之重新注册，无需自行处理重载。
     */
    private void registerCommands() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();

            // 主菜单：/menu 与简写 /m（别名）
            registrar.register("menu", "打开星落平原菜单", List.of("m"), new MenuCommand(this));

            // 垃圾桶
            registrar.register("trashbin", "打开扫地垃圾桶，取回被清扫的掉落物",
                    new TrashBinCommand(this));

            // 传送命令
            registerTeleportCommands(registrar);

            // 管理 / 调试命令 /sfp：reload / status / db / test
            registrar.register("sfp", "星落平原管理命令（输入 /sfp 查看用法）", new SfpCommand(this));
        });
    }

    /**
     * 注册传送命令（/back /home /sethome /delhome /homes /warp /setwarp /delwarp /warps
     * 以及 /tpa /tpahere /tpaccept /tpdeny）。
     * <p>
     * {@code BackCommand} 等实现的是 {@link BasicCommand}，拿不到命令标签，
     * 因此 Home / Warp / Tpa 系列由构造参数区分动作，每个标签各注册一个实例。
     * <p>
     * 传送模块被关闭（{@code teleportManager == null}）时仍注册同名命令，
     * 统一回复「功能未启用」，避免玩家误以为命令不存在。
     */
    private void registerTeleportCommands(Commands registrar) {
        if (teleportManager == null) {
            BasicCommand disabled = (source, args) -> source.getSender().sendMessage(
                    getMessage("common.feature-disabled", "<red>该功能当前未启用。</red>"));
            for (String label : new String[]{"back", "home", "sethome", "delhome", "homes",
                    "warp", "setwarp", "delwarp", "warps",
                    "tpa", "tpahere", "tpaccept", "tpdeny"}) {
                registrar.register(label, "传送功能（当前未启用）", disabled);
            }
            return;
        }

        registrar.register("back", "返回上一位置（上次传送前 / 死亡点）",
                new BackCommand(this, teleportManager));

        // /home /sethome /delhome /homes 共用一套逻辑，用 action 区分
        registrar.register("home", "打开家列表或传送到指定家",
                new HomeCommand(this, teleportManager, "home"));
        registrar.register("sethome", "设置个人家",
                new HomeCommand(this, teleportManager, "sethome"));
        registrar.register("delhome", "删除个人家",
                new HomeCommand(this, teleportManager, "delhome"));
        registrar.register("homes", "列出所有个人家",
                new HomeCommand(this, teleportManager, "homes"));

        // /warp /setwarp /delwarp /warps
        registrar.register("warp", "打开传送点列表或传送到指定传送点",
                new WarpCommand(this, teleportManager, "warp"));
        registrar.register("setwarp", "创建公共传送点",
                new WarpCommand(this, teleportManager, "setwarp"));
        registrar.register("delwarp", "删除公共传送点",
                new WarpCommand(this, teleportManager, "delwarp"));
        registrar.register("warps", "列出所有公共传送点",
                new WarpCommand(this, teleportManager, "warps"));

        // /tpa /tpahere /tpaccept /tpdeny
        registrar.register("tpa", "请求传送到某玩家身边",
                new TpaCommand(this, tpaManager, "tpa"));
        registrar.register("tpahere", "请求某玩家传送到你身边",
                new TpaCommand(this, tpaManager, "tpahere"));
        registrar.register("tpaccept", "接受传送请求",
                new TpaCommand(this, tpaManager, "tpaccept"));
        registrar.register("tpdeny", "拒绝传送请求",
                new TpaCommand(this, tpaManager, "tpdeny"));
    }

    private void setupTrashBin() {
        if (!configManager.trashBin().isEnabled()) {
            getLogger().info("垃圾桶：已按配置关闭。");
            return;
        }
        trashBinManager = new TrashBinManager(this, configManager);
        getServer().getPluginManager().registerEvents(new TrashBinListener(this), this);
        getLogger().info("垃圾桶系统已启动（/trashbin 打开）。");
    }

    private void setupFloorClean() {
        if (!configManager.clean().isEnabled()) {
            getLogger().info("自动扫地：已按配置关闭。");
            return;
        }
        floorCleanManager = new FloorCleanManager(this, configManager, trashBinManager);
        getLogger().info("扫地系统已启动（周期 " + floorCleanManager.formatTime() + "，"
                + (floorCleanManager.getConfig().isToTrashBin() && trashBinManager != null
                        ? "物品收入垃圾桶" : "物品直接删除") + "）。");
    }

    private void setupChair() {
        if (!configManager.chair().isEnabled()) {
            getLogger().info("椅子：已按配置关闭。");
            return;
        }
        chairManager = new ChairManager(this, configManager);
        getServer().getPluginManager().registerEvents(new ChairListener(this, configManager, chairManager), this);
    }

    private void setupMenu() {
        if (!configManager.menu().isEnabled()) {
            getLogger().info("主菜单：已按配置关闭（/menu 将提示未启用）。");
            return;
        }
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        getLogger().info("主菜单已启动（/menu 或 /m）。");
    }

    /**
     * 传送系统（/back /home /warp）：初始化 SQLite 数据层并注册监听器。
     * 命令在 {@link #registerCommands()} 中统一注册（此时 teleportManager 已存在）。
     */
    private void setupTeleport() {
        if (!configManager.teleport().isEnabled()) {
            getLogger().info("传送系统：已按配置关闭。");
            return;
        }
        teleportManager = new TeleportManager(this, configManager.teleport());
        if (!teleportManager.isStorageAvailable()) {
            getLogger().warning("传送数据库初始化失败，/back /home /warp 将不可用。");
        }
        // 玩家间传送请求（会话态，不入库；与传送系统同生命周期）
        tpaManager = new TpaManager(this, configManager.teleport(), teleportManager);

        getServer().getPluginManager().registerEvents(new TeleportListener(teleportManager, configManager.teleport()), this);
        getServer().getPluginManager().registerEvents(new TeleportGuiListener(this, teleportManager), this);
        getServer().getPluginManager().registerEvents(new TpaListener(tpaManager), this);

        getLogger().info("传送系统已启动（/back /home /warp"
                + (tpaManager.isEnabled() ? " /tpa /tpahere" : "") + "，数据存储于 SQLite）。");
    }

    private void setupPlaceholders(boolean papiOk) {
        if (!papiOk) {
            getLogger().warning("PlaceholderAPI 未安装，扫地倒计时占位符不可用。");
            return;
        }
        if (floorCleanManager != null) {
            new CleanTimePlaceholder(this).register();
            getLogger().info("PlaceholderAPI 占位符 %stf_cleantime% 注册成功！");
        }
    }

    // ==================== 重载 ====================

    /**
     * 重载全部配置。注意：开关状态变更需要重启服务器才能完全生效
     * （监听器注册不可逆），参数类修改立即生效。
     */
    public boolean reloadAll() {
        try {
            configManager.reload();
            return true;
        } catch (Throwable t) {
            getLogger().warning("重载配置失败：" + t.getMessage());
            return false;
        }
    }

    /** 便捷取消息文案并解析 */
    public Component getMessage(String key, String fallback) {
        return configManager.messages().component(key, fallback);
    }

    /** 取原始文案（未解析） */
    public String getRawMessage(String key, String fallback) {
        return configManager.messages().raw(key, fallback);
    }

    /** 取原始文案列表（未解析） */
    public List<String> getRawMessageList(String key, List<String> fallback) {
        List<String> list = configManager.messages().rawList(key);
        return list.isEmpty() ? fallback : list;
    }

    // ==================== 访问器 ====================

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public FloorCleanManager getFloorCleanManager() {
        return floorCleanManager;
    }

    public ChairManager getChairManager() {
        return chairManager;
    }

    public TrashBinManager getTrashBinManager() {
        return trashBinManager;
    }

    public TeleportManager getTeleportManager() {
        return teleportManager;
    }

    /** 玩家传送请求管理器；传送系统关闭时为 null */
    public TpaManager getTpaManager() {
        return tpaManager;
    }
}
