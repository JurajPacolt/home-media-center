package org.javerlabd.homecenter.media;

/** Koľko z indexu zaberá jeden Samba zdroj. Do prehľadu zdrojov v management UI. */
public record SourceUsage(long sourceId, long items, long sizeBytes) {

    public static SourceUsage empty(long sourceId) {
        return new SourceUsage(sourceId, 0, 0);
    }
}
