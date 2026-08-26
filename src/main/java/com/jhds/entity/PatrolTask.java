package com.jhds.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalTime;
import java.util.Date;

@Data
@TableName("patrol_task")
public class PatrolTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskName;
    private LocalTime executeTime;
    private String patrolRange;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updatedAt;
}
