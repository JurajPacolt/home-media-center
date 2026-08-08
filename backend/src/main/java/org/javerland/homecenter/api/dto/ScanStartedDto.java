package org.javerland.homecenter.api.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import org.javerland.homecenter.scan.ScanStart;

/**
 * Confirms that a scan was queued. History rows are created only when a source's turn
 * begins, so progress is retrieved from {@code GET /api/v1/scan/latest}.
 */
public record ScanStartedDto(

        @Schema(description = "How many sources will be walked")
        int queuedSources,

        @Schema(description = "Source names in the order they will be walked")
        List<String> sources) {

    public static ScanStartedDto from(ScanStart start) {
        return new ScanStartedDto(start.count(), start.sources());
    }
}
