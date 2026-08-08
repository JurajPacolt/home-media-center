package org.javerlabd.homecenter.media;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.javerlabd.homecenter.config.HomeCenterProperties;
import org.springframework.stereotype.Component;

/**
 * Zaradí súbor do kategórie podľa prípony a určí Content-Type. Typ sa nezisťuje
 * čítaním obsahu — sken by inak musel siahnuť na každý súbor na Sambe.
 */
@Component
public class MediaClassifier {

    private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
            Map.entry("mkv", "video/x-matroska"),
            Map.entry("mp4", "video/mp4"),
            Map.entry("m4v", "video/mp4"),
            Map.entry("webm", "video/webm"),
            Map.entry("avi", "video/x-msvideo"),
            Map.entry("mov", "video/quicktime"),
            Map.entry("wmv", "video/x-ms-wmv"),
            Map.entry("mpg", "video/mpeg"),
            Map.entry("mpeg", "video/mpeg"),
            Map.entry("ts", "video/mp2t"),
            Map.entry("m2ts", "video/mp2t"),
            Map.entry("flv", "video/x-flv"),
            Map.entry("3gp", "video/3gpp"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"),
            Map.entry("gif", "image/gif"),
            Map.entry("bmp", "image/bmp"),
            Map.entry("webp", "image/webp"),
            Map.entry("heic", "image/heic"),
            Map.entry("heif", "image/heif"),
            Map.entry("tif", "image/tiff"),
            Map.entry("tiff", "image/tiff"),
            Map.entry("mp3", "audio/mpeg"),
            Map.entry("flac", "audio/flac"),
            Map.entry("m4a", "audio/mp4"),
            Map.entry("alac", "audio/mp4"),
            Map.entry("aac", "audio/aac"),
            Map.entry("ogg", "audio/ogg"),
            Map.entry("oga", "audio/ogg"),
            Map.entry("opus", "audio/opus"),
            Map.entry("wav", "audio/wav"),
            Map.entry("wma", "audio/x-ms-wma"),
            Map.entry("aiff", "audio/aiff"));

    private static final String FALLBACK_CONTENT_TYPE = "application/octet-stream";

    private final Map<String, MediaCategory> categoriesByExtension;

    public MediaClassifier(HomeCenterProperties properties) {
        Map<String, MediaCategory> mapping = new HashMap<>();
        index(mapping, properties.library().videoExtensions(), MediaCategory.VIDEO);
        index(mapping, properties.library().photoExtensions(), MediaCategory.PHOTO);
        index(mapping, properties.library().audioExtensions(), MediaCategory.AUDIO);
        this.categoriesByExtension = Map.copyOf(mapping);
    }

    private static void index(Map<String, MediaCategory> mapping, Set<String> extensions, MediaCategory category) {
        if (extensions == null) {
            return;
        }
        for (String extension : extensions) {
            mapping.put(extension.toLowerCase(Locale.ROOT).replace(".", ""), category);
        }
    }

    /** Prázdny výsledok znamená, že súbor do knižnice nepatrí a sken ho preskočí. */
    public Optional<MediaCategory> categoryOf(String fileName) {
        return Optional.ofNullable(categoriesByExtension.get(extensionOf(fileName)));
    }

    public String contentTypeOf(String fileName) {
        return CONTENT_TYPES.getOrDefault(extensionOf(fileName), FALLBACK_CONTENT_TYPE);
    }

    public static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * Základný názov na zobrazenie pred obohatením. Zámerne len uprace
     * oddeľovače; rok a release značky spracúva následne {@code MediaNameParser}.
     */
    public static String titleOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String cleaned = base.replace('_', ' ').replace('.', ' ').trim().replaceAll("\\s+", " ");
        return cleaned.isEmpty() ? fileName : cleaned;
    }
}
