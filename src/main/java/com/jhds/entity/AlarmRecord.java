package com.jhds.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@Data
@TableName("alarm_record")
public class AlarmRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String description;
    private String level;
    private String sourceModule;
    private String location;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
    private Date handledAt;
}
