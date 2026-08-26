package com.jhds.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jhds.entity.Equipment;
import org.apache.ibatis.annotations.Select;

public interface EquipmentMapper extends BaseMapper<Equipment> {

    @Select("SELECT * FROM equipment WHERE alias = #{alias} LIMIT 1")
    Equipment selectByAlias(String alias);
}
