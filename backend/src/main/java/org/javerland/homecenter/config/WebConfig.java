package org.javerland.homecenter.config;

import java.util.Locale;

import lombok.RequiredArgsConstructor;
import org.javerland.homecenter.admin.PasswordChangeInterceptor;
import org.javerland.homecenter.media.MediaCategory;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final PasswordChangeInterceptor passwordChangeInterceptor;

    /**
     * Applies only to the management UI. The TV client's REST API does not enforce a
     * password change because the TV authenticates with a token and does not change passwords.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(passwordChangeInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns("/admin/heslo");
    }

    /**
     * Makes {@code ?category=video} work like {@code ?category=VIDEO} in URLs so the client
     * does not need to handle letter case.
     */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, MediaCategory.class, source -> {
            String value = source.trim();
            return value.isEmpty() ? null : MediaCategory.valueOf(value.toUpperCase(Locale.ROOT));
        });
    }
}
