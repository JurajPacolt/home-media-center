package org.javerlabd.homecenter.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Nastavenia servera. SMB zdroj tu zámerne nie je — ten sa konfiguruje za behu
 * cez management UI a býva uložený v databáze.
 */
@ConfigurationProperties(prefix = "homecenter")
public record HomeCenterProperties(

        @DefaultValue(DataDirectory.DEFAULT) Path dataDir,
        @DefaultValue Library library,
        @DefaultValue Metadata metadata,
        @DefaultValue Scan scan,
        @DefaultValue Streaming streaming,
        @DefaultValue Security security) {

    /** Prípony, podľa ktorých sken zaraďuje súbory do kategórií. */
    public record Library(
            @DefaultValue({ "mkv", "mp4", "avi", "mov", "m4v", "webm" }) Set<String> videoExtensions,
            @DefaultValue({ "jpg", "jpeg", "png", "gif", "webp" }) Set<String> photoExtensions,
            @DefaultValue({ "mp3", "flac", "m4a", "aac", "ogg", "wav" }) Set<String> audioExtensions) {
    }

    /**
     * Voliteľné filmové metadáta z TMDb. Prázdny token integráciu úplne vypne;
     * SMB sken a prehrávanie preto fungujú aj bez internetu alebo účtu TMDb.
     */
    public record Metadata(
            @DefaultValue("") String tmdbReadAccessToken,
            @DefaultValue("sk-SK") String language,
            @DefaultValue("en-US") String fallbackLanguage,
            @DefaultValue("30d") Duration refreshAfter,
            @DefaultValue("1d") Duration retryAfter,
            @DefaultValue("250ms") Duration requestDelay,
            @DefaultValue("https://api.themoviedb.org/3") String apiBaseUrl,
            @DefaultValue("https://image.tmdb.org/t/p/w342") String imageBaseUrl) {

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
     * @param tokenValidity ako dlho platí prihlásenie Android klienta. Televízor sa
     *                      neprihlasuje často, preto rádovo mesiace.
     */
    public record Security(
            @DefaultValue("90d") Duration tokenValidity) {
    }
}
