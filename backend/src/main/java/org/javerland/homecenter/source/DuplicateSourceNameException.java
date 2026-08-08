package org.javerland.homecenter.source;

/**
 * Two sources with the same name would be indistinguishable in both the list and library
 * filter. The address and share are not checked; connecting the same server to two different
 * directories is a valid configuration.
 */
public class DuplicateSourceNameException extends RuntimeException {

    public DuplicateSourceNameException(String name) {
        super("Zdroj s názvom '" + name + "' už existuje");
    }
}
