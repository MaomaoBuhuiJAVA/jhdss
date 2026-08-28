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
        registry.addResourceHandler("/images/**")
                .addResourceLocations("classpath:/static/images/", "classpath:/image/temp/");
    }

    private String directoryLocation(String path) {
        Path directory = Paths.get(path).toAbsolutePath().normalize();
        String location = directory.toUri().toString();
        return location.endsWith("/") ? location : location + "/";
    }
}
