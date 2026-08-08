package org.javerlabd.homecenter.api.dto;

import org.javerlabd.homecenter.media.MediaGenre;

public record MediaGenreDto(long id, String name) {

    public static MediaGenreDto from(MediaGenre genre) {
        return new MediaGenreDto(genre.id(), genre.name());
    }
}
