package org.javerlabd.homecenter.metadata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Set;

import org.javerlabd.homecenter.config.HomeCenterProperties;
import org.javerlabd.homecenter.media.MediaItem;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

/** Local poster cache; neither the browser nor TV retrieves posters directly from the internet. */
@Service
public class PosterStorage {

    private final Path directory;

    public PosterStorage(HomeCenterProperties properties) {
        this.directory = properties.dataDir().resolve("posters").toAbsolutePath().normalize();
        try {
            Files.createDirectories(directory);
        } catch (IOException ex) {
            throw new IllegalStateException("Nepodarilo sa vytvoriť cache plagátov " + directory, ex);
        }
    }

    public String save(String cacheKey, String remotePath, byte[] content) {
        String extension = extensionOf(remotePath);
        String safeKey = cacheKey.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
        String fileName = safeKey + extension;
        Path target = resolve(fileName);
        try {
            Files.write(target, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("Nepodarilo sa uložiť plagát " + fileName, ex);
        }
        return fileName;
    }

    public PosterAsset open(MediaItem item) {
        if (item.metadata() == null || !item.metadata().hasPoster()) {
            throw new PosterNotFoundException(item.requireId());
        }
        Path file = resolve(item.metadata().posterFile());
        if (!Files.isRegularFile(file)) {
            throw new PosterNotFoundException(item.requireId());
        }
        try {
            return new PosterAsset(new FileSystemResource(file), contentType(file), Files.size(file));
        } catch (IOException ex) {
            throw new PosterNotFoundException(item.requireId());
        }
    }

    /** Removes files no longer referenced by an index item after a scan finishes. */
    public int deleteUnreferenced(Set<String> referencedFiles) {
        int deleted = 0;
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                if (!referencedFiles.contains(file.getFileName().toString()) && Files.deleteIfExists(file)) {
                    deleted++;
                }
            }
            return deleted;
        } catch (IOException ex) {
            throw new IllegalStateException("Nepodarilo sa upratať cache plagátov " + directory, ex);
        }
    }

    private Path resolve(String fileName) {
        Path resolved = directory.resolve(fileName).normalize();
        if (!resolved.getParent().equals(directory)) {
            throw new IllegalArgumentException("Neplatný názov súboru plagátu");
        }
        return resolved;
    }

    private static String extensionOf(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return ".png";
        }
        if (lower.endsWith(".webp")) {
            return ".webp";
        }
        return ".jpg";
    }

    private static String contentType(Path file) {
        String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }
}
