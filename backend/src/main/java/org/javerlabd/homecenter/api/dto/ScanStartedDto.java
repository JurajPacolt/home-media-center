package org.javerlabd.homecenter.api.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import org.javerlabd.homecenter.scan.ScanStart;

/**
 * Confirms that a scan was queued. History rows are created only when a source's turn
 * begins, so progress is retrieved from {@code GET /api/v1/scan/latest}.
 */
public record ScanStartedDto(

        @Schema(description = "Počet zdrojov, ktoré sa budú prechádzať")
        int queuedSources,

        @Schema(description = "Názvy zdrojov v poradí, v akom sa prejdú")
        List<String> sources) {

    public static ScanStartedDto from(ScanStart start) {
        return new ScanStartedDto(start.count(), start.sources());
    }
}
