package xss.it.kosmix.helper.settings;

/**
 * Kosmix
 * <p>
 * Description:
 * This class is part of the xss.it.kosmix.helper.settings package.
 *
 * <p>
 *
 * @author XDSSWAR
 * @version 1.0
 * @since June 11, 2026
 * <p>
 * Created on 06/11/2026
 * <p>
 * Thrown when an encrypted read or write is attempted on a
 * {@link Settings} instance that was created without a secret key.
 */
public final class NullSecretKeyException extends RuntimeException {
    /**
     * Creates the exception with a fixed, descriptive message.
     */
    public NullSecretKeyException() {
        super("No secret key configured: encrypted settings are unavailable");
    }
}
