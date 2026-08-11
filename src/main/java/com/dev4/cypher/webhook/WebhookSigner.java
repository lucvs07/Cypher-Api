package com.dev4.cypher.webhook;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Assina o corpo cru do webhook com HMAC-SHA256.
 *
 * <p>O timestamp entra na mensagem assinada para que uma requisição capturada
 * não possa ser reenviada indefinidamente.
 */
@Component
public class WebhookSigner {

    private static final String ALGORITMO = "HmacSHA256";

    public String sign(String secret, String timestamp, String body) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITMO));
            byte[] digest = mac.doFinal(
                    (timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
            return "sha256=" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Falha ao assinar webhook", e);
        }
    }
}
