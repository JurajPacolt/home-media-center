package org.javerlabd.homecenter.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Táto špecifikácia je zmluva medzi serverom a Android TV klientom — modely sa
 * medzi Javou a Kotlinom nezdieľajú, drží ich pohromade len OpenAPI.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    /** Meno schémy, na ktoré sa odkazuje {@code @SecurityRequirement} v controlleroch. */
    private static final String BEARER_SCHEME = "bearer";

    @Bean
    OpenAPI homeCenterOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Domáce mediacentrum — API")
                        .version("v1")
                        .description("""
                                REST rozhranie pre Android TV klienta. Médiá sú rozdelené na tri kategórie
                                (VIDEO, PHOTO, AUDIO). Server číta z lokálneho indexu a súbory zo Samby
                                preposiela cez /api/v1/media/{id}/stream s podporou Range requestov.

                                Všetko okrem /api/v1/auth/login vyžaduje hlavičku
                                `Authorization: Bearer <token>`. Token vydá prihlásenie menom a heslom
                                alebo menom a PINom.
                                """)
                        .license(new License().name("Domáce použitie")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .description("Token z POST /api/v1/auth/login")))
                // Predvolene platí pre všetky operácie; /auth/login si to prebíja sám.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
