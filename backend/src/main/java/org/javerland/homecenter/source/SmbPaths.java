package org.javerland.homecenter.source;

/**
 * Paths in the index and REST API use forward slashes without a leading separator and
 * are converted to backslashes for Samba. This keeps the database and URL form consistent
 * regardless of how the server returned the path.
 */
public final class SmbPaths {

    private SmbPaths() {
    }

    /** Normalizes separators, removes empty segments, and rejects attempts to escape the root. */
    public static String normalize(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (String segment : path.replace('\\', '/').split("/")) {
            String trimmed = segment.trim();
            if (trimmed.isEmpty() || ".".equals(trimmed)) {
                continue;
            }
            if ("..".equals(trimmed)) {
                throw new IllegalArgumentException("Cesta nesmie obsahovať '..': " + path);
            }
            if (!result.isEmpty()) {
                result.append('/');
            }
            result.append(trimmed);
        }
        return result.toString();
    }

    public static String join(String parent, String child) {
        String left = normalize(parent);
        String right = normalize(child);
        if (left.isEmpty()) {
            return right;
        }
        if (right.isEmpty()) {
            return left;
        }
        return left + "/" + right;
    }

    /** Form understood by the SMB server. */
    public static String toSmb(String path) {
        return normalize(path).replace('/', '\\');
    }
}
