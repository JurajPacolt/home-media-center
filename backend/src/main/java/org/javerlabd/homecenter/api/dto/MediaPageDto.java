package org.javerlabd.homecenter.api.dto;

import java.util.List;

/** Stránka výpisu knižnice. */
public record MediaPageDto(List<MediaItemDto> items, long total, int limit, int offset) {
}
