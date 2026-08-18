package com.stdntedu.stage10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import com.stdntedu.ai.model.security.AiSecretCryptoService;
import org.junit.jupiter.api.Test;

class AiSecretCryptoServiceTest {
    private static final String MASTER_KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    @Test void scenarios01_06_08_validKeyRoundTripAndMetadata() {
        AiSecretCryptoService crypto = new AiSecretCryptoService(MASTER_KEY);
        char[] plaintext = "stage-ten-secret".toCharArray();
        var encrypted = crypto.encrypt("ais_reference", plaintext);

        assertThat(encrypted.algorithm()).isEqualTo("AES-256-GCM");
        assertThat(encrypted.nonce()).hasSize(12);
        assertThat(encrypted.keyVersion()).isEqualTo(1);
        assertThat(crypto.decrypt("ais_reference", encrypted.encryptedValue(), encrypted.nonce()))
                .containsExactly(plaintext);
    }

    @Test void scenario02_invalidBase64FailsWithoutEchoingConfiguredValue() {
        String configured = "not-base64-SECRET-MUST-NOT-LEAK";
        AiSecretCryptoService crypto = new AiSecretCryptoService(configured);
        assertThatThrownBy(() -> crypto.encrypt("ais_reference", new char[] {'x'}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AI secret master key is not valid Base64")
                .hasMessageNotContaining(configured);
    }

    @Test void scenario03_non256BitKeyIsRejected() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new AiSecretCryptoService(shortKey)
                .encrypt("ais_reference", new char[] {'x'}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AI secret master key must decode to exactly 32 bytes");
    }

    @Test void scenario04_repeatedEncryptionUsesDistinctNonceAndCiphertext() {
        AiSecretCryptoService crypto = new AiSecretCryptoService(MASTER_KEY);
        char[] plaintext = "same plaintext".toCharArray();
        var first = crypto.encrypt("ais_reference", plaintext);
        var second = crypto.encrypt("ais_reference", plaintext);

        assertThat(first.nonce()).isNotEqualTo(second.nonce());
        assertThat(first.encryptedValue()).isNotEqualTo(second.encryptedValue());
        Arrays.fill(plaintext, '\0');
    }

    @Test void scenario05_aadBindsCiphertextToSecretReference() {
        AiSecretCryptoService crypto = new AiSecretCryptoService(MASTER_KEY);
        var encrypted = crypto.encrypt("ais_reference", "bound".toCharArray());
        assertThatThrownBy(() -> crypto.decrypt("ais_other", encrypted.encryptedValue(), encrypted.nonce()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AI secret authentication failed");
    }
}
