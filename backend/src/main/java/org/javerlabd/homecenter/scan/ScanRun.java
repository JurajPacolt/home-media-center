package org.javerlabd.homecenter.scan;

import java.time.Duration;
import java.time.Instant;

import org.jspecify.annotations.Nullable;

/** Record of one scan run, used by both the history and UI progress indicator. */
public record ScanRun(
        @Nullable Long id,
        @Nullable Long sourceId,
        ScanTrigger trigger,
        ScanStatus status,
        Instant startedAt,
        @Nullable Instant finishedAt,
        int directoriesScanned,
        int filesSeen,
        int itemsAdded,
        int itemsUpdated,
        int itemsRemoved,
        @Nullable String errorMessage) {

    public long requireId() {
        if (id == null) {
            throw new IllegalStateException("Sken ešte nebol uložený");
        }
        return id;
    }

    public Duration duration() {
        return Duration.between(startedAt, finishedAt == null ? Instant.now() : finishedAt);
    }
}
