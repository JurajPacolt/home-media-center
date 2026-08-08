package org.javerlabd.homecenter.media;

/** Index usage by one Samba source for the management UI source overview. */
public record SourceUsage(long sourceId, long items, long sizeBytes) {

    public static SourceUsage empty(long sourceId) {
        return new SourceUsage(sourceId, 0, 0);
    }
}
