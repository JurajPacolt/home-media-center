package org.javerland.homecenter.media;

/** Status of automatic movie metadata enrichment. */
public enum MetadataStatus {
    PENDING,
    MATCHED,
    NOT_FOUND,
    FAILED,
    SKIPPED
}
