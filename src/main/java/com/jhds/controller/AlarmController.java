package com.jhds.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jhds.common.Result;
import com.jhds.entity.AlarmRecord;
import com.jhds.service.AlarmService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Api(tags = "报警中心模块")
@RestController
@RequestMapping("/api/alarm")
public class AlarmController {

    @Autowired
    private AlarmService alarmService;

    @ApiOperation("获取报警列表")
    @GetMapping("/list")
    public Result<IPage<AlarmRecord>> getList(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String sourceModule,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(alarmService.getList(level, sourceModule, page, size));
    }

    @ApiOperation("获取报警统计")
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        return Result.ok(alarmService.getStats());
    }

    @ApiOperation("处理报警")
    @PutMapping("/{id}/handle")
    public Result<Void> handle(@PathVariable Long id) {
        alarmService.handle(id);
        return Result.ok();
    }
}
