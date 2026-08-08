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
 * Authenticates the Android client through the {@code Authorization: Bearer <token>} header.
 *
 * <p>It is intentionally not a {@code @Component}; otherwise, Spring Boot would also
 * register it in the main servlet chain, causing it to run twice. {@code SecurityConfig}
 * creates the instance and adds it only to the chain for {@code /api/v1/**}.
 *
 * <p>An invalid token does not produce an error here. The request continues unauthenticated
 * and is rejected later by authorization with a 401 response.
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
