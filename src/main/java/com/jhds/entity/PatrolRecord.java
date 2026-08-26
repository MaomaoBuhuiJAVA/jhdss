package com.jhds.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@Data
@TableName("patrol_record")
public class PatrolRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String imageUrl;
    private String trackPosition;
    private Date shootTime;
    private String aiResult;
    private Integer aiStatus;
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
}
