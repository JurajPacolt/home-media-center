package org.javerland.homecenter.scan;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.javerland.homecenter.config.HomeCenterProperties;
import org.javerland.homecenter.media.MediaCategory;
import org.javerland.homecenter.media.MediaClassifier;
import org.javerland.homecenter.media.MediaItem;
import org.javerland.homecenter.media.MediaRepository;
import org.javerland.homecenter.metadata.MetadataEnrichmentService;
import org.javerland.homecenter.metadata.TmdbMetadataResolver;
import org.javerland.homecenter.source.SmbAccessException;
import org.javerland.homecenter.source.SmbEntry;
import org.javerland.homecenter.source.SmbGateway;
import org.javerland.homecenter.source.SmbSource;
import org.javerland.homecenter.source.SmbSourceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

/**
 * Traverses Samba and maintains the index. It runs exclusively in the background;
 * scanning must not occur in the HTTP request path, and the REST API reads only the
 * completed index.
 *
 * <p>Multiple sources are supported. They are processed <b>sequentially in one task</b>,
 * not in parallel: a home NAS has no reason to serve several concurrent traversals,
 * and sequential progress can be represented meaningfully in the UI. Each source gets
 * its own row in {@code scan_run}, associating counters and any error with that source.
 *
 * <p>An unavailable source does not stop the rest; it is marked {@code FAILED}, and
 * processing continues with the next source.
 */
@Service
@Slf4j
public class ScanService implements DisposableBean {

    /** Number of files between progress writes to the database for UI visibility. */
    private static final int PROGRESS_EVERY = 250;

    private final SmbSourceService sourceService;
    private final SmbGateway gateway;
    private final MediaRepository mediaRepository;
    private final MetadataEnrichmentService metadataService;
    private final ScanRunRepository runRepository;
    private final MediaClassifier classifier;
    private final int maxDepth;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("media-scan-", 0).factory());

    public ScanService(SmbSourceService sourceService,
                       SmbGateway gateway,
                       MediaRepository mediaRepository,
                       MetadataEnrichmentService metadataService,
                       ScanRunRepository runRepository,
                       MediaClassifier classifier,
                       HomeCenterProperties properties) {
        this.sourceService = sourceService;
        this.gateway = gateway;
        this.mediaRepository = mediaRepository;
        this.metadataService = metadataService;
        this.runRepository = runRepository;
        this.classifier = classifier;
        this.maxDepth = properties.scan().maxDepth();
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * Starts a scan of all enabled sources and returns immediately; neither UI nor REST
     * callers wait for completion.
     *
     * @throws org.javerland.homecenter.source.NoActiveSourceException if no source is enabled
     * @throws ScanAlreadyRunningException                            if a scan is already running
     */
    public ScanStart triggerAll(ScanTrigger trigger) {
        return start(sourceService.requireEnabled(), trigger);
    }

    /**
     * Starts a scan of one specific source. A disabled source can intentionally be scanned
     * this way; disabling only excludes it from scheduled runs.
     */
    public ScanStart triggerOne(long sourceId, ScanTrigger trigger) {
        return start(List.of(sourceService.require(sourceId)), trigger);
    }

    private ScanStart start(List<SmbSource> sources, ScanTrigger trigger) {
        if (!running.compareAndSet(false, true)) {
            throw new ScanAlreadyRunningException();
        }
        try {
            executor.execute(() -> {
                try {
                    TmdbMetadataResolver.Session metadataSession = metadataService.newSession();
                    sources.forEach(source -> scanQuietly(source, trigger, metadataSession));
                } finally {
                    metadataService.cleanupPosters();
                    running.set(false);
                }
            });
        } catch (RuntimeException ex) {
            running.set(false);
            throw ex;
        }
        log.info("Spustený sken ({}) nad {} zdrojmi: {}", trigger, sources.size(),
                sources.stream().map(SmbSource::name).toList());
        return new ScanStart(sources.stream().map(SmbSource::name).toList());
    }

    public Optional<ScanRun> latest() {
        return runRepository.findLatest();
    }

    public List<ScanRun> history(int limit) {
        return runRepository.findRecent(limit);
    }

    /** Latest scan for each source, used by the dashboard's "last scanned" column. */
    public Map<Long, ScanRun> latestBySource() {
        return runRepository.findLatestBySource();
    }

    /** Closes runs left in the RUNNING state after a server failure. */
    public void closeInterruptedRuns() {
        int closed = runRepository.markInterrupted();
        if (closed > 0) {
            log.warn("Uzavretých {} skenov prerušených reštartom servera", closed);
        }
    }

    /**
     * Processes one source. An error is written to its {@code scan_run}, and the remaining
     * sources continue; an offline NAS must not prevent another one from being reindexed.
     */
    private void scanQuietly(SmbSource source, ScanTrigger trigger,
                             TmdbMetadataResolver.Session metadataSession) {
        long scanId;
        try {
            scanId = runRepository.start(source.requireId(), trigger).requireId();
        } catch (RuntimeException ex) {
            log.error("Sken zdroja {} sa nepodarilo ani založiť", source.name(), ex);
            return;
        }
        execute(source, scanId, metadataSession);
    }

    private void execute(SmbSource source, long scanId,
                         TmdbMetadataResolver.Session metadataSession) {
        long sourceId = source.requireId();
        ScanCounters counters = new ScanCounters();
        int lastReported = 0;
        try {
            Deque<Location> queue = new ArrayDeque<>();
            queue.push(new Location("", 0));
            while (!queue.isEmpty()) {
                Location current = queue.pop();
                List<SmbEntry> entries = gateway.list(source, current.path());
                counters.directoryScanned();

                for (SmbEntry entry : entries) {
                    if (entry.directory()) {
                        if (current.depth() < maxDepth) {
                            queue.push(new Location(entry.path(), current.depth() + 1));
                        } else {
                            log.debug("Preskočený priečinok {} — prekročená hĺbka {}", entry.path(), maxDepth);
                        }
                        continue;
                    }
                    index(sourceId, scanId, entry, counters, metadataSession);
                }

                if (counters.getFilesSeen() - lastReported >= PROGRESS_EVERY) {
                    lastReported = counters.getFilesSeen();
                    runRepository.updateProgress(scanId, counters);
                }
            }

            counters.itemsRemoved(mediaRepository.deleteMissedBy(sourceId, scanId));
            runRepository.finish(scanId, ScanStatus.COMPLETED, counters, null);
            log.info("Sken #{} zdroja {} hotový: {} súborov, +{} / ~{} / -{}", scanId, source.name(),
                    counters.getFilesSeen(), counters.getItemsAdded(), counters.getItemsUpdated(),
                    counters.getItemsRemoved());
        } catch (SmbAccessException ex) {
            // Unavailable storage is an ordinary state (an offline NAS); no stack trace is needed.
            log.warn("Sken #{} zdroja {} zlyhal: {}", scanId, source.name(), ex.getMessage());
            runRepository.finish(scanId, ScanStatus.FAILED, counters, message(ex));
        } catch (RuntimeException ex) {
            log.error("Sken #{} zdroja {} zlyhal", scanId, source.name(), ex);
            runRepository.finish(scanId, ScanStatus.FAILED, counters, message(ex));
        }
    }

    private void index(long sourceId, long scanId, SmbEntry entry, ScanCounters counters,
                       TmdbMetadataResolver.Session metadataSession) {
        Optional<MediaCategory> category = classifier.categoryOf(entry.name());
        if (category.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        MediaItem item = new MediaItem(
                null,
                sourceId,
                category.get(),
                entry.path(),
                entry.name(),
                MediaClassifier.titleOf(entry.name()),
                MediaClassifier.extensionOf(entry.name()),
                entry.sizeBytes(),
                entry.modifiedAt(),
                classifier.contentTypeOf(entry.name()),
                null,
                now,
                now);
        counters.fileIndexed(mediaRepository.upsert(item, scanId));
        metadataService.enrich(item, metadataSession);
    }

    private static String message(RuntimeException ex) {
        String message = ex.getMessage();
        return (message == null || message.isBlank()) ? ex.getClass().getSimpleName() : message;
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }

    /** Directory awaiting processing together with its depth. */
    private record Location(String path, int depth) {
    }
}
