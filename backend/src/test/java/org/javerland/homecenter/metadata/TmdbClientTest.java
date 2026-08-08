package org.javerland.homecenter.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class TmdbClientTest {

    @Test
    void urlPlagatuZachovaKonfigurovanuVelkost() {
        assertThat(TmdbClient.posterUri(
                "https://image.tmdb.org/t/p/w342", "/nBNZadXqJSdt05SHLqgT0HuC5Gm.jpg"))
                .hasToString("https://image.tmdb.org/t/p/w342/nBNZadXqJSdt05SHLqgT0HuC5Gm.jpg");
    }

    @Test
    void cestaPlagatuNemozePresmerovatPoziadavkuMimoTmdb() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                TmdbClient.posterUri("https://image.tmdb.org/t/p/w342", "https://example.org/a.jpg"));
    }
}
