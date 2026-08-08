package org.javerlabd.homecenter.metadata;

import org.springframework.core.io.Resource;

public record PosterAsset(Resource resource, String contentType, long sizeBytes) {
}
