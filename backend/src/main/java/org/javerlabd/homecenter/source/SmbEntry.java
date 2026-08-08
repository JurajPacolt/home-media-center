package org.javerlabd.homecenter.source;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/** Jedna položka, ktorú vrátil výpis adresára na Sambe. */
public record SmbEntry(
        String path,
        String name,
        boolean directory,
        long sizeBytes,
        @Nullable Instant modifiedAt) {
}
