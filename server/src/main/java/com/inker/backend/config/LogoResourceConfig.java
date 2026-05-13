package com.inker.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class LogoResourceConfig implements WebMvcConfigurer {

    private final String storagePath;

    public LogoResourceConfig(@Value("${inker.logo.storage-path:data/logos}") String storagePath) {
        this.storagePath = storagePath;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(storagePath).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/api/v1/logos/**")
                .addResourceLocations(location);
    }
}
