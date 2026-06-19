package com.mogu.data.integration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ID生成器实体
 *
 * @author fengzhu
 */
@Data
@TableName("id_generator")
public class IdGenerator {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String prefix;

    private LocalDateTime createTime;
}
