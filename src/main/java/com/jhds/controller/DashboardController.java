package com.jhds.controller;

import com.jhds.common.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Api(tags = "数据大屏")
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @ApiOperation("获取大屏概览数据")
    @GetMapping("/overview")
    public Result<Map<String, Object>> getOverview() {
        Map<String, Object> overview = new HashMap<>();
        Map<String, Object> greenhouse = new HashMap<>();
        greenhouse.put("name", "种植架1");
        greenhouse.put("type", "玻璃体棚");
        greenhouse.put("crop", "樱桃");
        greenhouse.put("area", "1000 m²");
        greenhouse.put("plantCount", 1200);
        overview.put("greenhouse", greenhouse);
        return Result.ok(overview);
    }
}
