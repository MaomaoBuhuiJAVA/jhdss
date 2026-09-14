package com.jhds.service;

import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class PatrolReportService {

    @Value("${patrol.automatic.result-path:./uploads/patrol-results}")
    private String resultPath;

    public byte[] createReport(Map<String, Object> status) throws IOException {
        List<Map<String, Object>> results = results(status);
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            addTitle(document, "AI轨道巡检结果报告");
            addSummary(document, status, results);
            addResultTable(document, results);
            addResultDetails(document, results);
            document.write(output);
            return output.toByteArray();
        }
    }

    private void addTitle(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        XWPFRun run = paragraph.createRun();
        run.setBold(true);
        run.setFontFamily("Microsoft YaHei");
        run.setFontSize(20);
        run.setText(text);
    }

    private void addSummary(XWPFDocument document, Map<String, Object> status,
                            List<Map<String, Object>> results) {
        int pestCount = 0;
        int immatureCount = 0;
        for (Map<String, Object> result : results) {
            int count = integer(result.get("count"));
            if ("未成熟果实".equals(String.valueOf(result.get("category")))) immatureCount += count;
            else pestCount += count;
        }
        addHeading(document, "一、巡检概况");
        XWPFTable table = document.createTable(5, 2);
        setRow(table, 0, "巡检方案", text(status.get("planName"), "自动巡检"));
        setRow(table, 1, "执行时间", formatTime(status.get("startedAt")) + " 至 " + formatTime(status.get("endedAt")));
        setRow(table, 2, "扫描与抓拍", integer(status.get("totalRows")) + " 条扫描线，"
                + integer(status.get("captureCount")) + " 张有效照片");
        setRow(table, 3, "异常汇总", results.size() + " 处；害虫 " + pestCount + " 个，未成熟果实 " + immatureCount + " 个");
        setRow(table, 4, "分析方式", "设备停止后多帧复核，保留清晰标注图并合并同一停靠点结果");
    }

    private void addResultTable(XWPFDocument document, List<Map<String, Object>> results) {
        addHeading(document, "二、异常清单");
        if (results.isEmpty()) {
            paragraph(document, "本次巡检未发现明确的未成熟果实或害虫异常。");
            return;
        }
        XWPFTable table = document.createTable(results.size() + 1, 7);
        String[] headers = {"序号", "类别", "识别目标", "数量", "所在植株", "所在区域", "置信度"};
        for (int column = 0; column < headers.length; column++) setCell(table.getRow(0).getCell(column), headers[column], true);
        for (int row = 0; row < results.size(); row++) {
            Map<String, Object> result = results.get(row);
            String[] values = {String.valueOf(row + 1), text(result.get("category"), "害虫"),
                    text(result.get("name"), "未知目标"), String.valueOf(integer(result.get("count"))),
                    text(result.get("plant"), "未知植株"), text(result.get("location"), "未知区域"),
                    String.format("%.1f%%", number(result.get("confidence")) * 100.0)};
            for (int column = 0; column < values.length; column++) setCell(table.getRow(row + 1).getCell(column), values[column], false);
        }
    }

    private void addResultDetails(XWPFDocument document, List<Map<String, Object>> results) {
        if (results.isEmpty()) return;
        addHeading(document, "三、识别详情与处置建议");
        for (int index = 0; index < results.size(); index++) {
            Map<String, Object> result = results.get(index);
            XWPFRun label = document.createParagraph().createRun();
            label.setBold(true);
            label.setFontFamily("Microsoft YaHei");
            label.setText((index + 1) + ". " + text(result.get("name"), "异常") + "（"
                    + text(result.get("plant"), "未知植株") + "）");
            paragraph(document, "位置：" + text(result.get("location"), "未知") + "；多帧证据："
                    + integer(result.get("hits")) + "/" + integer(result.get("frames")) + " 帧；识别时间："
                    + text(result.get("capturedAt"), "未知"));
            paragraph(document, "建议：" + text(result.get("advice"), "安排人工复核并持续观察。"));
            addImage(document, result.get("image"), text(result.get("name"), "异常") + "标注图");
        }
    }

    private void addImage(XWPFDocument document, Object imageUrl, String description) {
        Path image = resolveImage(imageUrl);
        if (image == null) return;
        try (InputStream input = Files.newInputStream(image)) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.addPicture(input, XWPFDocument.PICTURE_TYPE_JPEG, image.getFileName().toString(),
                    Units.toEMU(520), Units.toEMU(292));
            XWPFRun caption = document.createParagraph().createRun();
            caption.setItalic(true);
            caption.setFontFamily("Microsoft YaHei");
            caption.setText(description);
        } catch (Exception ignored) {
            paragraph(document, "识别图片：" + image.getFileName());
        }
    }

    private Path resolveImage(Object imageUrl) {
        if (imageUrl == null) return null;
        String filename;
        try {
            filename = Paths.get(String.valueOf(imageUrl).replace('\\', '/')).getFileName().toString();
        } catch (RuntimeException e) {
            return null;
        }
        Path root = Paths.get(resultPath).toAbsolutePath().normalize();
        Path image = root.resolve(filename).normalize();
        return image.startsWith(root) && Files.isRegularFile(image) ? image : null;
    }

    private void addHeading(XWPFDocument document, String text) {
        XWPFRun run = document.createParagraph().createRun();
        run.setBold(true);
        run.setFontFamily("Microsoft YaHei");
        run.setFontSize(14);
        run.setText(text);
    }

    private void paragraph(XWPFDocument document, String text) {
        XWPFRun run = document.createParagraph().createRun();
        run.setFontFamily("Microsoft YaHei");
        run.setFontSize(10);
        run.setText(text);
    }

    private void setRow(XWPFTable table, int row, String label, String value) {
        setCell(table.getRow(row).getCell(0), label, true);
        setCell(table.getRow(row).getCell(1), value, false);
    }

    private void setCell(XWPFTableCell cell, String value, boolean bold) {
        cell.removeParagraph(0);
        XWPFRun run = cell.addParagraph().createRun();
        run.setBold(bold);
        run.setFontFamily("Microsoft YaHei");
        run.setFontSize(9);
        run.setText(value);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> results(Map<String, Object> status) {
        Object value = status.get("analysisResults");
        return value instanceof List ? (List<Map<String, Object>>) value : new ArrayList<Map<String, Object>>();
    }

    private String formatTime(Object value) {
        if (!(value instanceof Number) || ((Number) value).longValue() <= 0L) return "未知";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(((Number) value).longValue()));
    }

    private String text(Object value, String fallback) {
        return value == null || String.valueOf(value).trim().isEmpty() ? fallback : String.valueOf(value);
    }

    private int integer(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private double number(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
    }
}
