package com.jhds.common;

public class WindDirectionUtil {

    private static final String[] DIRECTIONS_16 = {
        "北", "北北东", "东北", "东北东",
        "东", "东南东", "东南", "南南东",
        "南", "南南西", "西南", "西南西",
        "西", "西北西", "西北", "北北西"
    };

    private static final String[] DIRECTIONS_8 = {
        "北", "东北", "东", "东南", "南", "西南", "西", "西北"
    };

    public static String toText16(double angle) {
        int idx = (int) Math.round(angle / 22.5) % 16;
        return DIRECTIONS_16[idx];
    }

    public static String toText8(double angle) {
        int idx = (int) Math.round(angle / 45) % 8;
        return DIRECTIONS_8[idx];
    }

    public static String formatWithAngle(double angle) {
        return String.format("%.0f° (%s)", angle, toText16(angle));
    }
}
