package org.javerland.homecenter.api.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Login response. The client stores the token and sends it in the
 * {@code Authorization: Bearer} header; neither the password nor PIN remains on the TV.
 */
public record LoginResponseDto(

        @Schema(description = "Server ho ukazuje jediný raz, znovu sa zobraziť nedá")
        String token,

        Instant expiresAt,

        AuthUserDto user) {
}
