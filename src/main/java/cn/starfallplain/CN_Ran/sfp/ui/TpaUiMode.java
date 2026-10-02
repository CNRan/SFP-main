package cn.starfallplain.CN_Ran.sfp.ui;

/**
 * 传送请求「回应界面」的形式偏好（独立于主菜单的 dialogUI/箱子偏好）。
 * <ul>
 *   <li>{@link #DIALOG} —— Paper 弹窗（带接受 / 拒绝 / 切换按钮）</li>
 *   <li>{@link #TUI} —— 聊天里的可点击按钮</li>
 * </ul>
 * 默认 DIALOG；在回应界面里点「切换界面」按钮即可切换（见 TpaManager#switchUi）。
 */
public enum TpaUiMode {

    DIALOG("dialog"),
    TUI("tui");

    private final String key;

    TpaUiMode(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /** 从标识解析；非 tui 一律视为 dialog（默认值） */
    public static TpaUiMode fromKey(String key) {
        if (key != null && "tui".equalsIgnoreCase(key)) {
            return TUI;
        }
        return DIALOG;
    }

    /** 切换：返回另一种形式 */
    public TpaUiMode toggle() {
        return this == DIALOG ? TUI : DIALOG;
    }
}
