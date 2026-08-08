package org.javerland.homecenter.api;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.javerland.homecenter.api.dto.ScanRunDto;
import org.javerland.homecenter.api.dto.ScanStartedDto;
import org.javerland.homecenter.scan.ScanService;
import org.javerland.homecenter.scan.ScanStart;
import org.javerland.homecenter.scan.ScanTrigger;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/scan")
@Tag(name = "Skenovanie", description = "Spustenie skenu Samby a jeho história")
@RequiredArgsConstructor
public class ScanApiController {

    private final ScanService scanService;

    @PostMapping
    @Operation(summary = "Spustí sken na pozadí a hneď sa vráti; bez sourceId prejde všetky zapnuté zdroje")
    @SecurityRequirement(name = "bearer")
    public ResponseEntity<ScanStartedDto> start(@RequestParam(required = false) @Nullable Long sourceId) {
        ScanStart started = sourceId == null
                ? scanService.triggerAll(ScanTrigger.MANUAL)
                : scanService.triggerOne(sourceId, ScanTrigger.MANUAL);
        return ResponseEntity.accepted().body(ScanStartedDto.from(started));
    }

    @GetMapping("/latest")
    @Operation(summary = "Posledný sken vrátane priebehu toho, ktorý práve beží")
    @SecurityRequirement(name = "bearer")
    public ResponseEntity<ScanRunDto> latest() {
        return scanService.latest()
                .map(run -> ResponseEntity.ok(ScanRunDto.from(run)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NO_CONTENT).build());
    }

    @GetMapping
    @Operation(summary = "História skenov naprieč všetkými zdrojmi")
    @SecurityRequirement(name = "bearer")
    public List<ScanRunDto> history(@RequestParam(defaultValue = "20") int limit) {
        return scanService.history(limit).stream().map(ScanRunDto::from).toList();
    }
}
