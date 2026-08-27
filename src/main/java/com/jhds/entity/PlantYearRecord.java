package com.jhds.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/** 植株某一年度的总结档案。 */
@Data
@TableName("plant_year_record")
public class PlantYearRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long plantId;
    private Integer year;
    private String growthGrade;
    private String annualSummary;
    private String problemReview;
    private String improvementSuggestion;
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updatedAt;
}
