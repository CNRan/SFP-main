package cn.starfallplain.sfpmain.menu;

import cn.starfallplain.sfpmain.SfpMain;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * 菜单钟的右键交互：手持菜单钟右键即打开主菜单。
 * <p>
 * 触发范围由 menu.yml 的 {@code menu-clock.trigger} 决定：
 * <ul>
 *   <li>{@code both}（默认）—— 看向方块或空气都触发，并取消该次方块交互
 *       （避免「拿着钟还想开箱子」这类冲突，行为可预期）</li>
 *   <li>{@code air} —— 只在没看向方块时触发，不干预任何方块交互</li>
 * </ul>
 * 打开菜单统一走 {@link MenuManager#openMenuByPreference}，
 * 因此「权限 / 开关」校验与按玩家偏好选择 dialogUI 或箱子界面的行为与 /menu 完全一致。
 */
public class MenuClockListener implements Listener {

    private final SfpMain plugin;
    private final MenuClockManager manager;

    public MenuClockListener(SfpMain plugin, MenuClockManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        boolean air = action == Action.RIGHT_CLICK_AIR;
        boolean block = action == Action.RIGHT_CLICK_BLOCK;
        if (!air && !block) return;

        // 只处理主手，避免左右手各触发一次
        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemStack item = event.getItem();
        if (!manager.isMenuClock(item)) return;

        // air 模式下不拦截方块交互（例如拿着钟右键箱子仍可正常开箱）
        if (block && "air".equalsIgnoreCase(manager.getConfig().getMenuClockTrigger())) {
            return;
        }

        event.setCancelled(true);

        Player player = event.getPlayer();
        // 主手切换 / 菜单开关 / 权限校验都在 openMenuByPreference → canOpen 里统一处理
        MenuManager.openMenuByPreference(plugin, player);
    }
}
