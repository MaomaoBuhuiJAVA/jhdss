package com.jhds.controller;

import com.jhds.common.Result;
import com.jhds.service.DashboardService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Api(tags = "数据大屏")
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    @ApiOperation("获取大屏概览数据")
    @GetMapping("/overview")
    public Result<Map<String, Object>> getOverview() {
        return Result.ok(dashboardService.getOverview());
    }
}
