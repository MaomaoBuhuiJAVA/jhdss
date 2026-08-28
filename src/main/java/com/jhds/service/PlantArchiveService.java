package com.jhds.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jhds.entity.PlantCultivation;
import com.jhds.entity.PlantGrowthRecord;
import com.jhds.entity.PlantInfo;
import com.jhds.entity.PlantPestDisease;
import com.jhds.entity.PlantPhenology;
import com.jhds.entity.PlantYearRecord;
import com.jhds.mapper.PlantCultivationMapper;
import com.jhds.mapper.PlantGrowthRecordMapper;
import com.jhds.mapper.PlantInfoMapper;
import com.jhds.mapper.PlantPestDiseaseMapper;
import com.jhds.mapper.PlantPhenologyMapper;
import com.jhds.mapper.PlantYearRecordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Application service for the plant archive module. */
@Service
public class PlantArchiveService {
    @Autowired private PlantInfoMapper plantInfoMapper;
    @Autowired private PlantYearRecordMapper plantYearRecordMapper;
    @Autowired private PlantPhenologyMapper plantPhenologyMapper;
    @Autowired private PlantCultivationMapper plantCultivationMapper;
    @Autowired private PlantPestDiseaseMapper plantPestDiseaseMapper;
    @Autowired private PlantGrowthRecordMapper plantGrowthRecordMapper;

    public List<Map<String, Object>> listPlants(String keyword) {
        LambdaQueryWrapper<PlantInfo> query = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            String value = keyword.trim();
            query.and(q -> q.like(PlantInfo::getPlantName, value)
                    .or().like(PlantInfo::getVariety, value)
                    .or().like(PlantInfo::getScientificName, value));
        }
        query.orderByAsc(PlantInfo::getId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (PlantInfo plant : plantInfoMapper.selectList(query)) {
            List<Integer> years = listYears(plant.getId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("plant", plant);
            item.put("years", years);
            item.put("grade", latestGrade(plant.getId(), years));
            result.add(item);
        }
        return result;
    }

    private String latestGrade(Long plantId, List<Integer> years) {
        if (years.isEmpty()) return null;
        PlantYearRecord record = plantYearRecordMapper.selectOne(new LambdaQueryWrapper<PlantYearRecord>()
                .eq(PlantYearRecord::getPlantId, plantId)
                .eq(PlantYearRecord::getYear, years.get(0)));
        return record == null ? null : record.getGrowthGrade();
    }

    public PlantInfo getPlant(Long id) {
        return plantInfoMapper.selectById(id);
    }

    @Transactional
    public void createPlant(PlantInfo plant) {
        if (plant.getPlantDate() == null) plant.setPlantDate(new Date());
        plantInfoMapper.insert(plant);
        createYear(plant.getId(), Calendar.getInstance().get(Calendar.YEAR));
    }

    public void updatePlant(PlantInfo plant) {
        plantInfoMapper.updateById(plant);
    }

    @Transactional
    public void deletePlant(Long id) {
        plantYearRecordMapper.delete(new LambdaQueryWrapper<PlantYearRecord>().eq(PlantYearRecord::getPlantId, id));
        plantPhenologyMapper.delete(new LambdaQueryWrapper<PlantPhenology>().eq(PlantPhenology::getPlantId, id));
        plantCultivationMapper.delete(new LambdaQueryWrapper<PlantCultivation>().eq(PlantCultivation::getPlantId, id));
        plantPestDiseaseMapper.delete(new LambdaQueryWrapper<PlantPestDisease>().eq(PlantPestDisease::getPlantId, id));
        plantGrowthRecordMapper.delete(new LambdaQueryWrapper<PlantGrowthRecord>().eq(PlantGrowthRecord::getPlantId, id));
        plantInfoMapper.deleteById(id);
    }

    public List<Integer> listYears(Long plantId) {
        List<Integer> years = new ArrayList<>();
        for (PlantYearRecord record : plantYearRecordMapper.selectList(new LambdaQueryWrapper<PlantYearRecord>()
                .eq(PlantYearRecord::getPlantId, plantId)
                .orderByDesc(PlantYearRecord::getYear))) {
            years.add(record.getYear());
        }
        return years;
    }

    public void createYear(Long plantId, Integer year) {
        if (year == null) year = Calendar.getInstance().get(Calendar.YEAR);
        Integer count = plantYearRecordMapper.selectCount(new LambdaQueryWrapper<PlantYearRecord>()
                .eq(PlantYearRecord::getPlantId, plantId).eq(PlantYearRecord::getYear, year));
        if (count != null && count > 0) return;
        PlantYearRecord record = new PlantYearRecord();
        record.setPlantId(plantId);
        record.setYear(year);
        plantYearRecordMapper.insert(record);
    }

    public void saveYearRecord(PlantYearRecord record) {
        if (record.getId() != null) {
            plantYearRecordMapper.updateById(record);
            return;
        }
        PlantYearRecord old = plantYearRecordMapper.selectOne(new LambdaQueryWrapper<PlantYearRecord>()
                .eq(PlantYearRecord::getPlantId, record.getPlantId())
                .eq(PlantYearRecord::getYear, record.getYear()));
        if (old == null) plantYearRecordMapper.insert(record);
        else {
            record.setId(old.getId());
            plantYearRecordMapper.updateById(record);
        }
    }

    @Transactional
    public void deleteYear(Long plantId, Integer year) {
        plantYearRecordMapper.delete(new LambdaQueryWrapper<PlantYearRecord>()
                .eq(PlantYearRecord::getPlantId, plantId).eq(PlantYearRecord::getYear, year));
        plantPhenologyMapper.delete(new LambdaQueryWrapper<PlantPhenology>().eq(PlantPhenology::getPlantId, plantId)
                .eq(PlantPhenology::getYear, year));
        plantCultivationMapper.delete(new LambdaQueryWrapper<PlantCultivation>().eq(PlantCultivation::getPlantId, plantId)
                .eq(PlantCultivation::getYear, year));
        plantPestDiseaseMapper.delete(new LambdaQueryWrapper<PlantPestDisease>().eq(PlantPestDisease::getPlantId, plantId)
                .eq(PlantPestDisease::getYear, year));
        plantGrowthRecordMapper.delete(new LambdaQueryWrapper<PlantGrowthRecord>().eq(PlantGrowthRecord::getPlantId, plantId)
                .eq(PlantGrowthRecord::getYear, year));
    }

    public Map<String, Object> getYearArchive(Long plantId, Integer year) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("plant", plantInfoMapper.selectById(plantId));
        data.put("yearRecord", plantYearRecordMapper.selectOne(new LambdaQueryWrapper<PlantYearRecord>()
                .eq(PlantYearRecord::getPlantId, plantId).eq(PlantYearRecord::getYear, year)));
        data.put("phenology", listPhenology(plantId, year));
        data.put("cultivation", listCultivation(plantId, year));
        data.put("pestDisease", listPest(plantId, year));
        data.put("growthRecords", listGrowth(plantId, year));
        return data;
    }

    public List<PlantPhenology> listPhenology(Long plantId, Integer year) {
        return plantPhenologyMapper.selectList(new LambdaQueryWrapper<PlantPhenology>()
                .eq(plantId != null, PlantPhenology::getPlantId, plantId)
                .eq(year != null, PlantPhenology::getYear, year)
                .orderByAsc(PlantPhenology::getEventDate));
    }
    public void savePhenology(PlantPhenology record) {
        if (record.getId() == null) plantPhenologyMapper.insert(record);
        else plantPhenologyMapper.updateById(record);
    }
    public void deletePhenology(Long id) { plantPhenologyMapper.deleteById(id); }

    public List<PlantCultivation> listCultivation(Long plantId, Integer year) {
        return plantCultivationMapper.selectList(new LambdaQueryWrapper<PlantCultivation>()
                .eq(plantId != null, PlantCultivation::getPlantId, plantId)
                .eq(year != null, PlantCultivation::getYear, year)
                .orderByAsc(PlantCultivation::getMonth));
    }
    /**
     * A month can contain multiple cultivation operations. New records must
     * always be inserted; edits are identified explicitly by their id.
     */
    public void saveCultivation(PlantCultivation record) {
        if (record.getId() == null) plantCultivationMapper.insert(record);
        else plantCultivationMapper.updateById(record);
    }
    public void deleteCultivation(Long id) { plantCultivationMapper.deleteById(id); }

    public List<PlantPestDisease> listPest(Long plantId, Integer year) {
        return plantPestDiseaseMapper.selectList(new LambdaQueryWrapper<PlantPestDisease>()
                .eq(plantId != null, PlantPestDisease::getPlantId, plantId)
                .eq(year != null, PlantPestDisease::getYear, year)
                .orderByAsc(PlantPestDisease::getOccurDate));
    }
    public void savePest(PlantPestDisease record) {
        if (record.getId() == null) plantPestDiseaseMapper.insert(record);
        else plantPestDiseaseMapper.updateById(record);
    }
    public void deletePest(Long id) { plantPestDiseaseMapper.deleteById(id); }

    public List<PlantGrowthRecord> listGrowth(Long plantId, Integer year) {
        return plantGrowthRecordMapper.selectList(new LambdaQueryWrapper<PlantGrowthRecord>()
                .eq(plantId != null, PlantGrowthRecord::getPlantId, plantId)
                .eq(year != null, PlantGrowthRecord::getYear, year)
                .orderByAsc(PlantGrowthRecord::getRecordDate));
    }
    public void saveGrowth(PlantGrowthRecord record) {
        if (record.getId() == null) plantGrowthRecordMapper.insert(record);
        else plantGrowthRecordMapper.updateById(record);
    }
    public void deleteGrowth(Long id) { plantGrowthRecordMapper.deleteById(id); }

    public Map<String, Object> stats() {
        int year = Calendar.getInstance().get(Calendar.YEAR);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("plantCount", plantInfoMapper.selectCount(null));
        result.put("yearCount", plantYearRecordMapper.selectCount(null));
        result.put("phenologyCount", plantPhenologyMapper.selectCount(new LambdaQueryWrapper<PlantPhenology>()
                .eq(PlantPhenology::getYear, year)));
        result.put("growthCount", plantGrowthRecordMapper.selectCount(new LambdaQueryWrapper<PlantGrowthRecord>()
                .eq(PlantGrowthRecord::getYear, year)));
        return result;
    }
}
