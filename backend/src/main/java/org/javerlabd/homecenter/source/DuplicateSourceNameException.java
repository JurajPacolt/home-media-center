package org.javerlabd.homecenter.source;

/**
 * Dva zdroje s rovnakým názvom by boli v zozname aj vo filtri knižnice na nerozoznanie.
 * Adresa ani share sa nekontrolujú — ten istý server pripojený na dva rôzne priečinky
 * je legitímne nastavenie.
 */
public class DuplicateSourceNameException extends RuntimeException {

    public DuplicateSourceNameException(String name) {
        super("Zdroj s názvom '" + name + "' už existuje");
    }
}
