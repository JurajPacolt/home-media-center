package org.javerlabd.homecenter.config;

import java.util.Locale;

import lombok.RequiredArgsConstructor;
import org.javerlabd.homecenter.admin.PasswordChangeInterceptor;
import org.javerlabd.homecenter.media.MediaCategory;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final PasswordChangeInterceptor passwordChangeInterceptor;

    /**
     * Platí len na management UI. REST API pre TV klienta sa vynúteným prihlásením
     * nezaoberá — televízor sa prihlasuje tokenom a heslo nemení.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(passwordChangeInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns("/admin/heslo");
    }

    /**
     * Aby v URL fungovalo {@code ?category=video} rovnako ako {@code ?category=VIDEO} —
     * klient nemá riešiť veľkosť písmen.
     */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, MediaCategory.class, source -> {
            String value = source.trim();
            return value.isEmpty() ? null : MediaCategory.valueOf(value.toUpperCase(Locale.ROOT));
        });
    }
}
