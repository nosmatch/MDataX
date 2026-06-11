package com.mogu.data.schedulerx.util;

import java.util.concurrent.ThreadLocalRandom;

/**
 * ID 生成器
 *
 * @author fengzhu
 */
public class IdGenerator {

    /**
     * 生成 DAG 实例 ID
     */
    public static String generateDagInstanceId() {
        return "DI" + System.nanoTime() + random(6);
    }

    /**
     * 生成任务实例 ID
     */
    public static String generateTaskInstanceId() {
        return "TI" + System.nanoTime() + random(6);
    }

    private static String random(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(ThreadLocalRandom.current().nextInt(10));
        }
        return sb.toString();
    }

}
