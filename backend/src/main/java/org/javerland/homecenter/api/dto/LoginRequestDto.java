package org.javerland.homecenter.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

/**
 * Android TV client login.
 *
 * @param secret     password or PIN; the server tries both, so the client need not identify it
 * @param deviceName how the device appears in the list of active sessions (for example, "Living Room")
 */
public record LoginRequestDto(

        @NotBlank(message = "Zadaj používateľské meno")
        String username,

        @NotBlank(message = "Zadaj heslo alebo PIN")
        @Schema(description = "Heslo alebo PIN, ak ho používateľ má nastavený")
        String secret,

        @Nullable String deviceName) {
}
