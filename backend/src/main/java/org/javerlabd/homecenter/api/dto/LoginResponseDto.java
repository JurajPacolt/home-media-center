package org.javerlabd.homecenter.api.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Odpoveď na prihlásenie. Token si klient uloží a posiela ho v hlavičke
 * {@code Authorization: Bearer} — heslo ani PIN na televízore neostávajú.
 */
public record LoginResponseDto(

        @Schema(description = "Server ho ukazuje jediný raz, znovu sa zobraziť nedá")
        String token,

        Instant expiresAt,

        AuthUserDto user) {
}
