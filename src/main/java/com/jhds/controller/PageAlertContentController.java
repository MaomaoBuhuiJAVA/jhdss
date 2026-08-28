package com.jhds.controller;

import com.jhds.common.Result;
import com.jhds.entity.PageAlertContent;
import com.jhds.service.PageAlertContentService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Api(tags = "页面告警内容")
@RestController
@RequestMapping("/api/page-alerts")
public class PageAlertContentController {

    @Autowired
    private PageAlertContentService pageAlertContentService;

    @ApiOperation("按告警键获取页面告警标题、说明和图片")
    @GetMapping("/{alertKey}")
    public Result<PageAlertContent> getByKey(@PathVariable String alertKey) {
        PageAlertContent content = pageAlertContentService.getByKey(alertKey);
        if (content == null) {
            return Result.error(404, "页面告警内容不存在");
        }
        return Result.ok(content);
    }

    @ApiOperation("获取已启用的页面告警内容")
    @GetMapping
    public Result<List<PageAlertContent>> listEnabled() {
        return Result.ok(pageAlertContentService.listEnabled());
    }
}
