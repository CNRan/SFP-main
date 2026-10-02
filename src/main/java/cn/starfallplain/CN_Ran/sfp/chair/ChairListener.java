package cn.starfallplain.CN_Ran.sfp.chair;

import cn.starfallplain.CN_Ran.sfp.config.ConfigManager;
import cn.starfallplain.CN_Ran.sfp.config.Messages;
import cn.starfallplain.CN_Ran.sfp.config.module.ChairConfig;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;

/**
 * 椅子交互监听器。起身触发方式由 chair.yml 的 stand-on.* 决定。
 */
public class ChairListener implements Listener {

    private final JavaPlugin plugin;
    private final ChairManager chairManager;
    private final ChairConfig config;
    private final Messages messages;

    public ChairListener(JavaPlugin plugin, ConfigManager configManager, ChairManager chairManager) {
        this.plugin = plugin;
        this.config = configManager.chair();
        this.messages = configManager.messages();
        this.chairManager = chairManager;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        Player player = event.getPlayer();

        // 如果玩家正在坐着，且点击的是当前座位方块，则站起来
        if (chairManager.isSitting(player) && config.isEjectOnReclick()) {
            Block currentChair = chairManager.getChairBlock(player);
            if (currentChair != null && currentChair.equals(block)) {
                chairManager.standPlayer(player);
                return;
            }
        }

        // 检查是否是椅子结构
        if (chairManager.isChairBlock(block)) {
            // 检查该方块是否已被其他玩家占用
            if (chairManager.isBlockInUse(block)) {
                player.sendMessage(messages.component("chair.occupied", "<yellow>这个座位已经有人了！</yellow>"));
                event.setCancelled(true);
                return;
            }
            event.setCancelled(true);
            chairManager.sitPlayer(player, block);
        }
    }

    @EventHandler
    public void onEntityDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (chairManager.isSitting(player)) {
            chairManager.standPlayer(player);
        }
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!config.isStandOnTeleport()) return;
        if (chairManager.isSitting(event.getPlayer())) {
            chairManager.standPlayer(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        chairManager.onQuit(event.getPlayer());
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!config.isStandOnBreak()) return;
        Block broken = event.getBlock();
        // 收集所有需要站起来的玩家（避免并发修改）
        Set<Player> toStand = new HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!chairManager.isSitting(p)) continue;
            Block chairBlock = chairManager.getChairBlock(p);
            if (chairBlock == null) continue;

            // 如果破坏的是椅子中心方块或其相邻方块（可能是告示牌）
            if (broken.equals(chairBlock) || isAdjacent(chairBlock, broken)) {
                toStand.add(p);
            }
        }
        for (Player p : toStand) {
            chairManager.standPlayer(p);
        }
    }

    @EventHandler
    public void onPlayerKick(PlayerKickEvent event) {
        // 防止原版反作弊因坐在盔甲架上踢出玩家
        Player player = event.getPlayer();
        if (!chairManager.isSitting(player)) return;
        String reason = event.getReason() == null ? "" : event.getReason().toLowerCase();
        if (reason.contains("flying") || reason.contains("飞行")
                || reason.contains("moving too fast") || reason.contains("移动过快")) {
            event.setCancelled(true);
        }
    }

    private boolean isAdjacent(Block a, Block b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) + Math.abs(a.getZ() - b.getZ()) == 1;
    }
}
