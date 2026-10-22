package com.argus.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI description for the ARGUS gateway (JWT bearer auth). */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI argusOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ARGUS API")
                        .description("Autonomous Risk Guard for Untrusted Systems - real-time AI agent security gateway")
                        .version("1.0.0"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste the token from POST /api/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
