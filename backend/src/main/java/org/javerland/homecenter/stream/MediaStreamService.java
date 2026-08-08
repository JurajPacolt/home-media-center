package org.javerland.homecenter.stream;

import java.util.Optional;

import org.javerland.homecenter.media.MediaItem;
import org.javerland.homecenter.media.MediaService;
import org.javerland.homecenter.source.SmbGateway;
import org.javerland.homecenter.source.SmbReadHandle;
import org.javerland.homecenter.source.SmbSource;
import org.javerland.homecenter.source.SmbSourceService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Proxies Samba files to the client. The client never connects to storage directly;
 * only the server knows the credentials.
 */
@Service
@RequiredArgsConstructor
public class MediaStreamService {

    private final MediaService mediaService;
    private final SmbSourceService sourceService;
    private final SmbGateway gateway;

    /**
     * Length comes from Samba, not the index, because the file may have changed since the
     * latest scan and an incorrect Content-Length would break player seeking.
     */
    public MediaStream open(long mediaId, @Nullable String rangeHeader) {
        MediaItem item = mediaService.require(mediaId);
        SmbSource source = sourceService.require(item.sourceId());
        SmbReadHandle handle = gateway.openForRead(source, item.relativePath());
        try {
            long totalLength = handle.size();
            Optional<ByteRange> range = ByteRange.parse(rangeHeader, totalLength);
            long start = range.map(ByteRange::start).orElse(0L);
            long length = range.map(ByteRange::length).orElse(totalLength);

            KnownLengthResource resource = new KnownLengthResource(
                    new SmbInputStream(handle, start, length), length, item.fileName());
            return new MediaStream(
                    resource,
                    item.contentType(),
                    length,
                    totalLength,
                    range.map(value -> value.contentRange(totalLength)).orElse(null),
                    item.fileName());
        } catch (RuntimeException ex) {
            handle.close();
            throw ex;
        }
    }
}
