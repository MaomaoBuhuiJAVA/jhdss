package com.jhds.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jhds.entity.InsectRecord;
import com.jhds.mapper.InsectRecordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class InsectService {

    @Autowired
    private InsectRecordMapper insectRecordMapper;
    @Autowired
    private InsectApiService insectApiService;

    public List<InsectRecord> getRecords(String date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        if (date == null) date = sdf.format(new Date());
        java.sql.Date sqlDate = java.sql.Date.valueOf(date);
        return insectRecordMapper.selectList(
                new LambdaQueryWrapper<InsectRecord>()
                        .eq(InsectRecord::getRecordDate, sqlDate));
    }

    public Integer getTodayTotal(String date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        if (date == null) date = sdf.format(new Date());
        Integer total = insectRecordMapper.selectTotalCountByDate(date);
        return total != null ? total : 0;
    }

    public List<Map<String, Object>> getTypeStats(String date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        if (date == null) date = sdf.format(new Date());
        return insectRecordMapper.selectStatsByDate(date);
    }

    public List<Map<String, Object>> getApiDevices() {
        List<Map<String, Object>> result = new ArrayList<>();
        JSONObject resp = insectApiService.getDeviceList(null);
        if (resp == null || resp.getIntValue("status") != 1) return result;
        JSONArray arr = resp.getJSONArray("data");
        if (arr == null) return result;
        for (int i = 0; i < arr.size(); i++) {
            JSONObject dev = arr.getJSONObject(i);
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("did", dev.getString("did"));
            map.put("name", dev.getString("name"));
            map.put("type", dev.getString("type"));
            result.add(map);
        }
        return result;
    }

    public Map<String, Object> sendControl(String did, String cmd, String groupname, String opname) {
        JSONObject resp = insectApiService.sendControl(did, cmd, groupname, opname);
        Map<String, Object> map = new LinkedHashMap<>();
        if (resp != null) {
            map.put("status", resp.getIntValue("status"));
            map.put("msg", resp.getString("msg"));
        } else {
            map.put("status", 0);
            map.put("msg", "控制命令发送失败");
        }
        return map;
    }

    public void syncLatestPhotos() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        JSONObject resp = insectApiService.getDeviceList(null);
        if (resp == null || resp.getIntValue("status") != 1) return;
        JSONArray devices = resp.getJSONArray("data");
        if (devices == null) return;

        for (int i = 0; i < devices.size(); i++) {
            String did = devices.getJSONObject(i).getString("did");
            JSONObject picResp = insectApiService.getLatestPhotos(did);
            if (picResp == null || picResp.getIntValue("status") != 1) continue;
            JSONArray pics = picResp.getJSONArray("data");
            if (pics == null) continue;
            for (int j = 0; j < pics.size(); j++) {
                JSONObject pic = pics.getJSONObject(j);
                String thumb = pic.getString("thumb");
                if (thumb == null) continue;

                long exists = insectRecordMapper.selectCount(
                        new LambdaQueryWrapper<InsectRecord>()
                                .eq(InsectRecord::getThumbUrl, thumb));
                if (exists > 0) continue;

                String datetime = pic.getString("datetime");
                Date recordTime = null;
                try {
                    recordTime = sdf.parse(datetime);
                } catch (Exception e) {
                    recordTime = new Date();
                }

                String dateStr = new SimpleDateFormat("yyyy-MM-dd").format(recordTime);
                java.sql.Date recordDate = java.sql.Date.valueOf(dateStr);

                String aiResultStr = pic.getString("ai_result");
                saveAiResults(did, thumb, aiResultStr, recordDate, recordTime);
            }
        }
    }

    private void saveAiResults(String did, String thumb, String aiResultStr,
                                java.sql.Date recordDate, Date recordTime) {
        if (aiResultStr == null || aiResultStr.isEmpty()) {
            InsectRecord record = new InsectRecord();
            record.setDeviceId(did);
            record.setThumbUrl(thumb);
            record.setSpecies("未知");
            record.setCount(0);
            record.setRecordDate(recordDate);
            record.setRecordTime(recordTime);
            insectRecordMapper.insert(record);
            return;
        }
        try {
            JSONArray results = JSON.parseArray(aiResultStr);
            if (results == null || results.isEmpty()) return;
            for (int k = 0; k < results.size(); k++) {
                JSONObject item = results.getJSONObject(k);
                InsectRecord record = new InsectRecord();
                record.setDeviceId(did);
                record.setThumbUrl(thumb);
                record.setSpecies(item.getString("name"));
                record.setCount(item.getIntValue("num"));
                record.setRecordDate(recordDate);
                record.setRecordTime(recordTime);
                insectRecordMapper.insert(record);
            }
        } catch (Exception e) {
            InsectRecord record = new InsectRecord();
            record.setDeviceId(did);
            record.setThumbUrl(thumb);
            record.setSpecies("未知");
            record.setCount(0);
            record.setRecordDate(recordDate);
            record.setRecordTime(recordTime);
            insectRecordMapper.insert(record);
        }
    }

    public List<Map<String, Object>> getPhotoHistory(String did, String startTime,
                                                      String endTime, Integer page, Integer num) {
        List<Map<String, Object>> result = new ArrayList<>();
        JSONObject resp = insectApiService.getPhotoHistory(did, startTime, endTime, page, num);
        if (resp == null || resp.getIntValue("status") != 1) return result;
        JSONArray data = resp.getJSONArray("data");
        Integer totalCount = resp.getInteger("count");
        if (data == null) return result;
        for (int i = 0; i < data.size(); i++) {
            JSONObject item = data.getJSONObject(i);
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", item.getInteger("id"));
            map.put("did", item.getString("did"));
            map.put("datetime", item.getString("datetime"));
            map.put("thumb", item.getString("thumb"));
            map.put("sourceThumb", item.getString("source_thumb"));
            map.put("aiResult", item.getString("ai_result"));
            map.put("aiEngine", item.getString("ai_engine"));
            map.put("aiStatus", item.getInteger("ai_status"));
            result.add(map);
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("total", totalCount != null ? totalCount : result.size());
        meta.put("page", page != null ? page : 1);
        meta.put("num", num != null ? num : result.size());
        result.add(0, meta);
        return result;
    }

    public Map<String, Object> getDeviceControlParams(String did) {
        JSONObject resp = insectApiService.getControlParams(did);
        Map<String, Object> map = new LinkedHashMap<>();
        if (resp != null) {
            map.put("status", resp.getIntValue("status"));
            map.put("msg", resp.getString("msg"));
            map.put("data", resp.get("data"));
        } else {
            map.put("status", 0);
            map.put("msg", "获取控制参数失败");
        }
        return map;
    }

    public Map<String, Object> getDeviceControlStatus(String did) {
        JSONObject resp = insectApiService.getControlStatus(did);
        Map<String, Object> map = new LinkedHashMap<>();
        if (resp != null) {
            map.put("status", resp.getIntValue("status"));
            map.put("msg", resp.getString("msg"));
            map.put("data", resp.get("data"));
        } else {
            map.put("status", 0);
            map.put("msg", "获取控制状态失败");
        }
        return map;
    }

    public Map<String, Object> getDeviceRealtimeData(String did) {
        JSONObject resp = insectApiService.getRealtimeData(did);
        Map<String, Object> map = new LinkedHashMap<>();
        if (resp != null) {
            map.put("status", resp.getIntValue("status"));
            map.put("msg", resp.getString("msg"));
            map.put("data", resp.get("data"));
        } else {
            map.put("status", 0);
            map.put("msg", "获取实时数据失败");
        }
        return map;
    }

    public Map<String, Object> getDataHistory(String did, String startTime, String endTime) {
        JSONObject resp = insectApiService.getDataHistory(did, startTime, endTime);
        Map<String, Object> map = new LinkedHashMap<>();
        if (resp != null) {
            map.put("status", resp.getIntValue("status"));
            map.put("msg", resp.getString("msg"));
            map.put("data", resp.get("data"));
        } else {
            map.put("status", 0);
            map.put("msg", "获取数据历史失败");
        }
        return map;
    }

    public List<Map<String, Object>> getLatestApiPhotos() {
        List<Map<String, Object>> result = new ArrayList<>();
        JSONObject resp = insectApiService.getDeviceList(null);
        if (resp == null || resp.getIntValue("status") != 1) return result;
        JSONArray devices = resp.getJSONArray("data");
        if (devices == null) return result;

        for (int i = 0; i < devices.size(); i++) {
            String did = devices.getJSONObject(i).getString("did");
            JSONObject picResp = insectApiService.getLatestPhotos(did);
            if (picResp == null || picResp.getIntValue("status") != 1) continue;
            JSONArray pics = picResp.getJSONArray("data");
            if (pics == null) continue;
            for (int j = 0; j < pics.size(); j++) {
                JSONObject pic = pics.getJSONObject(j);
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", pic.getInteger("id"));
                map.put("did", pic.getString("did"));
                map.put("datetime", pic.getString("datetime"));
                map.put("thumb", pic.getString("thumb"));
                map.put("originalImage", pic.getString("original_image"));
                map.put("aiEngine", pic.getString("ai_engine"));
                map.put("aiStatus", pic.getInteger("ai_status"));
                result.add(map);
            }
        }
        return result;
    }
}
