package com.mogu.data.metadata.vo;

import lombok.Data;

/**
 * 表权限信息展示对象
 *
 * @author fengzhu
 */
@Data
public class TablePermissionVO {

    private boolean read;

    private boolean write;

}
