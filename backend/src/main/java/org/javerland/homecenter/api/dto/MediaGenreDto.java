package org.javerland.homecenter.api.dto;

import org.javerland.homecenter.media.MediaGenre;

public record MediaGenreDto(long id, String name) {

    public static MediaGenreDto from(MediaGenre genre) {
        return new MediaGenreDto(genre.id(), genre.name());
    }
}
