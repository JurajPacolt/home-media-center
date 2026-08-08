package org.javerland.homecenter.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CinemetaClientTest {

    @Test
    void plagatZoZnamehoCdnPrejde() {
        assertThat(CinemetaClient.posterUri("https://images.metahub.space/poster/small/tt0133093/img"))
                .hasToString("https://images.metahub.space/poster/small/tt0133093/img");
        assertThat(CinemetaClient.posterUri("https://episodes.metahub.space/tt4574334/1/2/w780.jpg"))
                .hasToString("https://episodes.metahub.space/tt4574334/1/2/w780.jpg");
    }

    @Test
    void cudziHostAleboHttpSaOdmietne() {
        assertThatThrownBy(() -> CinemetaClient.posterUri("https://evil.example.org/img"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CinemetaClient.posterUri("http://images.metahub.space/img"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CinemetaClient.posterUri("https://images.metahub.space.evil.org/img"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CinemetaClient.posterUri("file:///etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void identifikatorZanruJeStabilnyANezaporny() {
        long first = CinemetaClient.genreId("Sci-Fi");
        assertThat(first).isEqualTo(CinemetaClient.genreId("sci-fi")).isNotNegative();
        assertThat(first).isNotEqualTo(CinemetaClient.genreId("Drama"));
    }

    @Test
    void hladanyVyrazSaOcistiOdBielychZnakov() {
        assertThat(CinemetaClient.searchTerm("  The\tMatrix \n")).isEqualTo("The Matrix");
        assertThat(CinemetaClient.searchTerm("   ")).isEmpty();
    }
}
