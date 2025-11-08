package com.medi.backend.global.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI(Swagger) 문서 설정
 * - 실행/비즈니스 로직에 영향 없음 (문서 메타데이터만 추가)
 * - Swagger UI: /swagger-ui/index.html
 * - OpenAPI JSON: /v3/api-docs
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Medi Backend API",
                version = "v1",
                description = "Medi 백엔드 API 명세 (세션 기반 인증, YouTube 연동 포함)",
                contact = @Contact(name = "Medi Team")
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "Local")
        }
)
@SecurityScheme(
        name = "sessionAuth",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.COOKIE,
        paramName = "MEDI_SESSION",
        description = "세션 쿠키 인증 (credentials: include 필요)"
)
public class OpenApiConfig {
}


