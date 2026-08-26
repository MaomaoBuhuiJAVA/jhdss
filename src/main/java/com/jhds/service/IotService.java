package com.jhds.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jhds.entity.Equipment;
import com.jhds.mapper.EquipmentMapper;
import com.jhds.service.mqtt.MqttService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IotService {

    @Autowired
    private EquipmentMapper equipmentMapper;

    @Autowired
    private MqttService mqttService;

    public List<Equipment> getDevices() {
        return equipmentMapper.selectList(
                new LambdaQueryWrapper<Equipment>()
                        .likeRight(Equipment::getAlias, "GH"));
    }

    public String controlDevice(String alias, Integer status) {
        String value = status == 1 ? "open" : "close";
        return mqttService.sendCommand(alias, value, false);
    }
}
