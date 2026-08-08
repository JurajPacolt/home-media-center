package org.javerland.homecenter.scan;

public enum ScanStatus {

    RUNNING("Beží"),
    COMPLETED("Dokončený"),
    FAILED("Zlyhal");

    private final String label;

    ScanStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
