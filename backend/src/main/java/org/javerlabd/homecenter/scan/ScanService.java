package org.javerlabd.homecenter.scan;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.javerlabd.homecenter.config.HomeCenterProperties;
import org.javerlabd.homecenter.media.MediaCategory;
import org.javerlabd.homecenter.media.MediaClassifier;
import org.javerlabd.homecenter.media.MediaItem;
import org.javerlabd.homecenter.media.MediaRepository;
import org.javerlabd.homecenter.metadata.MetadataEnrichmentService;
import org.javerlabd.homecenter.metadata.TmdbMetadataResolver;
import org.javerlabd.homecenter.source.SmbAccessException;
import org.javerlabd.homecenter.source.SmbEntry;
import org.javerlabd.homecenter.source.SmbGateway;
import org.javerlabd.homecenter.source.SmbSource;
import org.javerlabd.homecenter.source.SmbSourceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

/**
 * Prechádza Sambu a udržiava index. Beží výhradne na pozadí — v ceste HTTP requestu
 * sa skenovať nesmie, REST API číta len hotový index.
 *
 * <p>Zdrojov môže byť viac. Prechádzajú sa <b>za sebou v jednej úlohe</b>, nie paralelne:
 * domáci NAS nemá dôvod obsluhovať niekoľko súbežných prechodov a sekvenčný priebeh
 * sa dá v UI zmysluplne zobraziť. Každý zdroj dostane vlastný riadok v {@code scan_run},
 * takže sa počítadlá aj prípadná chyba viažu na konkrétny zdroj.
 *
 * <p>Nedostupný zdroj nezhodí zvyšok — zapíše sa mu {@code FAILED} a pokračuje sa ďalším.
 */
@Service
@Slf4j
public class ScanService implements DisposableBean {

    /** Po koľkých súboroch sa priebeh zapíše do databázy, aby ho UI videlo. */
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
     * Naštartuje sken všetkých zapnutých zdrojov a hneď sa vráti — volajúci (UI aj REST)
     * nečaká na dobehnutie.
     *
     * @throws org.javerlabd.homecenter.source.NoActiveSourceException ak nie je zapnutý žiadny zdroj
     * @throws ScanAlreadyRunningException                            ak už jeden sken beží
     */
    public ScanStart triggerAll(ScanTrigger trigger) {
        return start(sourceService.requireEnabled(), trigger);
    }

    /**
     * Naštartuje sken jedného konkrétneho zdroja. Vypnutý zdroj sa takto dá preskenovať
     * zámerne — vypnutie hovorí len to, že sa nemá brať do naplánovaného behu.
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

    /** Posledný sken každého zdroja — dashboard z toho skladá stĺpec „naposledy skenované“. */
    public Map<Long, ScanRun> latestBySource() {
        return runRepository.findLatestBySource();
    }

    /** Uzavrie behy, ktoré ostali v stave RUNNING po páde servera. */
    public void closeInterruptedRuns() {
        int closed = runRepository.markInterrupted();
        if (closed > 0) {
            log.warn("Uzavretých {} skenov prerušených reštartom servera", closed);
        }
    }

    /**
     * Jeden zdroj. Chyba sa zapíše do jeho {@code scan_run} a ostatné zdroje pokračujú —
     * vypnutý NAS nesmie znamenať, že sa nepreindexuje ani ten druhý.
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
            // Nedostupné úložisko je bežný stav (vypnutý NAS), netreba k nemu stack trace.
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

    /** Priečinok čakajúci na spracovanie spolu s hĺbkou, v ktorej leží. */
    private record Location(String path, int depth) {
    }
}
