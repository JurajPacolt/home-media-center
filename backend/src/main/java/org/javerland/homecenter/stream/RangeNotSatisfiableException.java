package org.javerland.homecenter.stream;

/** The client requested a range outside the file; respond with 416 and the file length. */
public class RangeNotSatisfiableException extends RuntimeException {

    private final long totalLength;

    public RangeNotSatisfiableException(long totalLength) {
        super("Požadovaný rozsah je mimo súboru dlhého " + totalLength + " B");
        this.totalLength = totalLength;
    }

    public long totalLength() {
        return totalLength;
    }
}
