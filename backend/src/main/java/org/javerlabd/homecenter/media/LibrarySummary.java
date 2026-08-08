package org.javerlabd.homecenter.media;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Prehľad knižnice — na TV sú z neho tri dlaždice, v management UI úvodná stránka. */
public record LibrarySummary(Map<MediaCategory, Long> countsByCategory, long totalItems, long totalSizeBytes) {

    public List<Tile> tiles() {
        return Arrays.stream(MediaCategory.values())
                .map(category -> new Tile(category, category.label(), countsByCategory.getOrDefault(category, 0L)))
                .toList();
    }

    public record Tile(MediaCategory category, String label, long count) {
    }
}
