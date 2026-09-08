package com.jhds.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${archive.upload-path:./uploads/archive}")
    private String archiveUploadPath;

    @Value("${ai.learn.photo-path:./photo}")
    private String aiLearnPhotoPath;

    @Value("${camera.local.hls-path:./work/camera/hls}")
    private String localCameraHlsPath;

    @Value("${ai.yolo.upload-path:./uploads/ai-inference}")
    private String aiYoloUploadPath;

    @Value("${ai.yolo.evaluation-path:E:/LabelImg资料图片/yolo_runs/camera_test_eval}")
    private String aiYoloEvaluationPath;

    @Value("${ai.yolo.evaluation-source-path:E:/LabelImg资料图片/yolo_dataset/camera_test}")
    private String aiYoloEvaluationSourcePath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/META-INF/resources/", "classpath:/static/");
        registry.addResourceHandler("swagger-ui.html")
                .addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
        registry.addResourceHandler("/captures/**")
                .addResourceLocations("file:./captures/");
        registry.addResourceHandler("/archive-uploads/**")
                .addResourceLocations(directoryLocation(archiveUploadPath));
        registry.addResourceHandler("/ai-learn-media/**")
                .addResourceLocations(directoryLocation(aiLearnPhotoPath));
        registry.addResourceHandler("/ai-inference/**")
                .addResourceLocations(directoryLocation(aiYoloUploadPath));
        registry.addResourceHandler("/ai-eval/**")
                .addResourceLocations(directoryLocation(aiYoloEvaluationPath));
        registry.addResourceHandler("/ai-eval-source/**")
                .addResourceLocations(directoryLocation(aiYoloEvaluationSourcePath));
        registry.addResourceHandler("/local-camera/**")
                .addResourceLocations(directoryLocation(localCameraHlsPath))
                // Playlists and segments are live resources. A cached m3u8
                // can keep a browser on an old sequence after reconnecting.
                .setCachePeriod(0);
        registry.addResourceHandler("/images/**")
                .addResourceLocations("classpath:/static/images/", "classpath:/image/temp/");
    }

    private String directoryLocation(String path) {
        Path directory = Paths.get(path).toAbsolutePath().normalize();
        String location = directory.toUri().toString();
        return location.endsWith("/") ? location : location + "/";
    }
}
