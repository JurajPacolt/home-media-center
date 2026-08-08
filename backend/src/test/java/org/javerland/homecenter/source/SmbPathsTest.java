package org.javerland.homecenter.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SmbPathsTest {

    @Test
    void normalizeZjednotiOddelovaceAOrezeLomky() {
        assertThat(SmbPaths.normalize("/filmy\\akcia/")).isEqualTo("filmy/akcia");
        assertThat(SmbPaths.normalize("filmy//akcia")).isEqualTo("filmy/akcia");
        assertThat(SmbPaths.normalize("  ")).isEmpty();
        assertThat(SmbPaths.normalize(null)).isEmpty();
    }

    @Test
    void joinPoskladaCestuAjKedJeJednaCastPrazdna() {
        assertThat(SmbPaths.join("filmy", "matrix.mkv")).isEqualTo("filmy/matrix.mkv");
        assertThat(SmbPaths.join("", "matrix.mkv")).isEqualTo("matrix.mkv");
        assertThat(SmbPaths.join("filmy", "")).isEqualTo("filmy");
        assertThat(SmbPaths.join("", "")).isEmpty();
    }

    @Test
    void toSmbPouzijeSpatneLomky() {
        assertThat(SmbPaths.toSmb("filmy/akcia/matrix.mkv")).isEqualTo("filmy\\akcia\\matrix.mkv");
        assertThat(SmbPaths.toSmb("")).isEmpty();
    }

    @Test
    void cestaSaNesmieVyhrabatNadKoren() {
        assertThatThrownBy(() -> SmbPaths.normalize("filmy/../../etc"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
