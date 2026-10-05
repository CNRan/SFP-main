package cn.starfallplain.sfpmain.punish;

import java.util.UUID;

/**
 * 一条处罚历史记录（{@code punishment_logs} 表的一行）。
 * <p>
 * 与 {@link Punishment} 的区别：日志是只增不删的历史，额外记录
 * 处罚 ID 对应的动作（施加 / 解除 / 到期）及其发生时间。
 *
 * @param logId        自增主键
 * @param punishmentId 关联的处罚 ID
 * @param playerUuid   玩家 UUID
 * @param playerName   玩家名
 * @param type         处罚类型
 * @param reason       原因
 * @param operator     操作者
 * @param createdAt    处罚时间（毫秒时间戳）
 * @param expireAt     原定到期时间（毫秒时间戳；-1 永久）
 * @param action       本条日志的动作
 * @param actionAt     动作发生时间（毫秒时间戳）
 */
public record PunishLog(long logId, String punishmentId, UUID playerUuid, String playerName,
                        PunishmentType type, String reason, String operator,
                        long createdAt, long expireAt, PunishAction action, long actionAt) {
}
