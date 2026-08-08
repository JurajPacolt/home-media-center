package org.javerlabd.homecenter.scan;

import lombok.Getter;

/**
 * Running counters for one scan. Only one scan thread uses them, so ordinary mutable
 * state without synchronization is sufficient.
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
