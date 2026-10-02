package cn.starfallplain.CN_Ran.sfp.config.module;

import cn.starfallplain.CN_Ran.sfp.config.AbstractConfig;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 主菜单配置（menu.yml）：
 * 标题、行数、边框玻璃，以及每个按钮的槽位/材质/名称/描述/动作。
 * <p>
 * 按钮在 menu.yml 中以 {@code buttons.<id>} 定义，每个按钮字段：
 * <pre>
 *   enabled        是否显示
 *   slot           所在槽位（行数决定的上限内）
 *   material       Bukkit Material
 *   name           显示名（MiniMessage）
 *   lore           描述（MiniMessage 列表，可空）
 *   command        点击执行的命令（不含斜杠；留空表示由插件内部处理）
 * </pre>
 */
public final class MenuConfig extends AbstractConfig {

    /**
     * 单个按钮定义。
     */
    public static final class Button {
        private final String id;
        private final boolean enabled;
        private final int slot;
        private final Material material;
        private final String name;
        private final List<String> lore;
        private final String command;

        Button(String id, boolean enabled, int slot, Material material,
               String name, List<String> lore, String command) {
            this.id = id;
            this.enabled = enabled;
            this.slot = slot;
            this.material = material;
            this.name = name;
            this.lore = lore;
            this.command = command;
        }

        public String getId() { return id; }

        public boolean isEnabled() { return enabled; }

        public int getSlot() { return slot; }

        public Material getMaterial() { return material; }

        public String getName() { return name; }

        public List<String> getLore() { return lore; }

        public String getCommand() { return command; }

        /** 该按钮是否可见（所属模块开关由 MenuManager 另行判断） */
        public boolean isVisible() {
            return enabled;
        }
    }

    // 主菜单
    private boolean enabled;
    private String title;
    private int rows;
    private boolean fillBorder;
    private List<Material> borderMaterials;

    // 按钮
    private final Map<String, Button> buttons = new LinkedHashMap<>();

    // 垃圾桶按钮 id（用于绑定模块开关）
    private String trashBinButtonId;
    // 传送相关按钮 id（home / warp / back）
    private String homeButtonId;
    private String warpButtonId;
    private String backButtonId;

    // 玩家头颅位置
    private boolean showPlayerHead;
    private int playerHeadSlot;

    // 权限
    private String openPermission;
    private String permissionMessage;

    public MenuConfig(JavaPlugin plugin) {
        super(plugin, "menu.yml");
    }

    @Override
    protected void onLoaded() {
        enabled = getBoolean("enabled", true);
        title = getString("title", "<rainbow><b><i>星落平原 菜单</i></b></rainbow>");
        rows = Math.max(1, Math.min(6, getInt("rows", 3)));
        fillBorder = getBoolean("border.fill", true);
        borderMaterials = readMaterials("border.materials");
        if (borderMaterials.isEmpty()) {
            borderMaterials = List.of(Material.GRAY_STAINED_GLASS_PANE);
        }

        showPlayerHead = getBoolean("player-head.enabled", true);
        playerHeadSlot = getInt("player-head.slot", 4);

        trashBinButtonId = getString("bind.trashbin-button", "trashbin");
        homeButtonId = getString("bind.home-button", "home");
        warpButtonId = getString("bind.warp-button", "warp");
        backButtonId = getString("bind.back-button", "back");

        openPermission = getString("permission", "sfpmenu.player");
        permissionMessage = getString("permission-message", "你没有权限使用此命令");

        buttons.clear();
        var section = raw().getConfigurationSection(normalize("buttons"));
        if (section != null) {
            for (String id : section.getKeys(false)) {
                String base = normalize("buttons") + "." + id;
                boolean btnEnabled = getBoolean(base + ".enabled", true);
                int slot = getInt(base + ".slot", -1);
                Material material = readMaterial(base + ".material", Material.STONE);
                String name = getString(base + ".name", id);
                List<String> lore = getStringList(base + ".lore");
                String command = getString(base + ".command", "");
                buttons.put(id, new Button(id, btnEnabled, slot, material, name, lore, command));
            }
        }
    }

    private Material readMaterial(String path, Material def) {
        String raw = getString(path, def.name());
        Material material = Material.matchMaterial(raw.trim());
        if (material == null) {
            plugin.getLogger().warning("menu.yml 中无效的材质 '" + raw + "'（" + path + "），已回退为 " + def.name());
            return def;
        }
        return material;
    }

    private List<Material> readMaterials(String path) {
        List<Material> list = new ArrayList<>();
        for (String raw : getStringList(path)) {
            Material material = Material.matchMaterial(raw.trim());
            if (material != null) {
                list.add(material);
            } else {
                plugin.getLogger().warning("menu.yml 中无效的材质 '" + raw + "'（" + path + "）已跳过");
            }
        }
        return list;
    }

    public boolean isEnabled() { return enabled; }

    public String getTitle() { return title; }

    public int getRows() { return rows; }

    public int getSize() { return rows * 9; }

    public boolean isFillBorder() { return fillBorder; }

    public List<Material> getBorderMaterials() { return borderMaterials; }

    public Map<String, Button> getButtons() { return buttons; }

    public Button getButton(String id) {
        return id == null ? null : buttons.get(id);
    }

    public String getTrashBinButtonId() { return trashBinButtonId; }

    public String getHomeButtonId() { return homeButtonId; }

    public String getWarpButtonId() { return warpButtonId; }

    public String getBackButtonId() { return backButtonId; }

    public boolean isShowPlayerHead() { return showPlayerHead; }

    public int getPlayerHeadSlot() { return playerHeadSlot; }

    public String getOpenPermission() { return openPermission; }

    public String getPermissionMessage() { return permissionMessage; }
}
