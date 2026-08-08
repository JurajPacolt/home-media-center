package org.javerlabd.homecenter.media;

import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read layer over the index, shared by the REST API and Thymeleaf UI. Samba is never
 * scanned here; that is the background {@code ScanService}'s responsibility.
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

    /** Index usage by source for the management UI source overview. */
    public Map<Long, SourceUsage> usageBySource() {
        return repository.usageBySource();
    }

    public SourceUsage usageOf(long sourceId) {
        return repository.usageBySource().getOrDefault(sourceId, SourceUsage.empty(sourceId));
    }
}
