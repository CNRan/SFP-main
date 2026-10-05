package cn.starfallplain.sfpmain.punish;

import java.util.UUID;

/**
 * 一条当前生效的处罚（{@code punishments} 表的一行）。
 *
 * @param id         处罚 ID（6 位字母数字混合，全局唯一）
 * @param playerUuid 被处罚玩家 UUID
 * @param playerName 被处罚玩家名（处罚当时的名字）
 * @param type       处罚类型（BAN / MUTE）
 * @param reason     处罚原因（原始文本，MiniMessage 由调用方解析）
 * @param operator   执行者名字
 * @param createdAt  处罚时间（毫秒时间戳）
 * @param expireAt   到期时间（毫秒时间戳；{@link #PERMANENT} 表示永久）
 */
public record Punishment(String id, UUID playerUuid, String playerName, PunishmentType type,
                         String reason, String operator, long createdAt, long expireAt) {

    /** 永久处罚的到期时间标记 */
    public static final long PERMANENT = -1L;

    /** 是否永久 */
    public boolean isPermanent() {
        return expireAt == PERMANENT;
    }

    /** 是否已过期（永久处罚永不过期） */
    public boolean isExpired() {
        return !isPermanent() && System.currentTimeMillis() >= expireAt;
    }

    /** 剩余毫秒；永久返回 -1，已过期返回 0 */
    public long remainingMillis() {
        if (isPermanent()) return -1;
        long remain = expireAt - System.currentTimeMillis();
        return Math.max(0, remain);
    }
}
