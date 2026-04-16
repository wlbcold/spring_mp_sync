package com.example.ghddapi.config;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration("gaohuSwaggerConfig")
public class SwaggerConfig {
    @Bean
    public GroupedOpenApi xtApi() {
        String[] paths = {"/**"};
        return GroupedOpenApi.builder()
                .group("高湖调度服务")
                .packagesToScan("com.example.ghddapi.controller")
                .pathsToMatch(paths).build();
    }
}
