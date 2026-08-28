package com.jhds.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/** Basic greenhouse information rendered on the dashboard. */
@Data
@TableName("dashboard_greenhouse")
public class DashboardGreenhouse {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String greenhouseType;
    private String cropName;
    private String area;
    private Integer plantCount;
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date plantingDate;
    private Integer isPrimary;
    private Integer sortOrder;
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updatedAt;
}
