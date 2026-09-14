package com.jhds.service;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PatrolReportServiceTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void createsReadableWordReportWithResultDetails() throws Exception {
        PatrolReportService service = new PatrolReportService();
        ReflectionTestUtils.setField(service, "resultPath", temporaryFolder.getRoot().getAbsolutePath());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("category", "害虫");
        result.put("name", "桃红颈天牛");
        result.put("count", 2);
        result.put("plant", "第 5 株盆栽");
        result.put("location", "第2条扫描线 · 中点");
        result.put("confidence", 0.923);
        result.put("hits", 3);
        result.put("frames", 3);
        result.put("advice", "安排人工捕捉并复检");
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("planName", "标准巡检");
        status.put("totalRows", 5);
        status.put("captureCount", 45);
        status.put("analysisResults", Arrays.asList(result));

        byte[] report = service.createReport(status);

        assertTrue(report.length > 1000);
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(report))) {
            assertEquals("AI轨道巡检结果报告", document.getParagraphs().get(0).getText());
            assertTrue(document.getTables().get(1).getText().contains("桃红颈天牛"));
            assertTrue(document.getTables().get(1).getText().contains("第 5 株盆栽"));
        }
    }
}
