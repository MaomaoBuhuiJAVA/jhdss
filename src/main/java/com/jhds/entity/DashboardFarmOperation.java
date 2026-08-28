package com.jhds.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/** One completed farm operation rendered in the dashboard sidebar. */
@Data
@TableName("dashboard_farm_operation")
public class DashboardFarmOperation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String operationName;
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date operationDate;
    private String iconClass;
    private String colorTheme;
    private Integer sortOrder;
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updatedAt;
}
