package com.jhds.controller;

import com.jhds.common.Result;
import com.jhds.service.ModbusMotorService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Api(tags = "Modbus 电机控制")
@RestController
@RequestMapping("/api/modbus/motor")
public class ModbusMotorController {
    @Autowired private ModbusMotorService service;

    @ApiOperation("获取 Modbus 电机连接状态")
    @GetMapping("/status")
    public Result<Map<String,Object>> status() { return Result.ok(service.status()); }

    @ApiOperation("相对运动")
    @PostMapping("/relative")
    public Result<Map<String,Object>> relative(@RequestBody Map<String,Object> body) {
        return Result.ok(service.relative(number(body, "target"), number(body, "speed"), string(body, "direction")));
    }

    @ApiOperation("绝对定位")
    @PostMapping("/absolute")
    public Result<Map<String,Object>> absolute(@RequestBody Map<String,Object> body) {
        int channel = body != null && body.get("channel") != null ? number(body, "channel") : 1;
        return Result.ok(service.absolute(number(body, "position"), number(body, "speed"), channel));
    }

    @PostMapping("/stop")
    public Result<Map<String,Object>> stop() { return Result.ok(service.stop()); }

    @PostMapping("/pump")
    public Result<Map<String,Object>> pump(@RequestBody Map<String,Object> body) {
        boolean enabled = body != null && Boolean.parseBoolean(String.valueOf(body.get("enabled")));
        return Result.ok(service.pump(enabled));
    }

    private int number(Map<String,Object> body, String key) {
        if (body == null || body.get(key) == null) throw new IllegalArgumentException(key + " 不能为空");
        try { return Integer.parseInt(String.valueOf(body.get(key))); } catch (NumberFormatException e) { throw new IllegalArgumentException(key + " 必须是整数"); }
    }
    private String string(Map<String,Object> body, String key) { return body == null || body.get(key) == null ? null : String.valueOf(body.get(key)); }
}
