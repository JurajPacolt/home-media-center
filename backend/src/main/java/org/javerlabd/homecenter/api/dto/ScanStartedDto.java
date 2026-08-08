package org.javerlabd.homecenter.api.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import org.javerlabd.homecenter.scan.ScanStart;

/**
 * Potvrdenie, že sken sa postavil do radu. Riadky v histórii vznikajú až vtedy, keď na
 * zdroj príde rad — priebeh sa preto ťahá z {@code GET /api/v1/scan/latest}.
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
