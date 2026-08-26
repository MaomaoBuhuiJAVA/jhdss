package com.jhds.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@Data
@TableName("irrigation_record")
public class IrrigationRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String mode;
    private String pumpAlias;
    private Integer duration;
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
}
