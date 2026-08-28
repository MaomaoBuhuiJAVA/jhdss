package com.jhds.controller;

import com.jhds.common.Result;
import com.jhds.entity.*;
import com.jhds.service.PlantArchiveImageStorageService;
import com.jhds.service.PlantArchiveService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Api(tags = "植株历年档案模块")
@RestController
@RequestMapping("/api/plant-archive")
public class PlantArchiveController {

    private static final Logger log = LoggerFactory.getLogger(PlantArchiveController.class);

    @Autowired
    private PlantArchiveService plantArchiveService;
    @Autowired
    private PlantArchiveImageStorageService plantArchiveImageStorageService;

    /* ============ 植株档案 ============ */

    @ApiOperation("上传植株历年档案图片")
    @PostMapping(value = {"/uploads/image", "/uploads/main-photo"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<String> uploadArchiveImage(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        try {
            String fileName = plantArchiveImageStorageService.store(file);
            return Result.ok(request.getContextPath() + "/archive-uploads/" + fileName);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (IOException e) {
            log.error("Failed to store plant archive image", e);
            return Result.error("图片上传失败，请稍后重试");
        }
    }

    @ApiOperation("档案列表（含覆盖年份、最新评级）")
    @GetMapping("/plants")
    public Result<List<Map<String, Object>>> listPlants(@RequestParam(required = false) String keyword) {
        return Result.ok(plantArchiveService.listPlants(keyword));
    }

    @ApiOperation("档案详情")
    @GetMapping("/plants/{id}")
    public Result<PlantInfo> getPlant(@PathVariable Long id) {
        PlantInfo plant = plantArchiveService.getPlant(id);
        return plant == null ? Result.error("档案不存在") : Result.ok(plant);
    }

    @ApiOperation("新增植株档案（自动创建当年年度档案）")
    @PostMapping("/plants")
    public Result<String> createPlant(@RequestBody PlantInfo plant) {
        plantArchiveService.createPlant(plant);
        return Result.ok("新增成功");
    }

    @ApiOperation("修改基础档案")
    @PutMapping("/plants/{id}")
    public Result<String> updatePlant(@PathVariable Long id, @RequestBody PlantInfo plant) {
        plant.setId(id);
        plantArchiveService.updatePlant(plant);
        return Result.ok("保存成功");
    }

    @ApiOperation("删除植株档案（级联删除全部年度数据）")
    @DeleteMapping("/plants/{id}")
    public Result<String> deletePlant(@PathVariable Long id) {
        plantArchiveService.deletePlant(id);
        return Result.ok("删除成功");
    }

    /* ============ 年度档案 ============ */

    @ApiOperation("已建档年份列表")
    @GetMapping("/plants/{id}/years")
    public Result<List<Integer>> listYears(@PathVariable Long id) {
        return Result.ok(plantArchiveService.listYears(id));
    }

    @ApiOperation("新建年度档案（默认当年）")
    @PostMapping("/plants/{id}/years")
    public Result<String> createYear(@PathVariable Long id, @RequestParam(required = false) Integer year) {
        plantArchiveService.createYear(id, year);
        return Result.ok("年度档案已创建");
    }

    @ApiOperation("保存年终总结（带 id 修改，否则按 植株+年份 upsert）")
    @PostMapping("/year-record")
    public Result<String> saveYearRecord(@RequestBody PlantYearRecord record) {
        plantArchiveService.saveYearRecord(record);
        return Result.ok("保存成功");
    }

    @ApiOperation("删除某年度档案（含该年全部明细）")
    @DeleteMapping("/plants/{id}/years/{year}")
    public Result<String> deleteYear(@PathVariable Long id, @PathVariable Integer year) {
        plantArchiveService.deleteYear(id, year);
        return Result.ok("删除成功");
    }

    @ApiOperation("年度档案聚合：一次返回六大部分")
    @GetMapping("/plants/{id}/years/{year}")
    public Result<Map<String, Object>> getYearArchive(@PathVariable Long id, @PathVariable Integer year) {
        return Result.ok(plantArchiveService.getYearArchive(id, year));
    }

    /* ============ 物候记录 ============ */

    @ApiOperation("物候列表")
    @GetMapping("/phenology")
    public Result<List<PlantPhenology>> listPhenology(
            @RequestParam(required = false) Long plantId, @RequestParam(required = false) Integer year) {
        return Result.ok(plantArchiveService.listPhenology(plantId, year));
    }

    @ApiOperation("新增/修改物候记录（带 id 为修改）")
    @PostMapping("/phenology")
    public Result<String> savePhenology(@RequestBody PlantPhenology e) {
        plantArchiveService.savePhenology(e);
        return Result.ok("保存成功");
    }

    @ApiOperation("删除物候记录")
    @DeleteMapping("/phenology")
    public Result<String> deletePhenology(@RequestParam Long id) {
        plantArchiveService.deletePhenology(id);
        return Result.ok("删除成功");
    }

    /* ============ 栽培管理 ============ */

    @ApiOperation("栽培记录列表（按月）")
    @GetMapping("/cultivation")
    public Result<List<PlantCultivation>> listCultivation(
            @RequestParam(required = false) Long plantId, @RequestParam(required = false) Integer year) {
        return Result.ok(plantArchiveService.listCultivation(plantId, year));
    }

    @ApiOperation("新增/修改栽培记录（带 id 为修改；同月可保存多条）")
    @PostMapping("/cultivation")
    public Result<String> saveCultivation(@RequestBody PlantCultivation e) {
        plantArchiveService.saveCultivation(e);
        return Result.ok("保存成功");
    }

    @ApiOperation("删除栽培记录")
    @DeleteMapping("/cultivation")
    public Result<String> deleteCultivation(@RequestParam Long id) {
        plantArchiveService.deleteCultivation(id);
        return Result.ok("删除成功");
    }

    /* ============ 病虫害与逆境 ============ */

    @ApiOperation("病虫害逆境列表")
    @GetMapping("/pest")
    public Result<List<PlantPestDisease>> listPest(
            @RequestParam(required = false) Long plantId, @RequestParam(required = false) Integer year) {
        return Result.ok(plantArchiveService.listPest(plantId, year));
    }

    @ApiOperation("新增/修改病虫害记录")
    @PostMapping("/pest")
    public Result<String> savePest(@RequestBody PlantPestDisease e) {
        plantArchiveService.savePest(e);
        return Result.ok("保存成功");
    }

    @ApiOperation("删除病虫害记录")
    @DeleteMapping("/pest")
    public Result<String> deletePest(@RequestParam Long id) {
        plantArchiveService.deletePest(id);
        return Result.ok("删除成功");
    }

    /* ============ 生长观测 ============ */

    @ApiOperation("生长观测列表")
    @GetMapping("/growth")
    public Result<List<PlantGrowthRecord>> listGrowth(
            @RequestParam(required = false) Long plantId, @RequestParam(required = false) Integer year) {
        return Result.ok(plantArchiveService.listGrowth(plantId, year));
    }

    @ApiOperation("新增/修改生长观测记录")
    @PostMapping("/growth")
    public Result<String> saveGrowth(@RequestBody PlantGrowthRecord e) {
        plantArchiveService.saveGrowth(e);
        return Result.ok("保存成功");
    }

    @ApiOperation("删除生长观测记录")
    @DeleteMapping("/growth")
    public Result<String> deleteGrowth(@RequestParam Long id) {
        plantArchiveService.deleteGrowth(id);
        return Result.ok("删除成功");
    }

    /* ============ 统计 ============ */

    @ApiOperation("模块统计")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.ok(plantArchiveService.stats());
    }
}
