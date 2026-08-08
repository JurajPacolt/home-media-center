package org.javerland.homecenter.metadata;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.javerland.homecenter.config.HomeCenterProperties;
import org.javerland.homecenter.media.MediaCategory;
import org.javerland.homecenter.media.MediaItem;
import org.javerland.homecenter.media.MediaMetadata;
import org.javerland.homecenter.media.MediaRepository;
import org.javerland.homecenter.media.MetadataStatus;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/** Enriches the stored index; an internet failure must never fail the SMB scan. */
@Service
@Slf4j
public class MetadataEnrichmentService {

    private final MediaRepository repository;
    private final MediaNameParser parser;
    private final List<MetadataProvider> providers;
    private final PosterStorage posters;
    private final HomeCenterProperties.Metadata properties;

    public MetadataEnrichmentService(MediaRepository repository,
                                     MediaNameParser parser,
                                     List<MetadataProvider> providers,
                                     PosterStorage posters,
                                     HomeCenterProperties properties) {
        this.repository = repository;
        this.parser = parser;
        this.providers = List.copyOf(providers);
        this.posters = posters;
        this.properties = properties.metadata();
    }

    /**
     * Picks the provider for the whole scan: TMDb when a token is configured, otherwise the
     * token-free Cinemeta. The choice is fixed for the run so one index never mixes
     * identifiers from two providers.
     */
    public MetadataSession newSession() {
        MetadataProvider provider = activeProvider();
        if (provider != null) {
            log.info("Metadáta sa počas skenu doplnia z {}", provider.name());
        }
        return new MetadataSession(provider);
    }

    /**
     * Name of the provider a scan would use right now, or {@code null} when enrichment is off.
     * The management UI needs it because every provider has its own attribution obligations.
     */
    public @Nullable String activeProviderName() {
        MetadataProvider provider = activeProvider();
        return provider == null ? null : provider.name();
    }

    private @Nullable MetadataProvider activeProvider() {
        return providers.stream()
                .filter(MetadataProvider::enabled)
                .findFirst()
                .orElse(null);
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

    public void enrich(MediaItem scannedItem, MetadataSession session) {
        if (scannedItem.category() != MediaCategory.VIDEO) {
            return;
        }
        MediaItem stored = repository.findBySourceAndPath(
                        scannedItem.sourceId(), scannedItem.relativePath())
                .orElseThrow();
        ParsedVideoName parsed = parser.parse(scannedItem.relativePath(), scannedItem.fileName());
        // The local estimate is a fallback. A repeated scan must not erase a more precise
        // provider collection/series key merely because its refresh is not due yet.
        if (stored.metadata() == null || !stored.metadata().hasProviderData()) {
            repository.saveStructure(stored.requireId(), parsed.structure());
        }

        MetadataProvider provider = session.provider();
        // A disabled session means the provider already failed during this scan. Leaving the
        // item untouched keeps its previous status, so the next scan retries it instead of
        // recording a NOT_FOUND that would only be refreshed in thirty days.
        if (provider == null || !session.available() || !due(stored.metadata(), Instant.now())) {
            return;
        }

        try {
            Optional<ResolvedVideoMetadata> resolved = provider.resolve(parsed, session);
            if (resolved.isEmpty()) {
                failedOrMissing(stored, MetadataStatus.NOT_FOUND);
                return;
            }
            ResolvedVideoMetadata metadata = resolved.get();
            String posterFile = existingPoster(stored);
            if (metadata.remotePosterPath() != null) {
                try {
                    byte[] image = provider.downloadPoster(metadata.remotePosterPath());
                    posterFile = posters.save(metadata.posterCacheKey(), metadata.remotePosterPath(), image);
                } catch (RuntimeException ex) {
                    log.warn("Plagát pre {} sa nepodarilo uložiť: {}",
                            scannedItem.relativePath(), message(ex));
                }
            }
            repository.saveMetadata(stored.requireId(), metadata.toUpdate(provider.name(), posterFile));
        } catch (RestClientException ex) {
            session.disable();
            failedOrMissing(stored, MetadataStatus.FAILED);
            log.warn("{} je počas tohto skenu nedostupné, ďalšie požiadavky sa preskočia: {}",
                    provider.name(), message(ex));
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
