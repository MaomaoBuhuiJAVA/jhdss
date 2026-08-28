package com.jhds.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Keyword matching backed by the editable ai_knowledge_entry table. */
@Service
public class AiKnowledgeService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public String findAnswer(String query) {
        if (query == null || query.trim().isEmpty()) {
            return null;
        }
        String normalized = normalize(query);
        List<Map<String, Object>> entries = jdbcTemplate.queryForList(
                "SELECT keywords, answer FROM ai_knowledge_entry WHERE enabled = 1 ORDER BY sort_order, id");
        for (Map<String, Object> entry : entries) {
            String keywords = String.valueOf(entry.get("keywords"));
            String[] values = keywords.split("[,\\n|]");
            for (String keyword : values) {
                if (!keyword.trim().isEmpty() && normalized.contains(normalize(keyword))) {
                    Object answer = entry.get("answer");
                    return answer == null ? null : String.valueOf(answer);
                }
            }
        }
        return null;
    }

    public List<Map<String, Object>> listEntries() {
        return jdbcTemplate.queryForList(
                "SELECT id, keywords, answer, enabled, sort_order, updated_at "
                        + "FROM ai_knowledge_entry ORDER BY sort_order, id");
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}
