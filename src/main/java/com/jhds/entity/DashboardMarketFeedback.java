package com.jhds.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/** Market feedback summary and detail modal content for the dashboard. */
@Data
@TableName("dashboard_market_feedback")
public class DashboardMarketFeedback {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String summary;
    private String modalTitle;
    private String content;
    private Integer enabled;
    private Integer sortOrder;
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updatedAt;
}
