package org.javerlabd.homecenter.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.javerlabd.homecenter.api.dto.AuthUserDto;
import org.javerlabd.homecenter.api.dto.LoginRequestDto;
import org.javerlabd.homecenter.api.dto.LoginResponseDto;
import org.javerlabd.homecenter.auth.AuthenticatedUser;
import org.javerlabd.homecenter.auth.AuthTokenService;
import org.javerlabd.homecenter.auth.InvalidLoginException;
import org.javerlabd.homecenter.auth.IssuedToken;
import org.javerlabd.homecenter.user.AppUser;
import org.javerlabd.homecenter.user.UserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Android TV client login with either a password or PIN. The PIN works exclusively
 * through this endpoint and cannot be used to access the management UI.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Prihlásenie", description = "Vydávanie a rušenie tokenov pre TV klienta")
@RequiredArgsConstructor
public class AuthApiController {

    private static final String BEARER = "Bearer ";

    private final UserService userService;
    private final AuthTokenService tokenService;

    @PostMapping("/login")
    @Operation(summary = "Prihlási menom a heslom alebo menom a PINom, vráti token")
    public LoginResponseDto login(@Valid @RequestBody LoginRequestDto request) {
        AppUser user = userService.authenticate(request.username(), request.secret(), true)
                .orElseThrow(InvalidLoginException::new);
        IssuedToken issued = tokenService.issue(user, request.deviceName());
        return new LoginResponseDto(issued.token(), issued.expiresAt(), AuthUserDto.from(user));
    }

    @PostMapping("/logout")
    @Operation(summary = "Zneplatní token, ktorým je request podpísaný")
    @SecurityRequirement(name = "bearer")
    public ResponseEntity<Void> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        if (authorization.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            tokenService.revoke(authorization.substring(BEARER.length()).trim());
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Kto je prihlásený týmto tokenom")
    @SecurityRequirement(name = "bearer")
    public AuthUserDto me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return AuthUserDto.from(principal.user());
    }
}
