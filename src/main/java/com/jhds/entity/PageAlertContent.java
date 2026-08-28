package com.jhds.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * Content for page-local AI alert cards and their detail dialogs.
 * Images are kept as JSON so the URLs can be edited without a code change.
 */
@Data
@TableName("page_alert_content")
public class PageAlertContent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String alertKey;
    private String title;
    private String summary;
    private String modalTitle;
    private String description;
    private String imagesJson;
    private Integer enabled;
    private Integer sortOrder;
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updatedAt;
}
