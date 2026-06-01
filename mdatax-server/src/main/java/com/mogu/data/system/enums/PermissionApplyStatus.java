package com.mogu.data.system.enums;

import lombok.Getter;

/**
 * 权限申请状态枚举
 *
 * @author fengzhu
 */
@Getter
public enum PermissionApplyStatus {

    PENDING(0, "待审批"),
    APPROVED(1, "已通过"),
    REJECTED(2, "已拒绝");

    private final int code;
    private final String label;

    PermissionApplyStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public static PermissionApplyStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (PermissionApplyStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
