package cn.starfallplain.CN_Ran.sfp.ui;

/**
 * 玩家界面样式：dialogUI（Paper 弹窗）或 box（箱子界面）。
 * <p>
 * 默认 dialogUI；字符串只存 {@code key()}，未知值一律回退到 dialogUI。
 */
public enum UiMode {

    DIALOG("dialogui"),
    BOX("box");

    private final String key;

    UiMode(String key) {
        this.key = key;
    }

    /** 存储 / 命令里用的标识 */
    public String key() {
        return key;
    }

    /** 从标识解析；非 box 一律视为 dialogui（默认值） */
    public static UiMode fromKey(String key) {
        if (key != null && "box".equalsIgnoreCase(key)) {
            return BOX;
        }
        return DIALOG;
    }

    /** 切换：返回另一种样式 */
    public UiMode toggle() {
        return this == DIALOG ? BOX : DIALOG;
    }
}
