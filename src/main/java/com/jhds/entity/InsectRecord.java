package com.jhds.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@Data
@TableName("insect_record")
public class InsectRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String deviceId;
    private String imageUrl;
    private String thumbUrl;
    private String species;
    private Integer count;
    private String aiEngine;
    private Date recordDate;
    private Date recordTime;
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
}
