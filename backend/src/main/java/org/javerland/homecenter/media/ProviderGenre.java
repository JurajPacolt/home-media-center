package org.javerland.homecenter.media;

/** Genre in the external catalog's identity before storage in the local index. */
public record ProviderGenre(long providerId, String name) {
}
