package com.jhds.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/** Reads the video learning catalogue from MySQL. */
@Service
public class AiLearnVideoService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${ai.learn.photo-path:./photo}")
    private String photoPath;

    public List<Map<String, Object>> analyze(String videoName) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT v.video_name, v.folder_key, v.group_title, c.image_url, c.card_title, c.description, c.sort_order "
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
            item.put("image", resolveImageUrl(row));
            item.put("title", row.get("card_title"));
            item.put("desc", row.get("description"));
            items.add(item);
        }
        return new ArrayList<>(groups.values());
    }

    /**
     * Prefer the operator-provided photo directory, but keep cloned deployments usable
     * when that optional, gitignored directory has not been copied yet.
     */
    private String resolveImageUrl(Map<String, Object> row) {
        String imageUrl = stringValue(row.get("image_url"));
        String folderKey = stringValue(row.get("folder_key"));
        if (imageUrl == null || folderKey == null) {
            return fallbackImage(folderKey, row.get("sort_order"));
        }
        if (imageUrl.startsWith("/images/") || imageUrl.startsWith("http://")
                || imageUrl.startsWith("https://") || imageUrl.startsWith("data:")) {
            return imageUrl;
        }

        int slash = imageUrl.lastIndexOf('/');
        String fileName = slash >= 0 ? imageUrl.substring(slash + 1) : imageUrl;
        try {
            fileName = URLDecoder.decode(fileName, StandardCharsets.UTF_8.name());
            Path file = Paths.get(photoPath).toAbsolutePath().normalize()
                    .resolve(folderKey).resolve(fileName).normalize();
            Path folder = Paths.get(photoPath).toAbsolutePath().normalize()
                    .resolve(folderKey).normalize();
            if (file.startsWith(folder) && Files.isRegularFile(file) && Files.size(file) > 0) {
                return imageUrl;
            }
        } catch (IOException | RuntimeException e) {
            // Fall through to the tracked fallback image.
        }
        return fallbackImage(folderKey, row.get("sort_order"));
    }

    private String fallbackImage(String folderKey, Object sortOrder) {
        int order = 1;
        if (sortOrder instanceof Number) {
            order = Math.max(1, ((Number) sortOrder).intValue());
        } else {
            try {
                order = Math.max(1, Integer.parseInt(String.valueOf(sortOrder)));
            } catch (Exception ignored) {
            }
        }
        return "/images/demo/" + (folderKey == null ? "1" : folderKey) + "-" + order + ".png";
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
