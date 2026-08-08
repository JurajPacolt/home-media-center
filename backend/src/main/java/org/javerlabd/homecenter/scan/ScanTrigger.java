package org.javerlabd.homecenter.scan;

/** What triggered the scan. */
public enum ScanTrigger {

    MANUAL("Ručne"),
    SCHEDULED("Naplánovane"),
    STARTUP("Pri štarte");

    private final String label;

    ScanTrigger(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
