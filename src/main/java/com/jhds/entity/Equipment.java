package com.jhds.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("equipment")
public class Equipment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String alias;
    private Integer type;
    private String openCode;
    private String closeCode;
    private String returnOpenCode;
    private String returnCloseCode;
    private Integer status;
}
