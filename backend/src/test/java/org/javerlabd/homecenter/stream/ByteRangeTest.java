package org.javerlabd.homecenter.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class ByteRangeTest {

    private static final long LENGTH = 1000;

    @Test
    void bezHlavickySaPosielaCelySubor() {
        assertThat(ByteRange.parse(null, LENGTH)).isEmpty();
        assertThat(ByteRange.parse("   ", LENGTH)).isEmpty();
    }

    @Test
    void uzavretyRozsah() {
        Optional<ByteRange> range = ByteRange.parse("bytes=100-199", LENGTH);
        assertThat(range).contains(new ByteRange(100, 199));
        assertThat(range.orElseThrow().length()).isEqualTo(100);
    }

    @Test
    void otvorenyRozsahIdePoKoniecSuboru() {
        assertThat(ByteRange.parse("bytes=900-", LENGTH)).contains(new ByteRange(900, 999));
    }

    @Test
    void zapornyRozsahBerieKoniecSuboru() {
        assertThat(ByteRange.parse("bytes=-100", LENGTH)).contains(new ByteRange(900, 999));
    }

    @Test
    void prilisVelkyZapornyRozsahSaOreze() {
        assertThat(ByteRange.parse("bytes=-5000", LENGTH)).contains(new ByteRange(0, 999));
    }

    @Test
    void koniecZaSuboromSaOrezeNaPoslednyBajt() {
        assertThat(ByteRange.parse("bytes=990-5000", LENGTH)).contains(new ByteRange(990, 999));
    }

    @Test
    void zaciatokZaKoncomSuboruJe416() {
        assertThatThrownBy(() -> ByteRange.parse("bytes=1000-1100", LENGTH))
                .isInstanceOf(RangeNotSatisfiableException.class);
    }

    @Test
    void prazdnySuborNevieVyhovietZiadnemuRozsahu() {
        assertThatThrownBy(() -> ByteRange.parse("bytes=0-10", 0))
                .isInstanceOf(RangeNotSatisfiableException.class);
    }

    @Test
    void poskodenaHlavickaSaIgnorujeAPosielaSaCelySubor() {
        assertThat(ByteRange.parse("bytes=abc-def", LENGTH)).isEmpty();
        assertThat(ByteRange.parse("položky=0-10", LENGTH)).isEmpty();
        assertThat(ByteRange.parse("bytes=200-100", LENGTH)).isEmpty();
        assertThat(ByteRange.parse("bytes=", LENGTH)).isEmpty();
    }

    @Test
    void viacRozsahovNarazSaIgnorujeAPosielaSaCelySubor() {
        assertThat(ByteRange.parse("bytes=0-99,200-299", LENGTH)).isEmpty();
    }

    @Test
    void contentRangeMaTvarPodlaRfc() {
        assertThat(new ByteRange(100, 199).contentRange(LENGTH)).isEqualTo("bytes 100-199/1000");
    }
}
