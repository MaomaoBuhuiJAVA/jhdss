package com.jhds.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/** 按月记录栽培管理操作。 */
@Data
@TableName("plant_cultivation")
public class PlantCultivation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long plantId;
    private Integer year;
    private Integer month;
    private String waterFrequency;
    private String fertilize;
    private String pruning;
    private String trellis;
    private String weeding;
    private String repot;
    private String other;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updatedAt;
}
