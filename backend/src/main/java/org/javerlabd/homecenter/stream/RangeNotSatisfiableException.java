package org.javerlabd.homecenter.stream;

/** Klient si vypýtal rozsah mimo súboru — odpovedá sa 416 aj s dĺžkou súboru. */
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
