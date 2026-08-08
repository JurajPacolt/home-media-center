package org.javerlabd.homecenter.scan;

import lombok.Getter;

/**
 * Priebežné počty jedného skenu. Beží nad ním jediné vlákno skenu, preto stačí
 * obyčajný mutovateľný stav bez synchronizácie.
 */
@Getter
public final class ScanCounters {

    private int directoriesScanned;
    private int filesSeen;
    private int itemsAdded;
    private int itemsUpdated;
    private int itemsRemoved;

    public void directoryScanned() {
        directoriesScanned++;
    }

    public void fileIndexed(boolean added) {
        filesSeen++;
        if (added) {
            itemsAdded++;
        } else {
            itemsUpdated++;
        }
    }

    public void itemsRemoved(int count) {
        itemsRemoved = count;
    }
}
