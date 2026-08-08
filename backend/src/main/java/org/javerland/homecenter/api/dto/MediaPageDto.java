package org.javerland.homecenter.api.dto;

import java.util.List;

/** A page of library results. */
public record MediaPageDto(List<MediaItemDto> items, long total, int limit, int offset) {
}
