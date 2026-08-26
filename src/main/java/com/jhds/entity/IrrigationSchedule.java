package com.jhds.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalTime;
import java.util.Date;

@Data
@TableName("irrigation_schedule")
public class IrrigationSchedule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalTime scheduleTime;
    private Integer duration;
    private String frequency;
    private Integer enabled;
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updatedAt;
}
