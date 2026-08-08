package org.javerlabd.homecenter.config;

import java.util.regex.Pattern;

import org.javerlabd.homecenter.auth.AuthTokenService;
import org.javerlabd.homecenter.auth.BearerTokenAuthenticationFilter;
import org.javerlabd.homecenter.user.Role;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.header.writers.CacheControlHeadersWriter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;

/**
 * Two separate environments with their own rules:
 *
 * <ul>
 *   <li><b>{@code /api/v1/**}</b>—Android TV client. Stateless, exclusively
 *       {@code Authorization: Bearer}, with no session or CSRF.</li>
 *   <li><b>everything else</b>—browser-based management UI. Login form, session,
 *       CSRF, and access restricted to {@code ADMIN}.</li>
 * </ul>
 *
 * <p>The separation is not cosmetic: if sessions also applied to {@code /api/v1/**},
 * where CSRF is disabled, a malicious site could make a logged-in administrator's
 * browser submit a POST request to the server.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Addresses that serve the media files themselves.
     *
     * <p>Spring Security adds {@code Cache-Control: no-store} to every response. This is
     * correct for administration pages but not for media: Chrome builds playback on
     * multiple buffers over the HTTP cache, so with {@code no-store} it does not receive
     * even the first block. The element ends with a {@code stalled} event, an empty buffer,
     * and no error. These responses set their own header in {@code MediaStreamResponse}.
     */
    private static final Pattern MEDIA_PATHS = Pattern.compile(
            ".*/(api/v1/media/\\d+/(stream|poster)|admin/kniznica/\\d+/(stream|stiahnut|poster))");

    /**
     * Argon2id with the default Spring Security parameters (16 MiB, two iterations).
     * It hashes both passwords and PINs. A PIN is short, making a slow hash even more
     * important.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    @Order(1)
    SecurityFilterChain apiFilterChain(HttpSecurity http, AuthTokenService tokenService) throws Exception {
        return http
                .securityMatcher("/api/v1/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        // Login with username + password or username + PIN.
                        .requestMatchers("/api/v1/auth/login").permitAll()
                        // Scanning is an administrator action through the API as well.
                        .requestMatchers(HttpMethod.POST, "/api/v1/scan").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .headers(headersAllowingMediaCache())
                .addFilterBefore(new BearerTokenAuthenticationFilter(tokenService),
                        UsernamePasswordAuthenticationFilter.class)
                // Do not redirect to the login page; the client expects 401, not HTML.
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        // Set the status directly instead of using sendError. sendError triggers
                        // an ERROR dispatch to /error, which is outside /api/v1/** and would be
                        // caught by the UI chain. The client would then receive a redirect to the
                        // login page instead of 403. MockMvc does not perform ERROR dispatches, so
                        // this appears only on a real server.
                        .accessDeniedHandler((request, response, exception) ->
                                response.setStatus(HttpStatus.FORBIDDEN.value())))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain adminFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(requests -> requests
                        // Static files must be available without authentication, or the login
                        // page will have no Bootstrap styling. /img/** carries the favicon and
                        // the TMDB logo—branding, nothing that needs protecting.
                        .requestMatchers("/css/**", "/js/**", "/img/**", "/webjars/**",
                                "/favicon.ico").permitAll()
                        .requestMatchers("/prihlasenie").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // The error page must be accessible, or every sendError() from any chain
                        // will turn into a redirect to the login page.
                        .requestMatchers("/error").permitAll()
                        // Includes /api/openapi and /api/swagger-ui.html; the client contract
                        // must not be publicly exposed on the network.
                        .anyRequest().hasRole("ADMIN"))
                .headers(headersAllowingMediaCache())
                .formLogin(form -> form
                        .loginPage("/prihlasenie")
                        .loginProcessingUrl("/prihlasenie")
                        .defaultSuccessUrl("/admin")
                        .failureUrl("/prihlasenie?chyba")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/odhlasenie")
                        .logoutSuccessUrl("/prihlasenie?odhlasene"))
                .exceptionHandling(handling -> handling.accessDeniedHandler(userRoleAccessDenied()))
                .build();
    }

    /**
     * A user with the {@code USER} role entered the correct password but is not allowed in
     * the management UI. Leaving them on a 403 page would be confusing, so log them out and
     * explain the restriction on the login page.
     *
     * <p>The role condition is required because {@code AccessDeniedException} also represents
     * a failed CSRF token. Without it, a logged-in administrator using an expired page would
     * be silently logged out and told that the account belongs to the TV, which is neither
     * true nor helpful. All other cases therefore follow the standard path to 403.
     */
    private static AccessDeniedHandler userRoleAccessDenied() {
        AccessDeniedHandlerImpl fallback = new AccessDeniedHandlerImpl();
        return (request, response, exception) -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (isLoggedInWithoutAdminRole(authentication)) {
                new SecurityContextLogoutHandler().logout(request, response, authentication);
                response.sendRedirect(request.getContextPath() + "/prihlasenie?rola");
                return;
            }
            fallback.handle(request, response, exception);
        };
    }

    /**
     * Retains security headers but does not add {@code Cache-Control: no-store} where the
     * file itself is served; see {@link #MEDIA_PATHS}.
     */
    private static Customizer<HeadersConfigurer<HttpSecurity>> headersAllowingMediaCache() {
        return headers -> headers
                .cacheControl(HeadersConfigurer.CacheControlConfig::disable)
                .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                        new NegatedRequestMatcher(
                                request -> MEDIA_PATHS.matcher(request.getRequestURI()).matches()),
                        new CacheControlHeadersWriter()));
    }

    private static boolean isLoggedInWithoutAdminRole(@Nullable Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getAuthorities().stream()
                        .noneMatch(granted -> Role.ADMIN.authority().equals(granted.getAuthority()));
    }
}
