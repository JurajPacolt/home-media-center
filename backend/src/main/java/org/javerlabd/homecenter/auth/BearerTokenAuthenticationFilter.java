package org.javerlabd.homecenter.auth;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.javerlabd.homecenter.user.AppUser;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Prihlásenie Android klienta hlavičkou {@code Authorization: Bearer <token>}.
 *
 * <p>Zámerne nie je {@code @Component} — Spring Boot by ho inak zaregistroval aj do
 * hlavného servletového reťazca a bežal by dvakrát. Inštanciu vytvára {@code SecurityConfig}
 * a vkladá ju len do reťazca pre {@code /api/v1/**}.
 *
 * <p>Neplatný token sa tu nerieši chybou — request pokračuje neprihlásený a zamietne
 * ho až autorizácia, ktorá naň odpovie 401.
 */
@RequiredArgsConstructor
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    private final AuthTokenService tokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = bearerToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            tokenService.resolve(token).ifPresent(user -> authenticate(user, request));
        }
        chain.doFilter(request, response);
    }

    private static void authenticate(AppUser user, HttpServletRequest request) {
        AuthenticatedUser principal = new AuthenticatedUser(user);
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

    private static @Nullable String bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            return null;
        }
        String token = header.substring(PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
