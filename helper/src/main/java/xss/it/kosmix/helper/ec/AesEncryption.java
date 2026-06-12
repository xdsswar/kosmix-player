package xss.it.kosmix.helper.ec;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Kosmix
 * <p>
 * Description:
 * This class is part of the xss.it.kosmix.helper.ec package.
 *
 * <p>
 *
 * @author XDSSWAR
 * @version 1.0
 * @since June 11, 2026
 * <p>
 * Created on 06/11/2026
 * <p>
 * Minimal AES-256/GCM helper used by the settings stack to protect
 * sensitive values (cookies, tokens) at rest. The key is derived from a
 * passphrase via SHA-256; every encryption uses a fresh random IV that
 * is prepended to the ciphertext before Base64 encoding.
 */
public final class AesEncryption {
    /**
     * Cipher transformation: AES in GCM mode with no padding.
     */
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    /**
     * GCM authentication tag length, in bits.
     */
    private static final int TAG_BITS = 128;

    /**
     * Initialization vector length, in bytes (96-bit IV per NIST
     * recommendation for GCM).
     */
    private static final int IV_BYTES = 12;

    /**
     * Source of cryptographically strong random IVs.
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Private constructor. This is a static utility holder and must
     * never be instantiated.
     */
    private AesEncryption() {
        throw new AssertionError("No instances of AesEncryption");
    }

    /**
     * Derives an AES-256 {@link SecretKey} from an arbitrary passphrase
     * by hashing it with SHA-256.
     *
     * @param secret the passphrase, must not be {@code null}
     * @return the derived 256-bit AES key
     * @throws Exception if the SHA-256 digest is unavailable
     */
    public static SecretKey toKey(String secret) throws Exception {
        final MessageDigest digest = MessageDigest.getInstance("SHA-256");
        final byte[] keyBytes = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Encrypts the given plain text with AES/GCM.
     *
     * @param value the plain text to encrypt
     * @param key   the AES key (see {@link #toKey(String)})
     * @return Base64 string containing {@code IV || ciphertext || tag}
     * @throws Exception if encryption fails
     */
    public static String encrypt(String value, SecretKey key) throws Exception {
        /*
         * Fresh IV per message; GCM security collapses on IV reuse.
         */
        final byte[] iv = new byte[IV_BYTES];
        RANDOM.nextBytes(iv);

        final Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        final byte[] cipherText = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));

        /*
         * Prepend the IV so decryption is self-contained.
         */
        final byte[] packed = new byte[iv.length + cipherText.length];
        System.arraycopy(iv, 0, packed, 0, iv.length);
        System.arraycopy(cipherText, 0, packed, iv.length, cipherText.length);
        return Base64.getEncoder().encodeToString(packed);
    }

    /**
     * Decrypts a value previously produced by
     * {@link #encrypt(String, SecretKey)}.
     *
     * @param encryptedValue Base64 string containing {@code IV || ciphertext || tag}
     * @param key            the AES key (see {@link #toKey(String)})
     * @return the decrypted plain text
     * @throws Exception if decryption or authentication fails
     */
    public static String decrypt(String encryptedValue, SecretKey key) throws Exception {
        final byte[] packed = Base64.getDecoder().decode(encryptedValue);

        final Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                new GCMParameterSpec(TAG_BITS, packed, 0, IV_BYTES)
        );
        final byte[] plain = cipher.doFinal(packed, IV_BYTES, packed.length - IV_BYTES);
        return new String(plain, StandardCharsets.UTF_8);
    }
}
