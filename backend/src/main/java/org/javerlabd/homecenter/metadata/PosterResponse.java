package org.javerlabd.homecenter.metadata;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.concurrent.TimeUnit;

/** Rovnaké cache hlavičky pre plagát v REST API aj management UI. */
public final class PosterResponse {

    private PosterResponse() {
    }

    public static ResponseEntity<Resource> of(PosterAsset asset) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePrivate())
                .contentType(MediaType.parseMediaType(asset.contentType()))
                .contentLength(asset.sizeBytes())
                .body(asset.resource());
    }
}
