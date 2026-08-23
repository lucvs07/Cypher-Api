package com.dev4.cypher.catalog;

import org.springframework.http.HttpStatus;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

/**
 * Regras de forma do catálogo, compartilhadas por serviço e controller.
 *
 * <p>O código corresponde a algo físico — placa na estação, etiqueta na
 * câmera — e é imutável depois de criado, porque está embutido no
 * {@code incidentKey} de todo incidente já gravado.
 */
public final class CatalogCodes {

    /** Maiúsculas e dígitos, um hífen obrigatório, 4 a 19 caracteres. */
    public static final String PADRAO = "^[A-Z0-9]{2,10}-[A-Z0-9]{1,8}$";

    private static final Pattern COMPILADO = Pattern.compile(PADRAO);

    private CatalogCodes() {
    }

    public static boolean valido(String codigo) {
        return codigo != null && COMPILADO.matcher(codigo).matches();
    }

    /**
     * Porta em que o pipeline deve servir o MJPEG, extraída do streamUrl.
     *
     * <p>A porta explícita é exigida para que a derivação seja inequívoca: o
     * MJPEG não serve em 80 nem em 443, então cair na porta default do esquema
     * produziria uma config silenciosamente errada.
     */
    public static int portaDe(String streamUrl) {
        if (streamUrl == null) {
            throw new CatalogValidationException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "streamUrl e obrigatoria");
        }
        URI uri;
        try {
            uri = new URI(streamUrl);
        } catch (URISyntaxException e) {
            throw new CatalogValidationException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "streamUrl nao e uma URL valida: " + streamUrl);
        }
        int porta = uri.getPort();
        if (porta == -1) {
            throw new CatalogValidationException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "streamUrl precisa de porta explicita, ex.: http://127.0.0.1:8090/stream");
        }
        return porta;
    }
}
