package org.javerland.homecenter.metadata;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class PosterNotFoundException extends RuntimeException {

    public PosterNotFoundException(long mediaId) {
        super("Náhľad položky " + mediaId + " neexistuje");
    }
}
