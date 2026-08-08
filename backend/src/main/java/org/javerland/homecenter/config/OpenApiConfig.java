package org.javerland.homecenter.config;

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
 * <p>The specification is written in <b>English</b>: it is read by whoever writes a
 * client, and tag names become Kotlin identifiers in the generated code. Renaming a tag
 * therefore renames a class the client imports—{@code Library} produces
 * {@code LibraryApi}—so it is never a cosmetic change. Diacritics are impossible for the
 * same reason: the generator strips what it cannot spell, and {@code Knižnica} used to
 * arrive as {@code KninicaApi}.
 *
 * <p>This applies to the specification only. {@code ProblemDetail} titles, validation
 * messages and log output stay Slovak—they belong to a running application, not to the
 * contract between two codebases.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    /** Name of the scheme referenced by {@code @SecurityRequirement} in controllers. */
    private static final String BEARER_SCHEME = "bearer";

    @Bean
    OpenAPI homeCenterOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Home Media Center — API")
                        .version("v1")
                        .description("""
                                REST interface for the Android TV client. Media is split into three
                                categories (VIDEO, PHOTO, AUDIO). The server reads from its local index
                                and forwards files from Samba through /api/v1/media/{id}/stream with
                                Range request support.

                                Everything except /api/v1/auth/login requires the
                                `Authorization: Bearer <token>`. A token is issued by logging in
                                with a username and password, or a username and PIN.
                                """)
                        // OpenAPI 3.1 rejects a license that carries only a name; the client's
                        // code generator validates the specification and refuses to run without
                        // an SPDX identifier here.
                        .license(new License().name("Apache-2.0").identifier("Apache-2.0")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .description("Token from POST /api/v1/auth/login")))
                // Applies to all operations by default; /auth/login overrides it.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
