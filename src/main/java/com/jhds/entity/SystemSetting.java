package com.jhds.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/** Persisted application setting that must survive a Redis restart. */
@Data
@TableName("system_setting")
public class SystemSetting {
    @TableId
    private String settingKey;
    private String settingValue;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updatedAt;
}
