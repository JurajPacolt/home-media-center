package org.javerland.homecenter.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Server settings. The SMB source is intentionally absent because it is configured at
 * runtime through the management UI and stored in the database.
 */
@ConfigurationProperties(prefix = "homecenter")
public record HomeCenterProperties(

        @DefaultValue(DataDirectory.DEFAULT) Path dataDir,
        @DefaultValue Library library,
        @DefaultValue Metadata metadata,
        @DefaultValue Scan scan,
        @DefaultValue Streaming streaming,
        @DefaultValue Security security) {

    /** Extensions used by the scanner to classify files into categories. */
    public record Library(
            @DefaultValue({ "mkv", "mp4", "avi", "mov", "m4v", "webm" }) Set<String> videoExtensions,
            @DefaultValue({ "jpg", "jpeg", "png", "gif", "webp" }) Set<String> photoExtensions,
            @DefaultValue({ "mp3", "flac", "m4a", "aac", "ogg", "wav" }) Set<String> audioExtensions) {
    }

    /**
     * Optional movie metadata. TMDb is used when a Read Access Token is configured; otherwise the
     * scan falls back to Cinemeta, which needs no account but only provides English texts. With
     * both switched off, SMB scanning and playback still work without internet access.
     */
    public record Metadata(
            @DefaultValue("") String tmdbReadAccessToken,
            @DefaultValue("sk-SK") String language,
            @DefaultValue("en-US") String fallbackLanguage,
            @DefaultValue("30d") Duration refreshAfter,
            @DefaultValue("1d") Duration retryAfter,
            @DefaultValue("250ms") Duration requestDelay,
            @DefaultValue("https://api.themoviedb.org/3") String apiBaseUrl,
            @DefaultValue("https://image.tmdb.org/t/p/w342") String imageBaseUrl,
            @DefaultValue("true") boolean cinemetaFallback,
            @DefaultValue("https://v3-cinemeta.strem.io") String cinemetaBaseUrl) {

        /** True when a TMDb token is configured. */
        public boolean enabled() {
            return tmdbReadAccessToken != null && !tmdbReadAccessToken.isBlank();
        }
    }

    public record Scan(
            @DefaultValue("0 30 3 * * *") String cron,
            @DefaultValue("false") boolean onStartup,
            @DefaultValue("32") int maxDepth) {
    }

    public record Streaming(
            @DefaultValue("256KB") DataSize bufferSize) {

        public int bufferSizeBytes() {
            return Math.toIntExact(bufferSize.toBytes());
        }
    }

    /**
     * @param tokenValidity how long an Android client login remains valid. The TV does
     *                      not log in frequently, so this is measured in months.
     */
    public record Security(
            @DefaultValue("90d") Duration tokenValidity) {
    }
}
