package com.jhds.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/** Pending agricultural task shown in the dashboard sidebar. */
@Data
@TableName("dashboard_todo")
public class DashboardTodo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String weekLabel;
    private String taskName;
    private String actionName;
    private Integer sortOrder;
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updatedAt;
}
