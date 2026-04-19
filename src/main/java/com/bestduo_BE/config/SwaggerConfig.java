package com.bestduo_BE.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    private static final String ADMIN_API_KEY_SCHEME = "AdminApiKey";
    private static final String ADMIN_API_KEY_HEADER = "X-Admin-Key";

    @Bean
    public OpenAPI bestduoOpenApi() {
        return new OpenAPI()
                .servers(List.of(new Server().url("/")))  // 상대 경로로 same-origin 유지 (프록시/HTTPS 대응)
                .components(new Components()
                        .addSecuritySchemes(ADMIN_API_KEY_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name(ADMIN_API_KEY_HEADER)
                                        .description("Admin API 인증 헤더 (application.yml의 admin.api.key 값)")))
                .info(new Info()
                        .title("BestDuo API")
                        .description("BestDuo 서비스 OpenAPI 문서")
                        .version("v1"));
    }

    /**
     * 사용자/클라이언트용 API 그룹
     * 예: /bottom-duo/stats, /bottom-duo/detail
     */
    @Bean
    public GroupedOpenApi clientApi() {
        return GroupedOpenApi.builder()
                .group("client-api")
                .displayName("Client API")
                .pathsToMatch(
                        "/bottom-duo/**"
                )
                .build();
    }

    /**
     * 운영용 Admin API 그룹
     * 예: /admin/run/**, /admin/queue/**
     */
    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("admin-api")
                .displayName("Admin API")
                .pathsToMatch(
                        "/admin/**"
                )
                .addOpenApiCustomizer(openApi ->
                        openApi.addSecurityItem(
                                new SecurityRequirement().addList(ADMIN_API_KEY_SCHEME)))
                .build();
    }
}

