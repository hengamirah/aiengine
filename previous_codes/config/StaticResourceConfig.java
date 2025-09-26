package com.example.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class StaticResourceConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        log.info("🔧 Configuring static resource handlers...");

        addHandler(registry, "/static/**", "file:./static/");
       
        addHandler(registry, "/crops/**", "file:./static/crops/");
        addHandler(registry, "/detections/**", "file:./static/detections/");
        addHandler(registry, "/uploads/**", "file:./static/uploads/");
       
        

      //  log.info("✅ Static resource handlers configured.");
    }

    private void addHandler(ResourceHandlerRegistry registry, String path, String location) {
        registry.addResourceHandler(path).addResourceLocations(location);
  //      log.debug("   → {} → {}", path, location);
    }
}
