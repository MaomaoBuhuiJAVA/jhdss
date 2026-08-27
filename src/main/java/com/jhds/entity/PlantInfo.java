package com.jhds.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/** 基础植株档案。 */
@Data
@TableName("plant_info")
public class PlantInfo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String plantName;
    private String scientificName;
    private String familyGenus;
    private String variety;
    private String sourceType;
    private String sourceChannel;
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date plantDate;
    private String plantLocation;
    private String soilType;
    private String substrateRatio;
    private String lightEnv;
    private String plantingSpec;
    private String mainPhoto;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private Date createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updatedAt;
}
