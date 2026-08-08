package org.javerlabd.homecenter.api.dto;

import java.time.Instant;

import org.javerlabd.homecenter.scan.ScanRun;
import org.javerlabd.homecenter.scan.ScanStatus;
import org.javerlabd.homecenter.scan.ScanTrigger;
import org.jspecify.annotations.Nullable;

public record ScanRunDto(
        long id,
        ScanTrigger trigger,
        ScanStatus status,
        Instant startedAt,
        @Nullable Instant finishedAt,
        long durationSeconds,
        int directoriesScanned,
        int filesSeen,
        int itemsAdded,
        int itemsUpdated,
        int itemsRemoved,
        @Nullable String errorMessage) {

    public static ScanRunDto from(ScanRun run) {
        return new ScanRunDto(
                run.requireId(),
                run.trigger(),
                run.status(),
                run.startedAt(),
                run.finishedAt(),
                run.duration().toSeconds(),
                run.directoriesScanned(),
                run.filesSeen(),
                run.itemsAdded(),
                run.itemsUpdated(),
                run.itemsRemoved(),
                run.errorMessage());
    }
}
