package cn.starfallplain.CN_Ran.sfp.teleport.db;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite 连接与表结构管理。
 * <p>
 * 数据库文件位于插件数据目录（默认 teleport.db）。
 * 采用单连接 + 同步访问（传送数据量小、写入频率低，无需连接池）。
 * 表结构：
 * <ul>
 *   <li>{@code homes} —— 个人家：玩家 UUID + 家名 + 位置</li>
 *   <li>{@code warps} —— 公共传送点：传送点名 + 位置</li>
 *   <li>{@code last_locations} —— /back 记录：玩家 UUID + 最后位置</li>
 * </ul>
 */
public final class Database {

    private final JavaPlugin plugin;
    private final File dbFile;
    private final boolean autoCommit;

    private Connection connection;

    public Database(JavaPlugin plugin, String fileName, boolean autoCommit) {
        this.plugin = plugin;
        this.autoCommit = autoCommit;
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("无法创建插件数据目录，传送数据库可能无法保存。");
        }
        this.dbFile = new File(plugin.getDataFolder(), fileName);
        connect();
        createTables();
    }

    /** 建立连接并加载驱动 */
    private void connect() {
        try {
            // 显式加载 SQLite 驱动（Paper 插件类加载器下更稳妥）
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("未找到 SQLite JDBC 驱动，传送功能将不可用：" + e.getMessage());
        }
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
                // WAL 提升并发读写表现（单文件场景安全）
                st.execute("PRAGMA journal_mode = WAL");
            }
            connection.setAutoCommit(autoCommit);
        } catch (SQLException e) {
            plugin.getLogger().severe("无法连接传送数据库：" + e.getMessage());
            connection = null;
        }
    }

    /** 建表（IF NOT EXISTS，重复启动安全） */
    private void createTables() {
        if (connection == null) return;
        String homes = """
                CREATE TABLE IF NOT EXISTS homes (
                    player_uuid TEXT NOT NULL,
                    home_name   TEXT NOT NULL,
                    world       TEXT NOT NULL,
                    x           REAL NOT NULL,
                    y           REAL NOT NULL,
                    z           REAL NOT NULL,
                    yaw         REAL NOT NULL DEFAULT 0,
                    pitch       REAL NOT NULL DEFAULT 0,
                    created_at  INTEGER NOT NULL,
                    PRIMARY KEY (player_uuid, home_name)
                )""";
        String warps = """
                CREATE TABLE IF NOT EXISTS warps (
                    warp_name  TEXT PRIMARY KEY,
                    world      TEXT NOT NULL,
                    x          REAL NOT NULL,
                    y          REAL NOT NULL,
                    z          REAL NOT NULL,
                    yaw        REAL NOT NULL DEFAULT 0,
                    pitch      REAL NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL
                )""";
        String lastLoc = """
                CREATE TABLE IF NOT EXISTS last_locations (
                    player_uuid TEXT PRIMARY KEY,
                    world       TEXT NOT NULL,
                    x           REAL NOT NULL,
                    y           REAL NOT NULL,
                    z           REAL NOT NULL,
                    yaw         REAL NOT NULL DEFAULT 0,
                    pitch       REAL NOT NULL DEFAULT 0,
                    updated_at  INTEGER NOT NULL
                )""";
        try (Statement st = connection.createStatement()) {
            st.executeUpdate(homes);
            st.executeUpdate(warps);
            st.executeUpdate(lastLoc);
        } catch (SQLException e) {
            plugin.getLogger().severe("创建传送数据表失败：" + e.getMessage());
        }
    }

    /** 获取连接，若已断开则尝试重连 */
    public synchronized Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connect();
            }
        } catch (SQLException e) {
            connect();
        }
        return connection;
    }

    /** 是否可用 */
    public boolean isAvailable() {
        return getConnection() != null;
    }

    /** 手动提交（auto-commit 关闭时使用） */
    public synchronized void commit() {
        if (connection == null || autoCommit) return;
        try {
            connection.commit();
        } catch (SQLException e) {
            plugin.getLogger().warning("提交传送数据失败：" + e.getMessage());
        }
    }

    /** 关闭连接（插件卸载时调用） */
    public synchronized void close() {
        if (connection == null) return;
        try {
            if (!autoCommit && !connection.isClosed()) {
                connection.commit();
            }
            connection.close();
        } catch (SQLException e) {
            plugin.getLogger().warning("关闭传送数据库失败：" + e.getMessage());
        } finally {
            connection = null;
        }
    }
}
