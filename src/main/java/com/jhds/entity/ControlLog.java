package com.jhds.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@Data
@TableName("control_log")
public class ControlLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String deviceAlias;
    private String deviceName;
    private String value;
    private Integer automatic;
    private String sendCommand;
    private String returnCommand;
    private Integer success;
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
}
