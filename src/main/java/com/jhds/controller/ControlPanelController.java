package com.jhds.controller;

import com.jhds.common.Result;
import com.jhds.service.ControlPanelService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Api(tags = "蓝牙局域网控制面板")
@RestController
@RequestMapping("/api/control-panel")
public class ControlPanelController {

    @Autowired
    private ControlPanelService controlPanelService;

    @ApiOperation("获取蓝牙局域网控制面板连接状态")
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.ok(controlPanelService.connectionStatus());
    }

    @ApiOperation("控制控制面板前进/后退")
    @PostMapping("/move")
    public Result<Map<String, Object>> move(@RequestBody Map<String, Object> body) {
        String direction = body == null ? null : String.valueOf(body.get("direction"));
        boolean enabled = body != null && Boolean.parseBoolean(String.valueOf(body.get("enabled")));
        return Result.ok(controlPanelService.move(direction, enabled));
    }

    @ApiOperation("控制控制面板水泵")
    @PostMapping("/pump")
    public Result<Map<String, Object>> pump(@RequestBody Map<String, Object> body) {
        boolean enabled = body != null && Boolean.parseBoolean(String.valueOf(body.get("enabled")));
        return Result.ok(controlPanelService.pump(enabled));
    }
}
