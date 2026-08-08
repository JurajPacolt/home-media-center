package org.javerlabd.homecenter.stream;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * Rozsah z hlavičky {@code Range}. Podporuje sa jeden rozsah — to je presne to, čo
 * posielajú prehrávače pri pretáčaní. Požiadavku o viac rozsahov naraz server podľa
 * RFC 9110 § 14.2 ignoruje a pošle celý súbor.
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

    /** Hodnota hlavičky {@code Content-Range} pre odpoveď 206. */
    public String contentRange(long totalLength) {
        return "bytes " + start + "-" + endInclusive + "/" + totalLength;
    }

    /**
     * @return prázdny výsledok, ak sa má hlavička ignorovať a poslať celý súbor
     * @throws RangeNotSatisfiableException ak je rozsah zrozumiteľný, ale mimo súboru
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
                // tvar "-N": posledných N bajtov
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
