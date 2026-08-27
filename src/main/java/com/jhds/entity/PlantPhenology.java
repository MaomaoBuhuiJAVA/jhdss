package com.jhds.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/** 物候阶段记录。 */
@Data
@TableName("plant_phenology")
public class PlantPhenology {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long plantId;
    private Integer year;
    private String stage;
    private String phase;
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date eventDate;
    private String description;
    private String photoUrl;
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updatedAt;
}
