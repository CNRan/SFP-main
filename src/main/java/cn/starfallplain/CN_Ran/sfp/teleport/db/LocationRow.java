package cn.starfallplain.CN_Ran.sfp.teleport.db;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 把查询结果的一行映射成 {@link StoredLocation}。
 * <p>
 * {@code homes} / {@code warps} / {@code last_locations} 三张表的位置列完全一致
 * （{@code world, x, y, z, yaw, pitch}），因此共用一个映射，避免三份重复代码。
 * 仅包内可见。
 */
final class LocationRow {

    private LocationRow() {
    }

    /** 读取 world / x / y / z / yaw / pitch 六列 */
    static StoredLocation read(ResultSet rs) throws SQLException {
        return new StoredLocation(
                rs.getString("world"),
                rs.getDouble("x"),
                rs.getDouble("y"),
                rs.getDouble("z"),
                rs.getFloat("yaw"),
                rs.getFloat("pitch"));
    }
}
