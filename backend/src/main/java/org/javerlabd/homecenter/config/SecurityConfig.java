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
 * Dva oddelené svety s vlastnými pravidlami:
 *
 * <ul>
 *   <li><b>{@code /api/v1/**}</b> — Android TV klient. Bezstavové, výhradne
 *       {@code Authorization: Bearer}, žiadna session ani CSRF.</li>
 *   <li><b>všetko ostatné</b> — management UI v prehliadači. Prihlasovací formulár,
 *       session, CSRF a prístup len pre {@code ADMIN}.</li>
 * </ul>
 *
 * <p>Rozdelenie nie je kozmetické: keby session platila aj na {@code /api/v1/**},
 * kde je CSRF vypnuté, cudzia stránka by vedela prehliadaču prihláseného správcu
 * podstrčiť POST na server.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Adresy, na ktorých tečú samotné súbory médií.
     *
     * <p>Spring Security dáva do každej odpovede {@code Cache-Control: no-store}. Pre
     * administračné stránky je to správne, pre médiá nie: Chrome stavia prehrávanie na
     * multibufferi nad HTTP cache, takže s {@code no-store} nedostane ani prvý blok —
     * element skončí na udalosti {@code stalled} s prázdnym bufferom a bez chyby.
     * Vlastnú hlavičku si tieto odpovede nastavujú v {@code MediaStreamResponse}.
     */
    private static final Pattern MEDIA_PATHS = Pattern.compile(
            ".*/(api/v1/media/\\d+/(stream|poster)|admin/kniznica/\\d+/(stream|stiahnut|poster))");

    /**
     * Argon2id s predvolenými parametrami Spring Security (16 MiB, 2 iterácie).
     * Hashujú sa ním heslá aj PINy — PIN je krátky, takže na pomalom hashi záleží
     * o to viac.
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
                        // Prihlásenie menom + heslom alebo menom + PINom.
                        .requestMatchers("/api/v1/auth/login").permitAll()
                        // Sken je správcovská akcia aj cez API.
                        .requestMatchers(HttpMethod.POST, "/api/v1/scan").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .headers(headersAllowingMediaCache())
                .addFilterBefore(new BearerTokenAuthenticationFilter(tokenService),
                        UsernamePasswordAuthenticationFilter.class)
                // Bez presmerovania na prihlasovaciu stránku — klient chce 401, nie HTML.
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        // Status sa nastavuje priamo, nie cez sendError. sendError totiž spustí
                        // ERROR dispatch na /error, ktorý už nespadá pod /api/v1/** — chytil by
                        // ho UI reťazec a klient by namiesto 403 dostal presmerovanie na
                        // prihlasovaciu stránku. MockMvc ERROR dispatch nerobí, takže sa to
                        // prejaví až na skutočnom serveri.
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
                        // Statické súbory musia ísť aj neprihlásenému, inak je prihlasovacia
                        // stránka bez Bootstrapu.
                        .requestMatchers("/css/**", "/js/**", "/webjars/**", "/favicon.ico").permitAll()
                        .requestMatchers("/prihlasenie").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // Chybová stránka musí byť priechodná, inak sa každé sendError()
                        // z ktoréhokoľvek reťazca zmení na presmerovanie na prihlásenie.
                        .requestMatchers("/error").permitAll()
                        // Vrátane /api/openapi a /api/swagger-ui.html — kontrakt pre klienta
                        // nemá visieť v sieti verejne.
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
     * Používateľ s rolou {@code USER} zadal správne heslo, ale do management UI nepatrí.
     * Nechať ho na 403 by bolo mätúce — radšej ho odhlásime a povieme mu to na
     * prihlasovacej stránke.
     *
     * <p>Podmienka na rolu tu musí byť: {@code AccessDeniedException} nesie aj zlyhanie
     * CSRF tokenu. Bez nej by sa prihlásený správca po vypršanej stránke potichu odhlásil
     * a dostal hlášku o tom, že jeho účet patrí televízoru — čo nie je ani pravda, ani
     * nápomocné. Všetko ostatné preto ide štandardnou cestou na 403.
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
     * Ponechá bezpečnostné hlavičky, ale {@code Cache-Control: no-store} nepridá tam,
     * kde tečie samotný súbor — pozri {@link #MEDIA_PATHS}.
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
