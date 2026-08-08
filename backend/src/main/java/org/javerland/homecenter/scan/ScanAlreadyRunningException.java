package org.javerland.homecenter.scan;

/** At most one scan runs at a time; two scans would delete each other's index entries. */
public class ScanAlreadyRunningException extends RuntimeException {

    public ScanAlreadyRunningException() {
        super("Sken už beží");
    }
}
