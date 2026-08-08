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
 * This specification is the contract between the server and Android TV client. Models
 * are not shared between Java and Kotlin; OpenAPI is the only link between them.
 *
 * <p><b>Tag names must stay free of diacritics.</b> The client generator turns each tag
 * into a Kotlin interface name and strips whatever it cannot spell, so {@code Knižnica}
 * would arrive as {@code KninicaApi}. Descriptions are prose and keep their diacritics;
 * only the names are identifiers.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    /** Name of the scheme referenced by {@code @SecurityRequirement} in controllers. */
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
                        // OpenAPI 3.1 rejects a license that carries only a name; the client's
                        // code generator validates the specification and refuses to run without
                        // an SPDX identifier here.
                        .license(new License().name("Apache-2.0").identifier("Apache-2.0")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .description("Token z POST /api/v1/auth/login")))
                // Applies to all operations by default; /auth/login overrides it.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
