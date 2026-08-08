package org.javerland.homecenter.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import org.javerland.homecenter.media.VideoKind;
import org.junit.jupiter.api.Test;

class MediaNameParserTest {

    private final MediaNameParser parser = new MediaNameParser();

    @Test
    void filmOddeliRokAKvalituOdNazvu() {
        ParsedVideoName parsed = parser.parse("filmy/The.Matrix.1999.1080p.BluRay.mkv",
                "The.Matrix.1999.1080p.BluRay.mkv");

        assertThat(parsed.queryTitle()).isEqualTo("The Matrix");
        assertThat(parsed.year()).isEqualTo(1999);
        assertThat(parsed.structure().kind()).isEqualTo(VideoKind.MOVIE);
    }

    @Test
    void serialRozpoznaSkratkuSerieAEpizody() {
        ParsedVideoName parsed = parser.parse("The.Last.of.Us/The.Last.of.Us.S02E03.1080p.mkv",
                "The.Last.of.Us.S02E03.1080p.mkv");

        assertThat(parsed.queryTitle()).isEqualTo("The Last of Us");
        assertThat(parsed.seasonNumber()).isEqualTo(2);
        assertThat(parsed.episodeNumber()).isEqualTo(3);
        assertThat(parsed.structure().groupKey()).isEqualTo("local:series:the-last-of-us");
    }

    @Test
    void serialBezNazvuVoFilePouzijePriecinok() {
        ParsedVideoName parsed = parser.parse("serialy/Dark/Season 1/S01E02.mkv", "S01E02.mkv");

        assertThat(parsed.queryTitle()).isEqualTo("Dark");
        assertThat(parsed.seasonNumber()).isEqualTo(1);
        assertThat(parsed.episodeNumber()).isEqualTo(2);
    }

    @Test
    void podporujeAjFormatJednaKratDva() {
        ParsedVideoName parsed = parser.parse("serialy/Andor/Andor.1x04.mkv", "Andor.1x04.mkv");

        assertThat(parsed.queryTitle()).isEqualTo("Andor");
        assertThat(parsed.seasonNumber()).isEqualTo(1);
        assertThat(parsed.episodeNumber()).isEqualTo(4);
    }

    @Test
    void viacdielnyFilmDostaneSpolocnyKlucAPoradie() {
        ParsedVideoName parsed = parser.parse("filmy/Dune.Part.2.2024.mkv", "Dune.Part.2.2024.mkv");

        assertThat(parsed.queryTitle()).isEqualTo("Dune");
        assertThat(parsed.year()).isEqualTo(2024);
        assertThat(parsed.partNumber()).isEqualTo(2);
        assertThat(parsed.structure().groupKey()).isEqualTo("local:parts:dune");
    }
}
