package org.javerlabd.homecenter.scan;

import java.util.List;

/**
 * Čo sa práve postavilo do radu. Riadky v {@code scan_run} tu ešte neexistujú — vznikajú
 * až vtedy, keď na zdroj príde rad, aby „posledný sken“ v UI ukazoval ten, ktorý naozaj
 * beží, a nie ten, ktorý sa ešte nezačal.
 *
 * @param sources názvy zdrojov v poradí, v akom sa budú prechádzať
 */
public record ScanStart(List<String> sources) {

    public int count() {
        return sources.size();
    }
}
