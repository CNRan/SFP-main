package cn.starfallplain.CN_Ran.sfp.bot;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.UUID;

/**
 * 通过 NMS 反射创建**真正的玩家实体**（ServerPlayer 假人）。
 * <p>
 * Bukkit/Paper 没有创建 Player 的公开 API（{@code org.bukkit.entity.NPC} 只是个标记接口），
 * 所以这里走的是 Carpet 假人那条路：
 * <pre>
 *   ServerPlayer(server, level, GameProfile, ClientInformation)
 *   Connection(PacketFlow.SERVERBOUND)              ← 一条不会真正连网络的本地连接
 *   ServerGamePacketListenerImpl(server, connection, player, cookie)
 *   player.connection = listener                    ← 该字段是 public
 *   PlayerList#placeNewPlayer(connection, player, cookie)   ← 走服务端自己的「玩家加入」流程
 * </pre>
 * 加入成功后它会出现在在线玩家列表里，并天然保持周围区块加载。
 * <p>
 * <b>这里的类名与方法签名是按 Paper 26.3 的 NMS 实地核对过的</b>（用服务端
 * versions/&lt;ver&gt;/paper-*.jar + libraries 组成完整类路径，反射打印签名），
 * 但仍然属于 NMS 层面：Minecraft 大版本更新后签名可能变化，届时看异常堆栈即可定位。
 * <p>
 * 所有失败都抛出异常，由 {@link BotManager} 捕获并回显给执行者，不影响服务器运行。
 */
final class NmsBotFactory {

    private static final String CRAFT_SERVER = "org.bukkit.craftbukkit.CraftServer";
    private static final String NMS_SERVER = "net.minecraft.server.MinecraftServer";
    private static final String NMS_LEVEL = "net.minecraft.server.level.ServerLevel";
    private static final String NMS_PLAYER = "net.minecraft.server.level.ServerPlayer";
    private static final String CLIENT_INFO = "net.minecraft.server.level.ClientInformation";
    private static final String PACKET_FLOW = "net.minecraft.network.protocol.PacketFlow";
    private static final String CONNECTION = "net.minecraft.network.Connection";
    private static final String LISTENER = "net.minecraft.server.network.ServerGamePacketListenerImpl";
    private static final String COOKIE = "net.minecraft.server.network.CommonListenerCookie";
    private static final String GAME_PROFILE = "com.mojang.authlib.GameProfile";

    private NmsBotFactory() {
    }

    /**
     * 在指定位置创建一个假人。
     *
     * @param skinSource 皮肤来源玩家；null 表示用默认皮肤
     * @return 创建出来的在线玩家对象（服务端已把它当作真玩家）
     * @throws Exception 任一步骤失败（NMS 签名变化、世界未加载等）
     */
    static Player spawn(JavaPlugin plugin, Player skinSource, World world, Location loc,
                        String name, UUID uuid) throws Exception {
        Object craftServer = Bukkit.getServer();
        Object nmsServer = invoke(craftServer, "getServer");
        Object playerList = invoke(craftServer, "getHandle");
        Object nmsLevel = invoke(world, "getHandle");

        // 1) GameProfile（可附带皮肤的签名属性）
        Class<?> profileClass = Class.forName(GAME_PROFILE);
        Object profile = profileClass.getConstructor(UUID.class, String.class).newInstance(uuid, name);
        if (skinSource != null) {
            copyTextures(plugin, skinSource, profile, profileClass);
        }

        // 2) ClientInformation.createDefault()
        Class<?> clientInfoClass = Class.forName(CLIENT_INFO);
        Object clientInfo = clientInfoClass.getMethod("createDefault").invoke(null);

        // 3) ServerPlayer(server, level, profile, clientInfo)
        Class<?> nmsServerClass = Class.forName(NMS_SERVER);
        Class<?> nmsLevelClass = Class.forName(NMS_LEVEL);
        Class<?> nmsPlayerClass = Class.forName(NMS_PLAYER);
        Object serverPlayer = nmsPlayerClass
                .getConstructor(nmsServerClass, nmsLevelClass, profileClass, clientInfoClass)
                .newInstance(nmsServer, nmsLevel, profile, clientInfo);

        // 位置：实体加入世界前先摆到目标坐标（方法名在映射里很稳定，仍做兜底）
        try {
            nmsPlayerClass.getMethod("setPos", double.class, double.class, double.class)
                    .invoke(serverPlayer, loc.getX(), loc.getY(), loc.getZ());
        } catch (NoSuchMethodException e) {
            plugin.getLogger().warning("未找到 setPos(double,double,double)，假人 " + name
                    + " 可能出现在默认位置。");
        }

        // 4) 本地假连接 + 数据包监听器
        Class<?> packetFlowClass = Class.forName(PACKET_FLOW);
        Object serverbound = enumValue(packetFlowClass, "SERVERBOUND");
        Class<?> connectionClass = Class.forName(CONNECTION);
        Object connection = connectionClass.getConstructor(packetFlowClass).newInstance(serverbound);

        // 关键一步：给假连接装上 netty channel。
        // placeNewPlayer 会向这个连接写登录相关的数据包，Connection.channel 为 null 时会在
        // channel.writeAndFlush(...) 处 NPE（真机验证时就是这么失败的）。
        // EmbeddedChannel 是 netty 自带的测试用 channel：它是「已连接」状态，写入只会进
        // 自己的 outbound 队列而不会真的发网络 —— 正好当作一个吞包的黑洞。
        // 队列会由 BotManager 定期清空，避免长期运行堆积。
        Class<?> embeddedChannelClass = Class.forName("io.netty.channel.embedded.EmbeddedChannel");
        Object channel = embeddedChannelClass.getConstructor().newInstance();
        // Connection.channel 是 public 字段
        connectionClass.getField("channel").set(connection, channel);

        Class<?> cookieClass = Class.forName(COOKIE);
        Object cookie = cookieClass.getMethod("createInitial", profileClass, boolean.class)
                .invoke(null, profile, false);

        Class<?> listenerClass = Class.forName(LISTENER);
        Object listener = listenerClass
                .getConstructor(nmsServerClass, connectionClass, nmsPlayerClass, cookieClass)
                .newInstance(nmsServer, connection, serverPlayer, cookie);
        // connection 字段是 public 的
        Field connectionField = nmsPlayerClass.getField("connection");
        connectionField.set(serverPlayer, listener);

        // 5) 走服务端自己的加入流程；它会同时把玩家计入在线列表并加载周围区块
        try {
            playerList.getClass().getMethod("placeNewPlayer", connectionClass, nmsPlayerClass, cookieClass)
                    .invoke(playerList, connection, serverPlayer, cookie);
        } catch (Throwable t) {
            // 兜底：只把它塞进世界（能加载区块与 tick，但可能不计入在线玩家列表）
            plugin.getLogger().warning("假人 " + name + " 走 placeNewPlayer 失败，退回 addNewPlayer："
                    + rootCause(t));
            nmsLevelClass.getMethod("addNewPlayer", nmsPlayerClass).invoke(nmsLevel, serverPlayer);
        }

        Player bot = Bukkit.getPlayer(uuid);
        if (bot == null) {
            throw new IllegalStateException("假人已加入服务端但取不到 Bukkit 玩家对象（uuid=" + uuid + "）");
        }
        return bot;
    }

    /**
     * 清空假连接上积压的待发送数据包。
     * <p>
     * EmbeddedChannel 会把服务端要发给「客户端」的包堆在自己的 outbound 队列里，
     * 假人没有真正的客户端来消费，长期运行会一直堆积，所以要定期清一次。
     */
    static void drainPendingPackets(Player bot) {
        try {
            Object nmsPlayer = invoke(bot, "getHandle");
            if (nmsPlayer == null) return;
            Object connection = nmsPlayer.getClass().getField("connection").get(nmsPlayer);
            if (connection == null) return;
            Object channel = connection.getClass().getField("channel").get(connection);
            if (channel == null) return;
            Object queue = channel.getClass().getMethod("outboundMessages").invoke(channel);
            if (queue instanceof Collection<?> collection) collection.clear();
        } catch (Throwable ignored) {
            // 清理失败不影响假人本身，下一轮再试
        }
    }

    /** 把来源玩家的皮肤（textures 属性）复制到目标 profile 上 */
    @SuppressWarnings("unchecked")
    private static void copyTextures(JavaPlugin plugin, Player source, Object targetProfile,
                                     Class<?> profileClass) {
        try {
            Object sourceProfile = invoke(source, "getProfile");
            if (sourceProfile == null) return;
            Object sourceProps = profileClass.getMethod("getProperties").invoke(sourceProfile);
            Object targetProps = profileClass.getMethod("getProperties").invoke(targetProfile);

            Method get = findMethod(sourceProps.getClass(), "get", 1);
            Method put = findMethod(targetProps.getClass(), "put", 2);
            if (get == null || put == null) return;

            Object textures = get.invoke(sourceProps, "textures");
            if (!(textures instanceof Collection<?> collection) || collection.isEmpty()) return;
            for (Object property : collection) {
                put.invoke(targetProps, "textures", property);
            }
        } catch (Throwable t) {
            // 皮肤只是外观，失败不影响假人本身
            plugin.getLogger().warning("复制假人皮肤失败（改用默认皮肤）：" + rootCause(t));
        }
    }

    // ==================== 反射工具 ====================

    /** 调用无参方法 */
    private static Object invoke(Object target, String method) throws Exception {
        return findMethod(target.getClass(), method, 0).invoke(target);
    }

    /** 调用指定参数类型的方法 */
    private static Object invoke(Object target, String method, Class<?>... params) throws Exception {
        return target.getClass().getMethod(method, params).invoke(target);
    }

    private static Method findMethod(Class<?> type, String name, int paramCount) {
        for (Method m : type.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == paramCount) return m;
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Class<?> enumClass, String constant) {
        return Enum.valueOf((Class<Enum>) enumClass, constant);
    }

    /** 取最底层原因，日志里更有用 */
    private static String rootCause(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
        return cur.getClass().getName() + ": " + cur.getMessage();
    }
}
