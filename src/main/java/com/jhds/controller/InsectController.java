package com.jhds.controller;

import com.jhds.common.Result;
import com.jhds.entity.InsectRecord;
import com.jhds.service.InsectService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Api(tags = "虫情灯模块")
@RestController
@RequestMapping("/api/insect")
public class InsectController {

    @Autowired
    private InsectService insectService;

    @ApiOperation("获取某日虫情记录")
    @GetMapping("/records")
    public Result<List<InsectRecord>> getRecords(@RequestParam(required = false) String date) {
        return Result.ok(insectService.getRecords(date));
    }

    @ApiOperation("获取今日虫情统计")
    @GetMapping("/stats/today")
    public Result<Map<String, Object>> getTodayStats() {
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("total", insectService.getTodayTotal(null));
        stats.put("types", insectService.getTypeStats(null));
        return Result.ok(stats);
    }

    @ApiOperation("获取虫体种类分布")
    @GetMapping("/stats/types")
    public Result<List<Map<String, Object>>> getTypeStats(@RequestParam(required = false) String date) {
        return Result.ok(insectService.getTypeStats(date));
    }

    @ApiOperation("从平台获取设备列表")
    @GetMapping("/api/devices")
    public Result<List<Map<String, Object>>> getApiDevices() {
        return Result.ok(insectService.getApiDevices());
    }

    @ApiOperation("从平台获取最新虫情照片")
    @GetMapping("/api/photos/latest")
    public Result<List<Map<String, Object>>> getLatestApiPhotos() {
        return Result.ok(insectService.getLatestApiPhotos());
    }

    @ApiOperation("从平台获取虫情照片历史记录")
    @GetMapping("/api/photos/history")
    public Result<List<Map<String, Object>>> getPhotoHistory(
            @RequestParam String did,
            @RequestParam String startTime,
            @RequestParam String endTime,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer num) {
        return Result.ok(insectService.getPhotoHistory(did, startTime, endTime, page, num));
    }

    @ApiOperation("发送控制命令")
    @PostMapping("/api/control")
    public Result<Map<String, Object>> sendControl(@RequestBody Map<String, String> params) {
        String did = params.get("did");
        String cmd = params.get("cmd");
        String groupname = params.get("groupname");
        String opname = params.get("opname");
        Map<String, Object> result = insectService.sendControl(did, cmd, groupname, opname);
        if (Integer.parseInt(result.get("status").toString()) == 1) {
            return Result.ok(result);
        }
        return Result.error(result.get("msg").toString());
    }

    @ApiOperation("手动同步数据")
    @PostMapping("/api/sync")
    public Result<String> syncData() {
        insectService.syncLatestPhotos();
        return Result.ok("同步完成");
    }

    @ApiOperation("获取设备控制参数")
    @GetMapping("/api/control-params")
    public Result<Map<String, Object>> getControlParams(@RequestParam String did) {
        return Result.ok(insectService.getDeviceControlParams(did));
    }

    @ApiOperation("获取设备控制状态")
    @GetMapping("/api/control-status")
    public Result<Map<String, Object>> getControlStatus(@RequestParam String did) {
        return Result.ok(insectService.getDeviceControlStatus(did));
    }

    @ApiOperation("获取设备实时数据")
    @GetMapping("/api/realtime")
    public Result<Map<String, Object>> getRealtimeData(@RequestParam String did) {
        return Result.ok(insectService.getDeviceRealtimeData(did));
    }

    @ApiOperation("获取设备数据历史记录")
    @GetMapping("/api/data-history")
    public Result<Map<String, Object>> getDataHistory(
            @RequestParam String did,
            @RequestParam String startTime,
            @RequestParam String endTime) {
        return Result.ok(insectService.getDataHistory(did, startTime, endTime));
    }
}
