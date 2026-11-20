package com.looki.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI lookiOpenAPI() {
        final String securitySchemeName = "Bearer JWT";

        return new OpenAPI()
                .info(new Info()
                        .title("Looki API")
                        .description("Looki Salon Booking Platform — REST API")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Looki Team")
                                .email("support@looki.tn")))
                .addSecurityItem(new SecurityRequirement()
                        .addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Paste the JWT access token from /api/auth/login")));
    }
}
