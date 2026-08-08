package org.javerlabd.homecenter.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

/**
 * Prihlásenie Android TV klienta.
 *
 * @param secret     heslo alebo PIN — server skúsi oboje a klient nemusí vedieť, čo drží
 * @param deviceName ako sa zariadenie ukáže v zozname prihlásených (napr. „Obývačka“)
 */
public record LoginRequestDto(

        @NotBlank(message = "Zadaj používateľské meno")
        String username,

        @NotBlank(message = "Zadaj heslo alebo PIN")
        @Schema(description = "Heslo alebo PIN, ak ho používateľ má nastavený")
        String secret,

        @Nullable String deviceName) {
}
