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
        if (alias == null || alias.trim().isEmpty() || status == null || (status != 0 && status != 1)) {
            throw new IllegalArgumentException("设备或开关状态无效");
        }
        Equipment equipment = equipmentMapper.selectByAlias(alias);
        if (equipment == null) {
            throw new IllegalArgumentException("设备不存在");
        }
        String value = status == 1 ? "open" : "close";
        String response = mqttService.sendCommand(alias, value, false);

        // A local/demo equipment row may intentionally have no MQTT command
        // code. Persist its desired state so it survives a page refresh.
        if (response == null && (equipment.getOpenCode() == null || equipment.getOpenCode().trim().isEmpty())
                && (equipment.getCloseCode() == null || equipment.getCloseCode().trim().isEmpty())) {
            equipment.setStatus(status);
            equipmentMapper.updateById(equipment);
            return "LOCAL_SAVED";
        }
        if (response == null) {
            throw new IllegalStateException("设备未响应，数据库状态未修改");
        }
        return response;
    }
}
