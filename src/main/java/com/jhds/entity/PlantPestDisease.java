package com.jhds.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/** 病虫害及其他逆境记录。 */
@Data
@TableName("plant_pest_disease")
public class PlantPestDisease {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long plantId;
    private Integer year;
    private String recordType;
    private String pestName;
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date occurDate;
    private String symptom;
    private String severity;
    private String measureType;
    private String measure;
    private String effect;
    private String photoUrl;
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updatedAt;
}
