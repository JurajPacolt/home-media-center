package org.javerlabd.homecenter.stream;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * Range from the {@code Range} header. One range is supported, which is what players
 * send when seeking. Under RFC 9110 section 14.2, the server ignores a request for
 * multiple ranges and sends the entire file.
 */
public record ByteRange(long start, long endInclusive) {

    public ByteRange {
        if (start < 0 || endInclusive < start) {
            throw new IllegalArgumentException("Neplatný rozsah " + start + "-" + endInclusive);
        }
    }

    public long length() {
        return endInclusive - start + 1;
    }

    /** {@code Content-Range} header value for a 206 response. */
    public String contentRange(long totalLength) {
        return "bytes " + start + "-" + endInclusive + "/" + totalLength;
    }

    /**
     * @return empty if the header should be ignored and the entire file sent
     * @throws RangeNotSatisfiableException if the range is valid but outside the file
     */
    public static Optional<ByteRange> parse(@Nullable String header, long totalLength) {
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        String value = header.trim();
        if (!value.regionMatches(true, 0, "bytes=", 0, "bytes=".length())) {
            return Optional.empty();
        }
        String spec = value.substring("bytes=".length()).trim();
        if (spec.indexOf(',') >= 0) {
            return Optional.empty();
        }
        int dash = spec.indexOf('-');
        if (dash < 0) {
            return Optional.empty();
        }

        String startText = spec.substring(0, dash).trim();
        String endText = spec.substring(dash + 1).trim();
        long start;
        long end;
        try {
            if (startText.isEmpty()) {
                // "-N" form: the last N bytes
                if (endText.isEmpty()) {
                    return Optional.empty();
                }
                long suffixLength = Long.parseLong(endText);
                if (suffixLength <= 0) {
                    throw new RangeNotSatisfiableException(totalLength);
                }
                start = Math.max(0, totalLength - suffixLength);
                end = totalLength - 1;
            } else {
                start = Long.parseLong(startText);
                end = endText.isEmpty() ? totalLength - 1 : Long.parseLong(endText);
            }
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }

        if (start < 0 || end < start) {
            return Optional.empty();
        }
        if (totalLength <= 0 || start >= totalLength) {
            throw new RangeNotSatisfiableException(totalLength);
        }
        return Optional.of(new ByteRange(start, Math.min(end, totalLength - 1)));
    }
}
