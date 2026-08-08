package org.javerlabd.homecenter.source;

/**
 * V indexe aj v REST API sa cesty držia s lomkou dopredu a bez úvodného oddeľovača,
 * na Sambu sa prekladajú na spätné lomky. Vďaka tomu je v databáze aj v URL vždy
 * rovnaký tvar bez ohľadu na to, ako cestu vrátil server.
 */
public final class SmbPaths {

    private SmbPaths() {
    }

    /** Zjednotí oddeľovače, odstráni prázdne segmenty a odmietne pokus o vyskočenie z koreňa. */
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

    /** Tvar, ktorému rozumie SMB server. */
    public static String toSmb(String path) {
        return normalize(path).replace('/', '\\');
    }
}
