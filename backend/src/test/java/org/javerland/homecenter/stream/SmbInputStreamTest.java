package org.javerland.homecenter.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.javerland.homecenter.source.SmbReadHandle;
import org.junit.jupiter.api.Test;

/**
 * This is the core of seeking: reads must use an absolute file position, and a skip
 * must not transfer any extra bytes.
 */
class SmbInputStreamTest {

    private static final byte[] FILE = "0123456789ABCDEFGHIJ".getBytes(StandardCharsets.US_ASCII);

    @Test
    void celeOknoPrecitaCelySubor() throws IOException {
        try (SmbInputStream stream = new SmbInputStream(handle(), 0, FILE.length)) {
            assertThat(stream.readAllBytes()).isEqualTo(FILE);
        }
    }

    @Test
    void oknoCitaLenSvojuCastSuboru() throws IOException {
        try (SmbInputStream stream = new SmbInputStream(handle(), 10, 5)) {
            assertThat(new String(stream.readAllBytes(), StandardCharsets.US_ASCII)).isEqualTo("ABCDE");
        }
    }

    @Test
    void skokPosunieIbaPoziciu() throws IOException {
        try (SmbInputStream stream = new SmbInputStream(handle(), 10, 10)) {
            assertThat(stream.skip(5)).isEqualTo(5);
            assertThat(new String(stream.readAllBytes(), StandardCharsets.US_ASCII)).isEqualTo("FGHIJ");
        }
    }

    @Test
    void skokZaKoniecOknaSaOreze() throws IOException {
        try (SmbInputStream stream = new SmbInputStream(handle(), 0, 4)) {
            assertThat(stream.skip(100)).isEqualTo(4);
            assertThat(stream.read()).isEqualTo(-1);
        }
    }

    @Test
    void availableHlasiZvysokOkna() throws IOException {
        try (SmbInputStream stream = new SmbInputStream(handle(), 2, 6)) {
            assertThat(stream.available()).isEqualTo(6);
            assertThat(stream.read()).isEqualTo('2');
            assertThat(stream.available()).isEqualTo(5);
        }
    }

    /** Handle behaving like {@link #FILE}, with reads from any position. */
    private static SmbReadHandle handle() {
        SmbReadHandle handle = mock(SmbReadHandle.class);
        given(handle.size()).willReturn((long) FILE.length);
        given(handle.read(any(byte[].class), anyLong(), anyInt(), anyInt())).willAnswer(invocation -> {
            byte[] buffer = invocation.getArgument(0);
            long fileOffset = invocation.getArgument(1);
            int bufferOffset = invocation.getArgument(2);
            int length = invocation.getArgument(3);
            if (fileOffset >= FILE.length) {
                return -1;
            }
            int read = (int) Math.min(length, FILE.length - fileOffset);
            System.arraycopy(FILE, (int) fileOffset, buffer, bufferOffset, read);
            return read;
        });
        return handle;
    }
}
