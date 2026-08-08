package org.javerlabd.homecenter.scan;

/** Naraz beží najviac jeden sken — dva by si v indexe navzájom mazali položky. */
public class ScanAlreadyRunningException extends RuntimeException {

    public ScanAlreadyRunningException() {
        super("Sken už beží");
    }
}
