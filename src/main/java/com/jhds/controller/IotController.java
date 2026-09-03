package com.jhds.controller;

import com.jhds.common.Result;
import com.jhds.entity.Equipment;
import com.jhds.service.IotService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Api(tags = "物联设备模块")
@RestController
@RequestMapping("/api/iot")
public class IotController {

    @Autowired
    private IotService iotService;

    @Autowired
    private com.jhds.service.mqtt.MqttService mqttService;

    @ApiOperation("获取有人云 MQTT 连接状态")
    @GetMapping("/mqtt-status")
    public Result<Map<String, Object>> mqttStatus() {
        return Result.ok(mqttService.connectionStatus());
    }

    @ApiOperation("获取所有大棚设备")
    @GetMapping("/devices")
    public Result<List<Equipment>> getDevices() {
        return Result.ok(iotService.getDevices());
    }

    @ApiOperation("控制设备开关")
    @PutMapping("/device/{alias}")
    public Result<String> controlDevice(@PathVariable String alias, @RequestBody Map<String, Integer> body) {
        return Result.ok(iotService.controlDevice(alias, body.get("status")));
    }
}
