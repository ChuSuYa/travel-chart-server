package com.travelchart.userservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private static final String AVATAR_UPLOAD_DIR = "src/main/resources/static/avatars";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String absolutePath = new File(AVATAR_UPLOAD_DIR).getAbsolutePath();
        registry.addResourceHandler("/avatars/**")
                .addResourceLocations("file:" + absolutePath + "/");
    }
}
