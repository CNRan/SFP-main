package cn.starfallplain.sfpmain.punish;

/**
 * 处罚日志里记录的动作类型。
 */
public enum PunishAction {

    /** 施加处罚（封禁 / 禁言 / 警告 / 踢出） */
    PUNISH("处罚"),

    /** 解除封禁 */
    UNBAN("解封"),

    /** 解除禁言 */
    UNMUTE("解禁"),

    /** 处罚到期自动失效（由系统写入） */
    EXPIRE("到期自动解除");

    private final String displayName;

    PunishAction(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** 从存库字符串解析；无法识别返回 null */
    public static PunishAction fromKey(String key) {
        if (key == null) return null;
        try {
            return valueOf(key.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
