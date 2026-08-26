package com.jhds.controller;

import com.jhds.common.Result;
import com.jhds.entity.*;
import com.jhds.service.NutrientService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Api(tags = "自动营养液配液模块")
@RestController
@RequestMapping("/api/nutrient")
public class NutrientController {

    @Autowired
    private NutrientService nutrientService;

    @ApiOperation("获取土壤传感器实时数据")
    @GetMapping("/soil")
    public Result<SoilSensorData> getSoilData() {
        return Result.ok(nutrientService.getSoilData());
    }

    @ApiOperation("获取土壤传感器历史数据")
    @GetMapping("/soil/history")
    public Result<List<SoilSensorData>> getSoilHistory(@RequestParam(defaultValue = "2") int days) {
        return Result.ok(nutrientService.getSoilHistory(days));
    }

    @ApiOperation("获取所有泵状态")
    @GetMapping("/pumps")
    public Result<List<Equipment>> getPumps() {
        return Result.ok(nutrientService.getPumps());
    }

    @ApiOperation("手动控制泵")
    @PutMapping("/pump/{alias}")
    public Result<String> controlPump(@PathVariable String alias, @RequestBody Map<String, Integer> body) {
        return Result.ok(nutrientService.controlPump(alias, body.get("status")));
    }

    @ApiOperation("切换模式")
    @PostMapping("/mode")
    public Result<Void> switchMode(@RequestBody Map<String, String> body) {
        nutrientService.switchMode(body.get("mode"));
        return Result.ok();
    }

    @ApiOperation("获取当前模式")
    @GetMapping("/mode")
    public Result<String> getMode() {
        return Result.ok(nutrientService.getMode());
    }

    @ApiOperation("保存灌溉计划")
    @PostMapping("/schedule")
    public Result<Void> saveSchedule(@RequestBody IrrigationSchedule schedule) {
        nutrientService.saveSchedule(schedule);
        return Result.ok();
    }

    @ApiOperation("获取灌溉计划列表")
    @GetMapping("/schedules")
    public Result<List<IrrigationSchedule>> getSchedules() {
        return Result.ok(nutrientService.getSchedules());
    }

    @ApiOperation("获取灌溉记录")
    @GetMapping("/records")
    public Result<List<IrrigationRecord>> getRecords() {
        return Result.ok(nutrientService.getRecords());
    }

    @ApiOperation("获取灌溉统计")
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        return Result.ok(nutrientService.getStats());
    }
}
