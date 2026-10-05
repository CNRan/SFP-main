package cn.starfallplain.sfpmain.punish;

/**
 * 处罚类型。
 * <p>
 * 需求里的「未获得处罚」是<b>状态</b>（该玩家当前没有任何生效处罚），不是一种处罚类型。
 * <p>
 * 这里区分两类：
 * <ul>
 *   <li><b>会写 {@code punishments} 生效表</b>的：{@link #BAN}、{@link #MUTE}
 *       —— 有到期时间、进服/发言时会被惰性检查；</li>
 *   <li><b>只写 {@code punishment_logs} 历史表</b>的：{@link #WARN}、{@link #KICK}
 *       —— 一次性动作，没有「生效中」的概念。</li>
 * </ul>
 * {@link #isTimed()} 用于区分这两类。
 */
public enum PunishmentType {

    /** 封禁：禁止登录（有到期时间，写入生效表） */
    BAN("封禁", "ban", true),

    /** 禁言：可登录但不能发言（有到期时间，写入生效表） */
    MUTE("禁言", "mute", true),

    /** 警告：仅提示 + 记历史，不写生效表 */
    WARN("警告", "warn", false),

    /** 踢出：断开当前连接 + 记历史，不写生效表 */
    KICK("踢出", "kick", false);

    private final String displayName;
    private final String key;
    private final boolean timed;

    PunishmentType(String displayName, String key, boolean timed) {
        this.displayName = displayName;
        this.key = key;
        this.timed = timed;
    }

    /** 中文显示名，用于消息与查询结果 */
    public String displayName() {
        return displayName;
    }

    /** 存库用的短键 */
    public String key() {
        return key;
    }

    /** 是否为「有时限、写入生效表」的类型（BAN / MUTE） */
    public boolean isTimed() {
        return timed;
    }

    /** 从存库字符串解析；无法识别返回 null */
    public static PunishmentType fromKey(String key) {
        if (key == null) return null;
        for (PunishmentType type : values()) {
            if (type.key.equalsIgnoreCase(key)) return type;
        }
        return null;
    }
}
