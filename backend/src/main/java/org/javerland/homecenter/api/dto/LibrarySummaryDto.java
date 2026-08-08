package org.javerland.homecenter.api.dto;

import java.util.List;

import org.javerland.homecenter.media.LibrarySummary;
import org.javerland.homecenter.media.MediaCategory;
import org.javerland.homecenter.scan.ScanRun;
import org.jspecify.annotations.Nullable;

/** Data for the three tiles on the TV client's home screen. */
public record LibrarySummaryDto(
        List<CategoryTileDto> categories,
        long totalItems,
        long totalSizeBytes,
        @Nullable ScanRunDto lastScan) {

    public static LibrarySummaryDto from(LibrarySummary summary, @Nullable ScanRun lastScan) {
        List<CategoryTileDto> tiles = summary.tiles().stream()
                .map(tile -> new CategoryTileDto(tile.category(), tile.label(), tile.count()))
                .toList();
        return new LibrarySummaryDto(tiles, summary.totalItems(), summary.totalSizeBytes(),
                lastScan == null ? null : ScanRunDto.from(lastScan));
    }

    public record CategoryTileDto(MediaCategory category, String label, long count) {
    }
}
