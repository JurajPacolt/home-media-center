package org.javerlabd.homecenter.metadata;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.javerlabd.homecenter.config.HomeCenterProperties;
import org.javerlabd.homecenter.media.MediaCategory;
import org.javerlabd.homecenter.media.MediaItem;
import org.javerlabd.homecenter.media.MediaMetadata;
import org.javerlabd.homecenter.media.MediaRepository;
import org.javerlabd.homecenter.media.MetadataStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/** Enriches the stored index; an internet failure must never fail the SMB scan. */
@Service
@Slf4j
public class MetadataEnrichmentService {

    private final MediaRepository repository;
    private final MediaNameParser parser;
    private final TmdbClient client;
    private final TmdbMetadataResolver resolver;
    private final PosterStorage posters;
    private final HomeCenterProperties.Metadata properties;

    public MetadataEnrichmentService(MediaRepository repository,
                                     MediaNameParser parser,
                                     TmdbClient client,
                                     TmdbMetadataResolver resolver,
                                     PosterStorage posters,
                                     HomeCenterProperties properties) {
        this.repository = repository;
        this.parser = parser;
        this.client = client;
        this.resolver = resolver;
        this.posters = posters;
        this.properties = properties.metadata();
    }

    public TmdbMetadataResolver.Session newSession() {
        return resolver.newSession();
    }

    public void cleanupPosters() {
        try {
            int deleted = posters.deleteUnreferenced(repository.findPosterFiles());
            if (deleted > 0) {
                log.info("Z cache odstránených {} nepoužívaných plagátov", deleted);
            }
        } catch (RuntimeException ex) {
            // Cache cleanup is maintenance and must not retroactively fail a completed SMB scan.
            log.warn("Cache plagátov sa nepodarilo upratať: {}", message(ex));
        }
    }

    public void enrich(MediaItem scannedItem, TmdbMetadataResolver.Session session) {
        if (scannedItem.category() != MediaCategory.VIDEO) {
            return;
        }
        MediaItem stored = repository.findBySourceAndPath(
                        scannedItem.sourceId(), scannedItem.relativePath())
                .orElseThrow();
        ParsedVideoName parsed = parser.parse(scannedItem.relativePath(), scannedItem.fileName());
        // The local estimate is a fallback. A repeated scan must not erase a more precise
        // TMDb collection/series key merely because its refresh is not due yet.
        if (stored.metadata() == null || !stored.metadata().hasProviderData()) {
            repository.saveStructure(stored.requireId(), parsed.structure());
        }

        if (!client.enabled() || !due(stored.metadata(), Instant.now())) {
            return;
        }

        try {
            Optional<ResolvedVideoMetadata> resolved = resolver.resolve(parsed, session);
            if (resolved.isEmpty()) {
                failedOrMissing(stored, MetadataStatus.NOT_FOUND);
                return;
            }
            ResolvedVideoMetadata metadata = resolved.get();
            String posterFile = existingPoster(stored);
            if (metadata.remotePosterPath() != null) {
                try {
                    byte[] image = client.downloadPoster(metadata.remotePosterPath());
                    posterFile = posters.save(metadata.posterCacheKey(), metadata.remotePosterPath(), image);
                } catch (RuntimeException ex) {
                    log.warn("Plagát pre {} sa nepodarilo uložiť: {}",
                            scannedItem.relativePath(), message(ex));
                }
            }
            repository.saveMetadata(stored.requireId(), metadata.toUpdate(posterFile));
        } catch (RestClientException ex) {
            session.disable();
            failedOrMissing(stored, MetadataStatus.FAILED);
            log.warn("TMDb je počas tohto skenu nedostupné, ďalšie požiadavky sa preskočia: {}",
                    message(ex));
        } catch (RuntimeException ex) {
            failedOrMissing(stored, MetadataStatus.FAILED);
            log.warn("Metadáta pre {} sa nepodarilo doplniť: {}",
                    scannedItem.relativePath(), message(ex));
        }
    }

    private boolean due(MediaMetadata metadata, Instant now) {
        if (metadata == null || metadata.status() == MetadataStatus.PENDING || metadata.updatedAt() == null) {
            return true;
        }
        Duration age = metadata.status() == MetadataStatus.FAILED
                ? properties.retryAfter() : properties.refreshAfter();
        return !metadata.updatedAt().plus(age).isAfter(now);
    }

    private void failedOrMissing(MediaItem stored, MetadataStatus status) {
        // markMetadata changes only the status and attempt time. The old description,
        // poster, and grouping remain usable, while retryAfter retries FAILED in one day.
        repository.markMetadata(stored.requireId(), status);
    }

    private static String existingPoster(MediaItem item) {
        return item.metadata() != null ? item.metadata().posterFile() : null;
    }

    private static String message(RuntimeException ex) {
        return ex.getMessage() == null || ex.getMessage().isBlank()
                ? ex.getClass().getSimpleName() : ex.getMessage();
    }
}
