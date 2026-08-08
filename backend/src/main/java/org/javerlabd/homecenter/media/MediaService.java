package org.javerlabd.homecenter.media;

import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Čítacia vrstva nad indexom, spoločná pre REST API aj Thymeleaf UI. Samba sa tu
 * nikdy neskenuje — to je práca {@code ScanService} na pozadí.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MediaService {

    private final MediaRepository repository;

    public List<MediaItem> find(MediaQuery query) {
        return repository.find(query);
    }

    public long count(MediaQuery query) {
        return repository.count(query);
    }

    public List<MediaGenre> genres() {
        return repository.findGenres();
    }

    public MediaItem require(long id) {
        return repository.findById(id).orElseThrow(() -> new MediaNotFoundException(id));
    }

    public LibrarySummary summary() {
        Map<MediaCategory, Long> counts = repository.countByCategory();
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return new LibrarySummary(counts, total, repository.totalSizeBytes());
    }

    /** Rozdelenie indexu podľa zdroja — pre prehľad zdrojov v management UI. */
    public Map<Long, SourceUsage> usageBySource() {
        return repository.usageBySource();
    }

    public SourceUsage usageOf(long sourceId) {
        return repository.usageBySource().getOrDefault(sourceId, SourceUsage.empty(sourceId));
    }
}
