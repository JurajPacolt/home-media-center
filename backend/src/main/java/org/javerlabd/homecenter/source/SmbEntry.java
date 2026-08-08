package org.javerlabd.homecenter.source;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/** One entry returned by a Samba directory listing. */
public record SmbEntry(
        String path,
        String name,
        boolean directory,
        long sizeBytes,
        @Nullable Instant modifiedAt) {
}
