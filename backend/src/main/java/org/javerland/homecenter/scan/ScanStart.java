package org.javerland.homecenter.scan;

import java.util.List;

/**
 * Describes what was just queued. Rows in {@code scan_run} do not exist yet; they are
 * created only when a source's turn begins so the UI's "latest scan" shows the one
 * actually running rather than one that has not started.
 *
 * @param sources source names in traversal order
 */
public record ScanStart(List<String> sources) {

    public int count() {
        return sources.size();
    }
}
