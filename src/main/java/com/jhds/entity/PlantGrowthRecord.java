package com.jhds.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/** 定期生长观测记录。 */
@Data
@TableName("plant_growth_record")
public class PlantGrowthRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long plantId;
    private Integer year;
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date recordDate;
    private Double heightCm;
    private Double crownWidthCm;
    private Integer leafCount;
    private Integer flowerCount;
    private Integer fruitCount;
    private String photoUrl;
    private String photoNo;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updatedAt;
}
