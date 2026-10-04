package cn.starfallplain.sfpmain.util;

/**
 * 时间格式化工具
 */
public final class TimeUtil {

    private TimeUtil() {
    }

    /**
     * 格式化秒数：x分x秒（小于一分钟时只显示 x秒）
     */
    public static String formatTime(int sec) {
        if (sec < 60) {
            return sec + "秒";
        }
        int min = sec / 60;
        int rest = sec % 60;
        return min + "分" + rest + "秒";
    }
}
