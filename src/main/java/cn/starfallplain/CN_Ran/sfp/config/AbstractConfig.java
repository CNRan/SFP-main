package cn.starfallplain.CN_Ran.sfp.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 配置文件基类。
 * <p>
 * 约定：每个功能包拥有一份独立配置文件（config/&lt;包名&gt;.yml），
 * 包内所有可调项（开关、参数、图标、槽位、文案）统一由该文件驱动，
 * 读取逻辑下沉到各包自己的 Config 子类。
 * <p>
 * 子类通过 {@link #getString(String)} 等 get* 方法读取自身文件的键；
 * 键既可用带前缀的完整路径（menu.sign.slot），也可用不含文件名前缀的短路径（sign.slot）。
 */
public abstract class AbstractConfig {

    protected final JavaPlugin plugin;
    private final String fileName;
    private File file;
    private FileConfiguration config;

    protected AbstractConfig(JavaPlugin plugin, String fileName) {
        this.plugin = plugin;
        this.fileName = fileName.endsWith(".yml") ? fileName : fileName + ".yml";
        load();
    }

    /** 配置文件名（含 .yml），用于日志与消息文件定位 */
    public final String getFileName() {
        return fileName;
    }

    // ==================== 加载 / 保存 ====================

    /**
     * 从磁盘加载；不存在时写出 jar 内置默认值。
     */
    public final void load() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("无法创建插件数据目录，配置 " + fileName + " 可能无法保存。");
        }
        file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            try {
                plugin.saveResource(fileName, false);
            } catch (IllegalArgumentException e) {
                // jar 内没有该资源（例如用户自定义文件），创建空文件
                try {
                    if (file.createNewFile()) {
                        plugin.getLogger().info("已创建空配置 " + fileName);
                    }
                } catch (IOException ex) {
                    plugin.getLogger().warning("无法创建配置 " + fileName + "：" + ex.getMessage());
                }
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
        mergeDefaults();
        onLoaded();
    }

    /**
     * 用 jar 内置默认值补齐文件缺失的键（不覆盖用户已填写的值）。
     * 这样老版本配置文件升级后新键也能生效，无需手动补。
     */
    private void mergeDefaults() {
        InputStream in = plugin.getResource(fileName);
        if (in == null) return;
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                new InputStreamReader(in, StandardCharsets.UTF_8));
        config.setDefaults(defaults);
        config.options().copyDefaults(true);
        save();
    }

    /** 子类加载完成后的钩子（用于读取并缓存字段） */
    protected void onLoaded() {
    }

    public final void save() {
        if (file == null || config == null) return;
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("无法保存配置 " + fileName + "：" + e.getMessage());
        }
    }

    // ==================== 读取（兼容带/不带文件名前缀的键） ====================

    protected final String normalize(String path) {
        String prefix = fileName.substring(0, fileName.length() - 4) + ".";
        if (path.startsWith(prefix)) {
            return path.substring(prefix.length());
        }
        return path;
    }

    public final boolean isSet(String path) {
        return config.isSet(normalize(path));
    }

    public final boolean getBoolean(String path, boolean def) {
        return config.getBoolean(normalize(path), def);
    }

    public final int getInt(String path, int def) {
        return config.getInt(normalize(path), def);
    }

    public final long getLong(String path, long def) {
        return config.getLong(normalize(path), def);
    }

    public final double getDouble(String path, double def) {
        return config.getDouble(normalize(path), def);
    }

    public final String getString(String path, String def) {
        String value = config.getString(normalize(path));
        return value != null ? value : def;
    }

    public final List<String> getStringList(String path) {
        return config.getStringList(normalize(path));
    }

    public final List<Integer> getIntegerList(String path) {
        return config.getIntegerList(normalize(path));
    }

    public final List<?> getList(String path) {
        return config.getList(normalize(path));
    }

    public final FileConfiguration raw() {
        return config;
    }
}
