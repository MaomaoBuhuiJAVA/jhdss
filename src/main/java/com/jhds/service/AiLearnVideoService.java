package com.jhds.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reads the video learning catalogue from MySQL. */
@Service
public class AiLearnVideoService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> analyze(String videoName) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT v.video_name, v.group_title, c.image_url, c.card_title, c.description "
                        + "FROM ai_learn_video v JOIN ai_learn_card c ON c.video_id = v.id "
                        + "WHERE v.enabled = 1 AND c.enabled = 1 ORDER BY v.sort_order, c.sort_order");

        String requested = normalizeFileName(videoName);
        Map<String, Map<String, Object>> groups = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String storedVideoName = normalizeFileName(stringValue(row.get("video_name")));
            if (requested != null && !requested.equals(storedVideoName)) {
                continue;
            }
            String groupTitle = stringValue(row.get("group_title"));
            if (groupTitle == null || groupTitle.trim().isEmpty()) {
                groupTitle = "AI学习资料";
            }
            Map<String, Object> group = groups.get(groupTitle);
            if (group == null) {
                group = new LinkedHashMap<>();
                group.put("group", groupTitle);
                group.put("items", new ArrayList<Map<String, Object>>());
                groups.put(groupTitle, group);
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) group.get("items");
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("image", row.get("image_url"));
            item.put("title", row.get("card_title"));
            item.put("desc", row.get("description"));
            items.add(item);
        }
        return new ArrayList<>(groups.values());
    }

    private String normalizeFileName(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String name = value.trim().replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return name.toLowerCase(Locale.ROOT);
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
