package com.mogu.data.integration.util;

/**
 * Cron 表达式工具类
 *
 * Quartz 与 Spring 的 Cron 格式转换：
 * - Quartz: 秒 分 时 日 月 周 [年]，支持 ?
 * - Spring: 秒 分 时 日 月 周，不支持 ?
 *
 * @author fengzhu
 */
public class CronUtils {

    /**
     * 将 Quartz 风格的 Cron 表达式转换为 Spring 可解析的格式
     *
     * @param quartzCron Quartz 风格表达式，可能包含 ? 和 7 位（含年）
     * @return Spring 可解析的 6 位表达式
     */
    public static String convertQuartzToSpringCron(String quartzCron) {
        if (quartzCron == null || quartzCron.isEmpty()) {
            return quartzCron;
        }
        String[] parts = quartzCron.trim().split("\\s+");
        // 去掉第 7 位（年字段）
        if (parts.length >= 7) {
            String[] newParts = new String[6];
            System.arraycopy(parts, 0, newParts, 0, 6);
            parts = newParts;
        }
        // 将 ? 替换为 *
        for (int i = 0; i < parts.length; i++) {
            if ("?".equals(parts[i])) {
                parts[i] = "*";
            }
        }
        return String.join(" ", parts);
    }
}
