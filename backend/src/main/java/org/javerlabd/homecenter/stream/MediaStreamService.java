package org.javerlabd.homecenter.stream;

import java.util.Optional;

import org.javerlabd.homecenter.media.MediaItem;
import org.javerlabd.homecenter.media.MediaService;
import org.javerlabd.homecenter.source.SmbGateway;
import org.javerlabd.homecenter.source.SmbReadHandle;
import org.javerlabd.homecenter.source.SmbSource;
import org.javerlabd.homecenter.source.SmbSourceService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Preposiela súbory zo Samby klientovi. Klient sa na úložisko nikdy nepripája sám —
 * prihlasovacie údaje pozná výhradne server.
 */
@Service
@RequiredArgsConstructor
public class MediaStreamService {

    private final MediaService mediaService;
    private final SmbSourceService sourceService;
    private final SmbGateway gateway;

    /**
     * Dĺžka sa berie zo Samby, nie z indexu — súbor sa mohol od posledného skenu zmeniť
     * a nesprávny Content-Length by prehrávaču rozbil pretáčanie.
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
