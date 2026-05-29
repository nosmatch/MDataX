package com.mogu.data.quality.enums;

/**
 * 质量等级枚举
 *
 * @author fengzhu
 * @since 2026-05-09
 */
public enum QualityLevel {

    /**
     * 优秀（90-100分）
     */
    EXCELLENT("EXCELLENT", "优秀", 90, 100, "🟢"),

    /**
     * 良好（75-89分）
     */
    GOOD("GOOD", "良好", 75, 89, "🟡"),

    /**
     * 及格（60-74分）
     */
    PASS("PASS", "及格", 60, 74, "🟠"),

    /**
     * 不及格（0-59分）
     */
    FAIL("FAIL", "不及格", 0, 59, "🔴");

    private final String code;
    private final String name;
    private final int minScore;
    private final int maxScore;
    private final String icon;

    QualityLevel(String code, String name, int minScore, int maxScore, String icon) {
        this.code = code;
        this.name = name;
        this.minScore = minScore;
        this.maxScore = maxScore;
        this.icon = icon;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getMinScore() {
        return minScore;
    }

    public int getMaxScore() {
        return maxScore;
    }

    public String getIcon() {
        return icon;
    }

    /**
     * 根据质量分数获取质量等级
     */
    public static QualityLevel fromScore(int score) {
        if (score >= EXCELLENT.minScore && score <= EXCELLENT.maxScore) {
            return EXCELLENT;
        } else if (score >= GOOD.minScore && score <= GOOD.maxScore) {
            return GOOD;
        } else if (score >= PASS.minScore && score <= PASS.maxScore) {
            return PASS;
        } else {
            return FAIL;
        }
    }

    /**
     * 根据code获取枚举
     */
    public static QualityLevel fromCode(String code) {
        for (QualityLevel level : QualityLevel.values()) {
            if (level.getCode().equals(code)) {
                return level;
            }
        }
        throw new IllegalArgumentException("Unknown quality level code: " + code);
    }
}
